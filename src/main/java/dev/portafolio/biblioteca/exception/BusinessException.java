package dev.portafolio.biblioteca.exception;

import lombok.Getter;

/**
 * A business rule was broken or a resource was not found. The handler turns it into the HTTP
 * status of its {@link ErrorCode} and a {@code {code, message}} body.
 *
 * <p>The message must never contain internals (SQL, table names, stack traces).
 */
@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode code;

	public BusinessException(ErrorCode code) {
		super(code.message());
		this.code = code;
	}

	/** Same code with a more specific message, for example one that names the configured limit. */
	public BusinessException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

}
