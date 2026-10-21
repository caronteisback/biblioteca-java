package dev.portafolio.biblioteca.controller;

import dev.portafolio.biblioteca.dto.MultaResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.service.MultaService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/multas")
@RequiredArgsConstructor
public class MultaController {

	private final MultaService multas;

	@GetMapping("/{id}")
	MultaResponse obtener(@PathVariable Long id) {
		return multas.obtener(id);
	}

	/** {@code socioId} limits to the fines of one member; {@code estado} is PENDING or PAID. */
	@GetMapping
	PaginaDto<MultaResponse> listar(
			@RequestParam(required = false) Long socioId,
			@RequestParam(required = false) EstadoMulta estado,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return multas.listar(socioId, estado, page, size);
	}

	@PostMapping("/{id}/pago")
	MultaResponse pagar(@PathVariable Long id) {
		return multas.pagar(id);
	}

}
