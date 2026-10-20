package dev.portafolio.biblioteca;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.OracleContainer;

/**
 * Starts the same Oracle XE 21c image used by docker-compose.yml, so tests run
 * against the engine the application targets (no in-memory substitutes).
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	static final String ORACLE_XE_IMAGE = "gvenzl/oracle-xe:21-slim-faststart";

	@Bean
	@ServiceConnection
	OracleContainer oracleXeContainer() {
		return new OracleContainer(ORACLE_XE_IMAGE);
	}

}
