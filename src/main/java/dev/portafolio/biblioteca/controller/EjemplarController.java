package dev.portafolio.biblioteca.controller;

import dev.portafolio.biblioteca.config.ErroresDocumentados;
import dev.portafolio.biblioteca.dto.EjemplarRequest;
import dev.portafolio.biblioteca.dto.EjemplarResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.service.EjemplarService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Copies")
public class EjemplarController {

	private final EjemplarService ejemplares;

	@PostMapping("/libros/{libroId}/ejemplares")
	@Operation(operationId = "createCopy", summary = "Add a copy to a book")
	@ApiResponse(responseCode = "201", description = "Created.", content = @Content(schema = @Schema(implementation = EjemplarResponse.class)))
	@ErroresDocumentados({ ErrorCode.BOOK_NOT_FOUND, ErrorCode.DUPLICATE_COPY_CODE })
	ResponseEntity<EjemplarResponse> crear(@PathVariable Long libroId, @Valid @RequestBody EjemplarRequest request) {
		EjemplarResponse creado = ejemplares.crear(libroId, request);
		URI ubicacion = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/v1/ejemplares/{id}").buildAndExpand(creado.id()).toUri();
		return ResponseEntity.created(ubicacion).body(creado);
	}

	@GetMapping("/libros/{libroId}/ejemplares")
	@Operation(operationId = "listCopiesOfBook", summary = "List the copies of a book", description = "Paginated and always ordered by id.")
	@ErroresDocumentados(ErrorCode.BOOK_NOT_FOUND)
	PaginaDto<EjemplarResponse> listarPorLibro(
			@PathVariable Long libroId,
			@Parameter(description = "Page number, starting at 0.") @RequestParam(defaultValue = "0") @Min(0) int page,
			@Parameter(description = "Page size, from 1 to 100.") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return ejemplares.listarPorLibro(libroId, page, size);
	}

	@GetMapping("/ejemplares/{id}")
	@Operation(operationId = "getCopy", summary = "Get a copy", description = "`disponible` is calculated: the copy is active and has no active loan.")
	@ErroresDocumentados(ErrorCode.COPY_NOT_FOUND)
	EjemplarResponse obtener(@PathVariable Long id) {
		return ejemplares.obtener(id);
	}

	@PostMapping("/ejemplares/{id}/baja")
	@Operation(operationId = "retireCopy", summary = "Retire a copy", description = "For a damaged or lost copy. A copy that is on loan cannot be retired. Retiring twice is harmless.")
	@ErroresDocumentados({ ErrorCode.COPY_NOT_FOUND, ErrorCode.COPY_HAS_ACTIVE_LOAN, ErrorCode.CONCURRENT_UPDATE })
	EjemplarResponse darDeBaja(@PathVariable Long id) {
		return ejemplares.darDeBaja(id);
	}

	@PostMapping("/ejemplares/{id}/alta")
	@Operation(operationId = "restoreCopy", summary = "Bring a retired copy back")
	@ErroresDocumentados({ ErrorCode.COPY_NOT_FOUND, ErrorCode.CONCURRENT_UPDATE })
	EjemplarResponse reactivar(@PathVariable Long id) {
		return ejemplares.reactivar(id);
	}

	@DeleteMapping("/ejemplares/{id}")
	@Operation(operationId = "deleteCopy", summary = "Delete a copy", description = "A copy that appears in any loan is never deleted, only retired.")
	@ApiResponse(responseCode = "204", description = "Deleted.")
	@ErroresDocumentados({ ErrorCode.COPY_NOT_FOUND, ErrorCode.COPY_HAS_HISTORY, ErrorCode.CONCURRENT_UPDATE })
	ResponseEntity<Void> eliminar(@PathVariable Long id) {
		ejemplares.eliminar(id);
		return ResponseEntity.noContent().build();
	}

}
