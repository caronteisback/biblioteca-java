package dev.portafolio.biblioteca.service;

import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ErrorCode;
import java.util.regex.Pattern;

/** ISBN rule of the business rules: 10 or 13 digits (a 10-digit ISBN may end in X); the check digit is not verified. */
public final class Isbn {

	private static final Pattern VALIDO = Pattern.compile("^([0-9]{9}[0-9X]|[0-9]{13})$");

	private Isbn() {
	}

	/** Removes hyphens and spaces and upper-cases an X. Throws VALIDATION_ERROR when the result is not an ISBN. */
	public static String normalizar(String texto) {
		String limpio = texto == null ? "" : texto.replaceAll("[\\s-]", "").toUpperCase();
		if (!VALIDO.matcher(limpio).matches()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The request is not valid. isbn: must have 10 or 13 digits.");
		}
		return limpio;
	}

}
