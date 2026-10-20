package dev.portafolio.biblioteca.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ConstraintErrorsTests {

	private DataIntegrityViolationException violation(String constraint) {
		return new DataIntegrityViolationException("could not execute statement",
				new java.sql.SQLException("ORA-00001: unique constraint (BIBLIOTECA." + constraint + ") violated"));
	}

	@Test
	void translatesTheUniqueIndexOfTheActiveLoan() {
		assertThat(ConstraintErrors.from(violation("UX_PRESTAMO_EJEMPLAR_ACTIVO"))).contains(ErrorCode.COPY_ALREADY_ON_LOAN);
	}

	@Test
	void translatesTheDuplicatesOfBookMemberAndCopy() {
		assertThat(ConstraintErrors.from(violation("UK_LIBRO_ISBN"))).contains(ErrorCode.DUPLICATE_ISBN);
		assertThat(ConstraintErrors.from(violation("UX_SOCIO_EMAIL"))).contains(ErrorCode.DUPLICATE_EMAIL);
		assertThat(ConstraintErrors.from(violation("UK_EJEMPLAR_CODIGO"))).contains(ErrorCode.DUPLICATE_COPY_CODE);
	}

	@Test
	void translatesTheRenewalCheckAndTheForeignKeysUsedByDeletes() {
		assertThat(ConstraintErrors.from(violation("CK_PRESTAMO_RENOVACIONES"))).contains(ErrorCode.RENEWAL_LIMIT_REACHED);
		assertThat(ConstraintErrors.from(violation("FK_EJEMPLAR_LIBRO"))).contains(ErrorCode.BOOK_HAS_COPIES);
		assertThat(ConstraintErrors.from(violation("FK_PRESTAMO_EJEMPLAR"))).contains(ErrorCode.COPY_HAS_HISTORY);
		assertThat(ConstraintErrors.from(violation("FK_PRESTAMO_SOCIO"))).contains(ErrorCode.MEMBER_HAS_HISTORY);
	}

	@Test
	void isCaseInsensitive() {
		assertThat(ConstraintErrors.from(new RuntimeException("violated (biblioteca.ux_socio_email)"))).contains(ErrorCode.DUPLICATE_EMAIL);
	}

	@Test
	void doesNotMistakeACheckForAUniqueConstraintOfASimilarName() {
		assertThat(ConstraintErrors.from(violation("CK_LIBRO_ISBN"))).isEmpty();
	}

	@Test
	void anUnknownConstraintOrNoMessageGivesNothing() {
		assertThat(ConstraintErrors.from(violation("CK_MULTA_PAGO"))).isEmpty();
		assertThat(ConstraintErrors.from(new RuntimeException())).isEmpty();
	}

}
