package dev.portafolio.biblioteca.controller;

import dev.portafolio.biblioteca.dto.LibroRequest;
import dev.portafolio.biblioteca.dto.LibroResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.service.LibroService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/libros")
@RequiredArgsConstructor
public class LibroController {

	private final LibroService libros;

	@PostMapping
	ResponseEntity<LibroResponse> crear(@Valid @RequestBody LibroRequest request) {
		LibroResponse creado = libros.crear(request);
		URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
		return ResponseEntity.created(ubicacion).body(creado);
	}

	@GetMapping("/{id}")
	LibroResponse obtener(@PathVariable Long id) {
		return libros.obtener(id);
	}

	/** {@code q} filters by title or author, ignoring case. */
	@GetMapping
	PaginaDto<LibroResponse> listar(
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return libros.listar(q, page, size);
	}

	@PutMapping("/{id}")
	LibroResponse actualizar(@PathVariable Long id, @Valid @RequestBody LibroRequest request) {
		return libros.actualizar(id, request);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> eliminar(@PathVariable Long id) {
		libros.eliminar(id);
		return ResponseEntity.noContent().build();
	}

}
