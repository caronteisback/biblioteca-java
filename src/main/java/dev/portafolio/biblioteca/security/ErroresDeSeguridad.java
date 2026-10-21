package dev.portafolio.biblioteca.security;

import dev.portafolio.biblioteca.dto.ErrorResponse;
import dev.portafolio.biblioteca.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The security filters answer before any controller runs, so the controller advice never sees these
 * failures. This class gives them the same {@code {code, message}} shape as every other error, and the
 * same text whatever went wrong (no token, a bad signature, an expired token): the client learns nothing
 * about why a token was refused.
 */
@Component
@RequiredArgsConstructor
class ErroresDeSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final JsonMapper json;

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
		response.setHeader("WWW-Authenticate", "Bearer");
		escribir(response, ErrorCode.UNAUTHENTICATED);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
		escribir(response, ErrorCode.FORBIDDEN);
	}

	private void escribir(HttpServletResponse response, ErrorCode codigo) throws IOException {
		response.setStatus(codigo.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(json.writeValueAsString(new ErrorResponse(codigo.name(), codigo.message())));
	}

}
