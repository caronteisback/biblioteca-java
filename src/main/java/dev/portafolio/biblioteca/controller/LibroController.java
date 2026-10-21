package dev.portafolio.biblioteca.controller;

import dev.portafolio.biblioteca.config.ErroresDocumentados;
import dev.portafolio.biblioteca.dto.LibroRequest;
import dev.portafolio.biblioteca.dto.LibroResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.service.LibroService;
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
@RequestMapping("/api/v1/libros")
@RequiredArgsConstructor
@Tag(name = "Books")
public class LibroController {

	private final LibroService libros;

	@PostMapping
	@Operation(operationId = "createBook", summary = "Create a book", description = "The ISBN may be written with hyphens or spaces; it is stored as 10 or 13 characters (a 10-digit ISBN may end in X).")
	@ApiResponse(responseCode = "201", description = "Created.", content = @Content(schema = @Schema(implementation = LibroResponse.class)))
	@ErroresDocumentados(ErrorCode.DUPLICATE_ISBN)
	ResponseEntity<LibroResponse> crear(@Valid @RequestBody LibroRequest request) {
		LibroResponse creado = libros.crear(request);
		URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(creado.id()).toUri();
		return ResponseEntity.created(ubicacion).body(creado);
	}

	@GetMapping("/{id}")
	@Operation(operationId = "getBook", summary = "Get a book")
	@ErroresDocumentados(ErrorCode.BOOK_NOT_FOUND)
	LibroResponse obtener(@PathVariable Long id) {
		return libros.obtener(id);
	}

	@GetMapping
	@Operation(operationId = "listBooks", summary = "List books", description = "Paginated and always ordered by id. A sort parameter is ignored.")
	PaginaDto<LibroResponse> listar(
			@Parameter(description = "Text that the title or the author must contain, ignoring case. `%` and `_` are literal.")
			@RequestParam(required = false) String q,
			@Parameter(description = "Page number, starting at 0.") @RequestParam(defaultValue = "0") @Min(0) int page,
			@Parameter(description = "Page size, from 1 to 100.") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return libros.listar(q, page, size);
	}

	@PutMapping("/{id}")
	@Operation(operationId = "updateBook", summary = "Replace the data of a book")
	@ErroresDocumentados({ ErrorCode.BOOK_NOT_FOUND, ErrorCode.DUPLICATE_ISBN })
	LibroResponse actualizar(@PathVariable Long id, @Valid @RequestBody LibroRequest request) {
		return libros.actualizar(id, request);
	}

	@DeleteMapping("/{id}")
	@Operation(operationId = "deleteBook", summary = "Delete a book", description = "Only a book without copies can be deleted.")
	@ApiResponse(responseCode = "204", description = "Deleted.")
	@ErroresDocumentados({ ErrorCode.BOOK_NOT_FOUND, ErrorCode.BOOK_HAS_COPIES })
	ResponseEntity<Void> eliminar(@PathVariable Long id) {
		libros.eliminar(id);
		return ResponseEntity.noContent().build();
	}

}
