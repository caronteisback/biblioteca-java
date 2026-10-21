package dev.portafolio.biblioteca.controller;

import dev.portafolio.biblioteca.config.ErroresDocumentados;
import dev.portafolio.biblioteca.dto.MultaResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.service.MultaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Fines")
public class MultaController {

	private final MultaService multas;

	@GetMapping("/{id}")
	@Operation(operationId = "getFine", summary = "Get a fine")
	@ErroresDocumentados(ErrorCode.FINE_NOT_FOUND)
	MultaResponse obtener(@PathVariable Long id) {
		return multas.obtener(id);
	}

	@GetMapping
	@Operation(operationId = "listFines", summary = "List fines", description = "Paginated and always ordered by id.")
	PaginaDto<MultaResponse> listar(
			@Parameter(description = "Only the fines of the loans of this member.") @RequestParam(required = false) Long socioId,
			@Parameter(description = "Only the fines in this status.") @RequestParam(required = false) EstadoMulta estado,
			@Parameter(description = "Page number, starting at 0.") @RequestParam(defaultValue = "0") @Min(0) int page,
			@Parameter(description = "Page size, from 1 to 100.") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return multas.listar(socioId, estado, page, size);
	}

	@PostMapping("/{id}/pago")
	@Operation(operationId = "payFine", summary = "Pay a fine",
			description = "Pays the whole amount: there is no payment gateway and no partial payment. Paying unblocks new loans but does not give score points back.")
	@ErroresDocumentados({ ErrorCode.FINE_NOT_FOUND, ErrorCode.FINE_ALREADY_PAID, ErrorCode.CONCURRENT_UPDATE })
	MultaResponse pagar(@PathVariable Long id) {
		return multas.pagar(id);
	}

}
