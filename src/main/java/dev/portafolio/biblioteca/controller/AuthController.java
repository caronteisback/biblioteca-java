package dev.portafolio.biblioteca.controller;

import dev.portafolio.biblioteca.config.ErroresDocumentados;
import dev.portafolio.biblioteca.dto.LoginRequest;
import dev.portafolio.biblioteca.dto.LoginResponse;
import dev.portafolio.biblioteca.exception.ErrorCode;
import dev.portafolio.biblioteca.service.AutenticacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

	private final AutenticacionService autenticacion;

	@PostMapping("/login")
	@Operation(operationId = "login", summary = "Sign in",
			description = "Exchanges an email and a password for a bearer token valid for 60 minutes. After 5 failed attempts in a row for the same email, further attempts are refused for 5 minutes (`TOO_MANY_ATTEMPTS`). The answer never says whether the email exists: a wrong email and a wrong password look the same (`INVALID_CREDENTIALS`).")
	@ErroresDocumentados({ ErrorCode.INVALID_CREDENTIALS, ErrorCode.TOO_MANY_ATTEMPTS })
	LoginResponse entrar(@Valid @RequestBody LoginRequest request) {
		return autenticacion.entrar(request);
	}

}
