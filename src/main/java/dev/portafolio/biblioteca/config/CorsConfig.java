package dev.portafolio.biblioteca.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the browser frontend call the API from another origin.
 *
 * <p>Only the origins listed in {@code biblioteca.cors.origenes-permitidos} are accepted, and only
 * for {@code /api/**}. A wildcard is rejected at startup by the property validation, because
 * "any site may call this API from a user's browser" is almost never what is meant. The {@code Authorization}
 * header is allowed so a page can send its bearer token. The security filter chain uses this same
 * configuration, so a preflight request is answered before it asks for a token.
 *
 * <p>A request from a disallowed origin is refused by Spring before it reaches a controller, so its
 * 403 body is the framework's text and not the {@code {code, message}} shape.
 */
@Configuration
public class CorsConfig {

	@Bean
	WebMvcConfigurer corsConfigurer(BibliotecaProperties properties) {
		String[] origenes = properties.cors().origenesPermitidos().toArray(String[]::new);
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				if (origenes.length == 0) {
					return;
				}
				registry.addMapping("/api/**")
						.allowedOrigins(origenes)
						.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
						.allowedHeaders("Content-Type", "Authorization")
						.exposedHeaders("Location")
						.maxAge(3600);
			}
		};
	}

}
