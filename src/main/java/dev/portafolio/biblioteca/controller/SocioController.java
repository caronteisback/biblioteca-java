package dev.portafolio.biblioteca.controller;

import dev.portafolio.biblioteca.config.ErroresDocumentados;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.dto.SocioRequest;
import dev.portafolio.biblioteca.dto.SocioResponse;
import dev.portafolio.biblioteca.dto.SocioResumen;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.service.SocioService;
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
@Tag(name = "Members")
public class SocioController {

	private final SocioService socios;

	@PostMapping
	@Operation(operationId = "createMember", summary = "Register a member", description = "The registration date is today, set by the server. The answer carries score 100 and no alerts.")
	@ApiResponse(responseCode = "201", description = "Created.", content = @Content(schema = @Schema(implementation = SocioResponse.class)))
	@ErroresDocumentados(ErrorCode.DUPLICATE_EMAIL)
	ResponseEntity<SocioResponse> crear(@Valid @RequestBody SocioRequest request) {
		SocioResponse creado = socios.crear(request);
		URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
		return ResponseEntity.created(ubicacion).body(creado);
	}

	@GetMapping("/{id}")
	@Operation(operationId = "getMember", summary = "Get a member with score and alerts",
			description = "The score and the alerts are calculated now from the loan history and are never stored.")
	@ErroresDocumentados(ErrorCode.MEMBER_NOT_FOUND)
	SocioResponse obtener(@PathVariable Long id) {
		return socios.obtener(id);
	}

	@GetMapping
	@Operation(operationId = "listMembers", summary = "List members",
			description = "Paginated and always ordered by id. A row carries no score: calculating it needs the whole loan history, so ask for one member to get it.")
	PaginaDto<SocioResumen> listar(
			@Parameter(description = "Text that the name or the email must contain, ignoring case. `%` and `_` are literal.")
			@RequestParam(required = false) String q,
			@Parameter(description = "Page number, starting at 0.") @RequestParam(defaultValue = "0") @Min(0) int page,
			@Parameter(description = "Page size, from 1 to 100.") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return socios.listar(q, page, size);
	}

	@PutMapping("/{id}")
	@Operation(operationId = "updateMember", summary = "Replace the name and the email of a member")
	@ErroresDocumentados({ ErrorCode.MEMBER_NOT_FOUND, ErrorCode.DUPLICATE_EMAIL })
	SocioResponse actualizar(@PathVariable Long id, @Valid @RequestBody SocioRequest request) {
		return socios.actualizar(id, request);
	}

	@PostMapping("/{id}/desactivar")
	@Operation(operationId = "deactivateMember", summary = "Deactivate a member", description = "A member with active loans cannot be deactivated. An inactive member cannot borrow or renew, but can still return.")
	@ErroresDocumentados({ ErrorCode.MEMBER_NOT_FOUND, ErrorCode.MEMBER_HAS_ACTIVE_LOANS, ErrorCode.CONCURRENT_UPDATE })
	SocioResponse desactivar(@PathVariable Long id) {
		return socios.desactivar(id);
	}

	@PostMapping("/{id}/reactivar")
	@Operation(operationId = "reactivateMember", summary = "Reactivate a member")
	@ErroresDocumentados({ ErrorCode.MEMBER_NOT_FOUND, ErrorCode.CONCURRENT_UPDATE })
	SocioResponse reactivar(@PathVariable Long id) {
		return socios.reactivar(id);
	}

	@DeleteMapping("/{id}")
	@Operation(operationId = "deleteMember", summary = "Delete a member", description = "A member that appears in any loan is never deleted, only deactivated.")
	@ApiResponse(responseCode = "204", description = "Deleted.")
	@ErroresDocumentados({ ErrorCode.MEMBER_NOT_FOUND, ErrorCode.MEMBER_HAS_HISTORY, ErrorCode.CONCURRENT_UPDATE })
	ResponseEntity<Void> eliminar(@PathVariable Long id) {
		socios.eliminar(id);
		return ResponseEntity.noContent().build();
	}

}
