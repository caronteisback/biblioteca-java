package dev.portafolio.biblioteca;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class BibliotecaJavaApplicationTests {

	@Test
	void contextLoads() {
		// Boots the full context: connects to Oracle XE and runs Flyway + JPA schema validation.
	}

}
