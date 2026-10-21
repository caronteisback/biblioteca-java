package dev.portafolio.biblioteca.controller;

import dev.portafolio.biblioteca.config.ErroresDocumentados;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.dto.PrestamoRequest;
import dev.portafolio.biblioteca.dto.PrestamoResponse;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.service.PrestamoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Loans")
public class PrestamoController {

	private final PrestamoService prestamos;

	@PostMapping
	@Operation(operationId = "createLoan", summary = "Lend a copy to a member",
			description = "Eight checks run in a fixed order and the first that fails decides the error: member and copy exist, member active, copy active, copy not on loan, no overdue loan, no pending fine, score at least the minimum, fewer than the maximum active loans. Nothing is written if one fails. The loan date is today and the due date is 14 days later, both set by the server.")
	@ApiResponse(responseCode = "201", description = "Created.", content = @Content(schema = @Schema(implementation = PrestamoResponse.class)))
	@ErroresDocumentados({
			ErrorCode.MEMBER_NOT_FOUND, ErrorCode.COPY_NOT_FOUND,
			ErrorCode.COPY_ALREADY_ON_LOAN, ErrorCode.CONCURRENT_UPDATE,
			ErrorCode.MEMBER_INACTIVE, ErrorCode.COPY_INACTIVE, ErrorCode.MEMBER_HAS_OVERDUE_LOAN,
			ErrorCode.MEMBER_HAS_PENDING_FINE, ErrorCode.MEMBER_BLOCKED_BY_SCORE, ErrorCode.LOAN_LIMIT_REACHED })
	ResponseEntity<PrestamoResponse> crear(@Valid @RequestBody PrestamoRequest request) {
		PrestamoResponse creado = prestamos.crear(request);
		URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
		return ResponseEntity.created(ubicacion).body(creado);
	}

	@GetMapping("/{id}")
	@Operation(operationId = "getLoan", summary = "Get a loan", description = "Includes the days of delay as of today, the alerts, and the fine when the loan was returned late.")
	@ErroresDocumentados(ErrorCode.LOAN_NOT_FOUND)
	PrestamoResponse obtener(@PathVariable Long id) {
		return prestamos.obtener(id);
	}

	@GetMapping
	@Operation(operationId = "listLoans", summary = "List loans", description = "Paginated and always ordered by id.")
	PaginaDto<PrestamoResponse> listar(
			@Parameter(description = "Only the loans of this member.") @RequestParam(required = false) Long socioId,
			@Parameter(description = "`true` lists the loans not yet returned, `false` the returned ones; leave it out for all.")
			@RequestParam(required = false) Boolean activo,
			@Parameter(description = "Page number, starting at 0.") @RequestParam(defaultValue = "0") @Min(0) int page,
			@Parameter(description = "Page size, from 1 to 100.") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return prestamos.listar(socioId, activo, page, size);
	}

	@PostMapping("/{id}/renovacion")
	@Operation(operationId = "renewLoan", summary = "Renew a loan",
			description = "Adds one term to the current due date (not to today), up to the maximum number of renewals. It never creates a new loan. A fine or a low score do not block a renewal.")
	@ErroresDocumentados({
			ErrorCode.LOAN_NOT_FOUND, ErrorCode.LOAN_ALREADY_RETURNED, ErrorCode.CONCURRENT_UPDATE,
			ErrorCode.MEMBER_INACTIVE, ErrorCode.RENEWAL_NOT_ALLOWED_OVERDUE, ErrorCode.RENEWAL_LIMIT_REACHED })
	PrestamoResponse renovar(@PathVariable Long id) {
		return prestamos.renovar(id);
	}

	@PostMapping("/{id}/devolucion")
	@Operation(operationId = "returnLoan", summary = "Return a loan",
			description = "Always accepted for an active loan, even if the member or the copy were deactivated meanwhile. A return after the due date creates a fine of one sol per day late; returning on the due date is on time.")
	@ErroresDocumentados({ ErrorCode.LOAN_NOT_FOUND, ErrorCode.LOAN_ALREADY_RETURNED, ErrorCode.CONCURRENT_UPDATE })
	PrestamoResponse devolver(@PathVariable Long id) {
		return prestamos.devolver(id);
	}

}
