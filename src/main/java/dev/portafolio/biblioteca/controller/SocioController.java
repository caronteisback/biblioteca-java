package dev.portafolio.biblioteca.controller;

import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.dto.SocioRequest;
import dev.portafolio.biblioteca.dto.SocioResponse;
import dev.portafolio.biblioteca.dto.SocioResumen;
import dev.portafolio.biblioteca.service.SocioService;
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
@RequestMapping("/api/v1/socios")
@RequiredArgsConstructor
public class SocioController {

	private final SocioService socios;

	@PostMapping
	ResponseEntity<SocioResponse> crear(@Valid @RequestBody SocioRequest request) {
		SocioResponse creado = socios.crear(request);
		URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
		return ResponseEntity.created(ubicacion).body(creado);
	}

	/** One member with the score and the alerts. */
	@GetMapping("/{id}")
	SocioResponse obtener(@PathVariable Long id) {
		return socios.obtener(id);
	}

	/** {@code q} filters by name or email, ignoring case. The list carries no score. */
	@GetMapping
	PaginaDto<SocioResumen> listar(
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return socios.listar(q, page, size);
	}

	@PutMapping("/{id}")
	SocioResponse actualizar(@PathVariable Long id, @Valid @RequestBody SocioRequest request) {
		return socios.actualizar(id, request);
	}

	@PostMapping("/{id}/desactivar")
	SocioResponse desactivar(@PathVariable Long id) {
		return socios.desactivar(id);
	}

	@PostMapping("/{id}/reactivar")
	SocioResponse reactivar(@PathVariable Long id) {
		return socios.reactivar(id);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> eliminar(@PathVariable Long id) {
		socios.eliminar(id);
		return ResponseEntity.noContent().build();
	}

}
