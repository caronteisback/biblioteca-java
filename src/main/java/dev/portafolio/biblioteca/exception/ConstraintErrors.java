package dev.portafolio.biblioteca.exception;

import java.util.Map;
import java.util.Optional;

/**
 * Translates an Oracle constraint violation into an {@link ErrorCode}.
 *
 * <p>Services check the rules before writing, but two simultaneous requests can both pass the
 * check. The constraint is the final judge, and this class turns its verdict into the same error
 * the check would have produced. Oracle names the constraint in the message, for example
 * {@code ORA-00001: unique constraint (BIBLIOTECA.UX_SOCIO_EMAIL) violated}.
 */
public final class ConstraintErrors {

	private static final Map<String, ErrorCode> BY_CONSTRAINT = Map.ofEntries(
			Map.entry("UX_PRESTAMO_EJEMPLAR_ACTIVO", ErrorCode.COPY_ALREADY_ON_LOAN),
			Map.entry("UK_LIBRO_ISBN", ErrorCode.DUPLICATE_ISBN),
			Map.entry("UX_SOCIO_EMAIL", ErrorCode.DUPLICATE_EMAIL),
			Map.entry("UK_EJEMPLAR_CODIGO", ErrorCode.DUPLICATE_COPY_CODE),
			Map.entry("CK_PRESTAMO_RENOVACIONES", ErrorCode.RENEWAL_LIMIT_REACHED),
			Map.entry("FK_EJEMPLAR_LIBRO", ErrorCode.BOOK_HAS_COPIES),
			Map.entry("FK_PRESTAMO_EJEMPLAR", ErrorCode.COPY_HAS_HISTORY),
			Map.entry("FK_PRESTAMO_SOCIO", ErrorCode.MEMBER_HAS_HISTORY));

	private ConstraintErrors() {
	}

	/** Looks for a known constraint name in the message of the exception and of its causes. */
	public static Optional<ErrorCode> from(Throwable error) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			String message = current.getMessage();
			if (message == null) {
				continue;
			}
			String upper = message.toUpperCase();
			for (Map.Entry<String, ErrorCode> entry : BY_CONSTRAINT.entrySet()) {
				if (upper.contains(entry.getKey())) {
					return Optional.of(entry.getValue());
				}
			}
			if (current.getCause() == current) {
				break;
			}
		}
		return Optional.empty();
	}

}
