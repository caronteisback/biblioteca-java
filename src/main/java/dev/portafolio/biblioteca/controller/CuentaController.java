package dev.portafolio.biblioteca.controller;

import dev.portafolio.biblioteca.dto.MultaResponse;
import dev.portafolio.biblioteca.dto.PaginaDto;
import dev.portafolio.biblioteca.dto.PrestamoResponse;
import dev.portafolio.biblioteca.dto.UsuarioResponse;
import dev.portafolio.biblioteca.entity.EstadoMulta;
import dev.portafolio.biblioteca.service.CuentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** What the signed-in account can see about itself. The member is read from the token, never from the request. */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Account")
public class CuentaController {

	private final CuentaService cuenta;

	@GetMapping
	@Operation(operationId = "getMe", summary = "Get the signed-in user",
			description = "The signed-in user. For a member it carries the member data, with the score and the alerts calculated now.")
	UsuarioResponse yo(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return cuenta.yo(Long.valueOf(jwt.getSubject()));
	}

	@GetMapping("/prestamos")
	@Operation(operationId = "listMyLoans", summary = "List my loans",
			description = "Only the loans of the signed-in member, paginated and ordered by id. A librarian has no loans of their own.")
	PaginaDto<PrestamoResponse> misPrestamos(
			@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "`true` lists the loans not yet returned, `false` the returned ones; leave it out for all.")
			@RequestParam(required = false) Boolean activo,
			@Parameter(description = "Page number, starting at 0.") @RequestParam(defaultValue = "0") @Min(0) int page,
			@Parameter(description = "Page size, from 1 to 100.") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return cuenta.misPrestamos(socioDe(jwt), activo, page, size);
	}

	@GetMapping("/multas")
	@Operation(operationId = "listMyFines", summary = "List my fines",
			description = "Only the fines of the signed-in member, paginated and ordered by id. A librarian has no fines of their own.")
	PaginaDto<MultaResponse> misMultas(
			@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
			@Parameter(description = "Only the fines in this status.") @RequestParam(required = false) EstadoMulta estado,
			@Parameter(description = "Page number, starting at 0.") @RequestParam(defaultValue = "0") @Min(0) int page,
			@Parameter(description = "Page size, from 1 to 100.") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
		return cuenta.misMultas(socioDe(jwt), estado, page, size);
	}

	/** The member of the account, from the token. A librarian has none. */
	private static Long socioDe(Jwt jwt) {
		Number socio = jwt.getClaim("socioId");
		return socio == null ? null : socio.longValue();
	}

}
