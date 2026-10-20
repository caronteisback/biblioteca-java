package dev.portafolio.biblioteca.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Stable shape of every paginated answer. It is the project's own record, not Spring's page, so the
 * contract does not depend on the framework and another backend can reproduce it exactly.
 */
public record PaginaDto<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

	public static <E, T> PaginaDto<T> de(Page<E> page, Function<E, T> mapper) {
		return new PaginaDto<>(
				page.getContent().stream().map(mapper).toList(),
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages());
	}

}
