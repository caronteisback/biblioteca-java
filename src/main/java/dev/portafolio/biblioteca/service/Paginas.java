package dev.portafolio.biblioteca.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Builds the page request from plain numbers. The client cannot choose the sort: accepting a free
 * sort property would let a request name any field and turn into a server error.
 */
final class Paginas {

	private Paginas() {
	}

	static Pageable por(int page, int size) {
		return PageRequest.of(page, size, Sort.by("id"));
	}

	static boolean sinFiltro(String texto) {
		return texto == null || texto.isBlank();
	}

}
