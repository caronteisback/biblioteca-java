package dev.portafolio.biblioteca.controller;

import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.dto.PrestamoRequest;
import dev.portafolio.biblioteca.dto.PrestamoResponse;
import dev.portafolio.biblioteca.service.PrestamoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/prestamos")
@RequiredArgsConstructor
public class PrestamoController {

	private final PrestamoService prestamos;

	@PostMapping
	ResponseEntity<PrestamoResponse> crear(@Valid @RequestBody PrestamoRequest request) {
		PrestamoResponse creado = prestamos.crear(request);
		URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
		return ResponseEntity.created(ubicacion).body(creado);
	}

	@GetMapping("/{id}")
	PrestamoResponse obtener(@PathVariable Long id) {
		return prestamos.obtener(id);
	}

	/** {@code socioId} limits to one member; {@code activo=true} lists loans not returned, {@code false} the returned ones. */
	@GetMapping
	PaginaDto<PrestamoResponse> listar(
			@RequestParam(required = false) Long socioId,
			@RequestParam(required = false) Boolean activo,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return prestamos.listar(socioId, activo, page, size);
	}

	@PostMapping("/{id}/renovacion")
	PrestamoResponse renovar(@PathVariable Long id) {
		return prestamos.renovar(id);
	}

	@PostMapping("/{id}/devolucion")
	PrestamoResponse devolver(@PathVariable Long id) {
		return prestamos.devolver(id);
	}

}
