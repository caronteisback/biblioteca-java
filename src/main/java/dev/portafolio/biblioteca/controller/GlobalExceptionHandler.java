package dev.portafolio.biblioteca.controller;

import dev.portafolio.biblioteca.dto.ErrorResponse;
import dev.portafolio.biblioteca.exception.BusinessException;
import dev.portafolio.biblioteca.exception.ConstraintErrors;
import dev.portafolio.biblioteca.exception.ErrorCode;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * One place that turns every failure into {@code {code, message}} with the right HTTP status.
 *
 * <p>Rule of this class: nothing internal reaches the client. Business and validation errors carry
 * clear messages; anything unexpected is logged here, with its stack trace, and answered with a
 * generic message.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ErrorResponse> business(BusinessException error) {
		return respond(error.getCode(), error.getMessage());
	}

	@ExceptionHandler(BindException.class)
	ResponseEntity<ErrorResponse> invalidBody(BindException error) {
		List<String> problems = error.getBindingResult().getAllErrors().stream()
				.map(problem -> (problem instanceof FieldError field ? field.getField() + ": " : "") + problem.getDefaultMessage())
				.sorted()
				.toList();
		return validation(problems);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ErrorResponse> invalidParameters(HandlerMethodValidationException error) {
		List<String> problems = error.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(problem -> result.getMethodParameter().getParameterName() + ": " + problem.getDefaultMessage()))
				.sorted()
				.toList();
		return validation(problems);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ErrorResponse> constraintViolations(ConstraintViolationException error) {
		List<String> problems = error.getConstraintViolations().stream()
				.map((ConstraintViolation<?> violation) -> violation.getPropertyPath() + ": " + violation.getMessage())
				.sorted()
				.toList();
		return validation(problems);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> malformedBody(HttpMessageNotReadableException error) {
		log.debug("Malformed request body: {}", error.getMessage());
		return respond(ErrorCode.VALIDATION_ERROR, "The request body is missing or malformed.");
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> wrongType(MethodArgumentTypeMismatchException error) {
		return respond(ErrorCode.VALIDATION_ERROR, "The value of '" + error.getName() + "' is not valid.");
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ErrorResponse> missingParameter(MissingServletRequestParameterException error) {
		return respond(ErrorCode.VALIDATION_ERROR, "The parameter '" + error.getParameterName() + "' is required.");
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ErrorResponse> endpointNotFound(NoResourceFoundException error) {
		return respond(ErrorCode.ENDPOINT_NOT_FOUND, ErrorCode.ENDPOINT_NOT_FOUND.message());
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException error) {
		return respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.message());
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ErrorResponse> unsupportedMediaType(HttpMediaTypeNotSupportedException error) {
		return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE.message());
	}

	/** The lock wait ran out: another request holds the same member or loan. The client may retry. */
	@ExceptionHandler({ PessimisticLockingFailureException.class, PessimisticLockException.class, LockTimeoutException.class })
	ResponseEntity<ErrorResponse> lockNotObtained(Exception error) {
		log.warn("Lock not obtained: {}", error.getMessage());
		return respond(ErrorCode.CONCURRENT_UPDATE, ErrorCode.CONCURRENT_UPDATE.message());
	}

	/** A constraint stopped a write that two simultaneous requests both thought was allowed. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ErrorResponse> integrity(DataIntegrityViolationException error) {
		Optional<ErrorCode> known = ConstraintErrors.from(error);
		if (known.isPresent()) {
			return respond(known.get(), known.get().message());
		}
		return unexpected(error);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> unexpected(Exception error) {
		log.error("Unexpected error", error);
		return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.message());
	}

	private ResponseEntity<ErrorResponse> validation(List<String> problems) {
		String detail = problems.isEmpty() ? "" : " " + problems.stream().collect(Collectors.joining("; ")) + ".";
		return respond(ErrorCode.VALIDATION_ERROR, "The request is not valid." + detail);
	}

	private ResponseEntity<ErrorResponse> respond(ErrorCode code, String message) {
		return ResponseEntity.status(code.status()).body(new ErrorResponse(code.name(), message));
	}

}
