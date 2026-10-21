package dev.portafolio.biblioteca.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.portafolio.biblioteca.BaseIntegracionTest;
import dev.portafolio.biblioteca.entity.Socio;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** The rules of the accounts that the schema keeps even if the application code has a bug. */
class EsquemaUsuarioTests extends BaseIntegracionTest {

	private void insertar(String email, String rol, Long socioId) {
		jdbc.update("insert into usuario (email, password_hash, rol, socio_id) values (?, ?, ?, ?)", email, "{bcrypt}x", rol, socioId);
	}

	@Test
	void flywayAppliedTheAccountsMigration() {
		Integer applied = jdbc.queryForObject(
				"select count(*) from \"flyway_schema_history\" where \"version\" = '2' and \"success\" = 1", Integer.class);

		assertThat(applied).isEqualTo(1);
	}

	@Test
	void aMemberAccountNeedsAMemberAndALibrarianAccountMustNotHaveOne() {
		Socio socio = socio();

		assertThatThrownBy(() -> insertar("sin-socio@example.org", "MEMBER", null))
				.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("CK_USUARIO_SOCIO");
		assertThatThrownBy(() -> insertar("con-socio@example.org", "LIBRARIAN", socio.getId()))
				.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("CK_USUARIO_SOCIO");
		assertThatCode(() -> insertar("socio@example.org", "MEMBER", socio.getId())).doesNotThrowAnyException();
		assertThatCode(() -> insertar("bibliotecaria@example.org", "LIBRARIAN", null)).doesNotThrowAnyException();
	}

	@Test
	void onlyTheTwoRolesExist() {
		// An unknown role breaks the role check and the member check (it is neither of the two combinations): Oracle names one.
		assertThatThrownBy(() -> insertar("admin@example.org", "ADMIN", null))
				.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("CK_USUARIO_");
	}

	@Test
	void anEmailIsUniqueIgnoringCase() {
		insertar("Unica@Example.org", "LIBRARIAN", null);

		assertThatThrownBy(() -> insertar("unica@example.ORG", "LIBRARIAN", null))
				.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("UX_USUARIO_EMAIL");
	}

	@Test
	void aMemberHasAtMostOneAccountButManyLibrariansAreAllowed() {
		Socio socio = socio();
		insertar("uno@example.org", "MEMBER", socio.getId());

		assertThatThrownBy(() -> insertar("dos@example.org", "MEMBER", socio.getId()))
				.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("UX_USUARIO_SOCIO");
		assertThatCode(() -> {
			insertar("bib1@example.org", "LIBRARIAN", null);
			insertar("bib2@example.org", "LIBRARIAN", null);
		}).doesNotThrowAnyException();
	}

	@Test
	void anAccountCannotPointToAMemberThatDoesNotExist() {
		assertThatThrownBy(() -> insertar("fantasma@example.org", "MEMBER", 987654321L))
				.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("FK_USUARIO_SOCIO");
	}

}
