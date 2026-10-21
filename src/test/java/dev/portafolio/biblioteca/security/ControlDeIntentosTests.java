package dev.portafolio.biblioteca.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** The lock after repeated failures, with a clock the test can move. */
class ControlDeIntentosTests {

	/** A clock that moves only when the test says so. */
	private static final class RelojManual extends Clock {

		private Instant ahora = Instant.parse("2026-10-19T15:00:00Z");

		void avanzar(Duration duracion) {
			ahora = ahora.plus(duracion);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return ahora;
		}

	}

	private final RelojManual reloj = new RelojManual();
	private final ControlDeIntentos control = new ControlDeIntentos(reloj,
			new SeguridadProperties("x".repeat(32), Duration.ofMinutes(60), 3, Duration.ofMinutes(5), null, null, null));

	private void fallar(String email, int veces) {
		for (int i = 0; i < veces; i++) {
			control.exigirNoBloqueado(email);
			control.registrarFallo(email);
		}
	}

	@Test
	void anEmailIsNotLockedBeforeTheLimit() {
		fallar("ana@example.org", 2);

		assertThatCode(() -> control.exigirNoBloqueado("ana@example.org")).doesNotThrowAnyException();
	}

	@Test
	void anEmailIsLockedAtTheLimit() {
		fallar("ana@example.org", 3);

		assertThatThrownBy(() -> control.exigirNoBloqueado("ana@example.org"))
				.isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.TOO_MANY_ATTEMPTS));
	}

	@Test
	void theLockIsPerEmailAndIgnoresCaseAndSpaces() {
		fallar("Ana@Example.org", 3);

		assertThatThrownBy(() -> control.exigirNoBloqueado("  ana@example.ORG ")).isInstanceOf(BusinessException.class);
		assertThatCode(() -> control.exigirNoBloqueado("berta@example.org")).doesNotThrowAnyException();
	}

	@Test
	void theLockEndsAfterTheConfiguredTime() {
		fallar("ana@example.org", 3);

		reloj.avanzar(Duration.ofMinutes(5).minusSeconds(1));
		assertThatThrownBy(() -> control.exigirNoBloqueado("ana@example.org")).isInstanceOf(BusinessException.class);

		reloj.avanzar(Duration.ofSeconds(1));
		assertThatCode(() -> control.exigirNoBloqueado("ana@example.org")).doesNotThrowAnyException();
	}

	@Test
	void afterTheLockTheCountStartsAgain() {
		fallar("ana@example.org", 3);
		reloj.avanzar(Duration.ofMinutes(6));

		fallar("ana@example.org", 2);

		assertThatCode(() -> control.exigirNoBloqueado("ana@example.org")).doesNotThrowAnyException();
	}

	@Test
	void aSuccessClearsTheFailures() {
		fallar("ana@example.org", 2);
		control.registrarExito("ana@example.org");

		fallar("ana@example.org", 2);

		assertThatCode(() -> control.exigirNoBloqueado("ana@example.org")).doesNotThrowAnyException();
	}

}
