package dev.portafolio.biblioteca.controller;

import dev.portafolio.biblioteca.dto.EjemplarRequest;
import dev.portafolio.biblioteca.dto.EjemplarResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.service.EjemplarService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EjemplarController {

	private final EjemplarService ejemplares;

	@PostMapping("/libros/{libroId}/ejemplares")
	ResponseEntity<EjemplarResponse> crear(@PathVariable Long libroId, @Valid @RequestBody EjemplarRequest request) {
		EjemplarResponse creado = ejemplares.crear(libroId, request);
		URI ubicacion = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/v1/ejemplares/{id}").buildAndExpand(creado.id()).toUri();
		return ResponseEntity.created(ubicacion).body(creado);
	}

	@GetMapping("/libros/{libroId}/ejemplares")
	PaginaDto<EjemplarResponse> listarPorLibro(
			@PathVariable Long libroId,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ejemplares.listarPorLibro(libroId, page, size);
	}

	@GetMapping("/ejemplares/{id}")
	EjemplarResponse obtener(@PathVariable Long id) {
		return ejemplares.obtener(id);
	}

	@PostMapping("/ejemplares/{id}/baja")
	EjemplarResponse darDeBaja(@PathVariable Long id) {
		return ejemplares.darDeBaja(id);
	}

	@PostMapping("/ejemplares/{id}/alta")
	EjemplarResponse reactivar(@PathVariable Long id) {
		return ejemplares.reactivar(id);
	}

	@DeleteMapping("/ejemplares/{id}")
	ResponseEntity<Void> eliminar(@PathVariable Long id) {
		ejemplares.eliminar(id);
		return ResponseEntity.noContent().build();
	}

}
