package dev.portafolio.biblioteca.config;

import dev.portafolio.biblioteca.exception.ErrorCode;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The business errors an endpoint can answer, written next to the method that raises them.
 *
 * <p>{@link OpenApiConfig} turns this list into the error responses of the OpenAPI document, grouped
 * by status, so the contract names every code a client has to handle. The generic ones (400 for a bad
 * request, 415 for a wrong content type, 500) are added by the configuration and are not listed here.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ErroresDocumentados {

	ErrorCode[] value();

}
