package dev.portafolio.biblioteca;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.OracleContainer;

/**
 * Starts the same Oracle XE 21c image used by docker-compose.yml, so tests run
 * against the engine the application targets (no in-memory substitutes).
 *
 * <p>One container is shared by every Spring context of the test run: starting Oracle takes
 * several seconds, so it must not restart for each test class.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final String ORACLE_XE_IMAGE = "gvenzl/oracle-xe:21-slim-faststart";

	private static final OracleContainer ORACLE = new OracleContainer(ORACLE_XE_IMAGE);

	@Bean
	@ServiceConnection
	OracleContainer oracleXeContainer() {
		return ORACLE;
	}

}
