package dev.portafolio.biblioteca.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Every error the API can return. The name is the stable {@code code} of the response body; the
 * message is the default English text. Source of truth: the error catalog in docs/business-rules.md.
 */
public enum ErrorCode {

	VALIDATION_ERROR(400, "The request is not valid."),

	BOOK_NOT_FOUND(404, "The book does not exist."),
	COPY_NOT_FOUND(404, "The copy does not exist."),
	MEMBER_NOT_FOUND(404, "The member does not exist."),
	LOAN_NOT_FOUND(404, "The loan does not exist."),
	FINE_NOT_FOUND(404, "The fine does not exist."),
	ENDPOINT_NOT_FOUND(404, "The endpoint does not exist."),

	UNAUTHENTICATED(401, "You must sign in to use this operation."),
	INVALID_CREDENTIALS(401, "The email or the password is not correct."),
	FORBIDDEN(403, "Your role is not allowed to use this operation."),
	TOO_MANY_ATTEMPTS(429, "Too many failed attempts. Try again in a few minutes."),

	METHOD_NOT_ALLOWED(405, "The HTTP method is not allowed for this endpoint."),
	UNSUPPORTED_MEDIA_TYPE(415, "The content type is not supported. Use application/json."),

	COPY_ALREADY_ON_LOAN(409, "The copy already has an active loan."),
	LOAN_ALREADY_RETURNED(409, "The loan was already returned."),
	FINE_ALREADY_PAID(409, "The fine is already paid."),
	DUPLICATE_ISBN(409, "A book with that ISBN already exists."),
	DUPLICATE_EMAIL(409, "A member with that email already exists."),
	DUPLICATE_COPY_CODE(409, "A copy with that code already exists."),
	BOOK_HAS_COPIES(409, "The book has copies and cannot be deleted."),
	COPY_HAS_HISTORY(409, "The copy appears in loans and cannot be deleted."),
	COPY_HAS_ACTIVE_LOAN(409, "The copy is on loan and cannot be retired."),
	MEMBER_HAS_HISTORY(409, "The member appears in loans and cannot be deleted."),
	MEMBER_HAS_ACTIVE_LOANS(409, "The member has active loans and cannot be deactivated."),
	CONCURRENT_UPDATE(409, "Another request is updating the same record. Try again."),

	MEMBER_INACTIVE(422, "The member is not active."),
	COPY_INACTIVE(422, "The copy is retired."),
	MEMBER_HAS_OVERDUE_LOAN(422, "The member has an overdue loan."),
	MEMBER_HAS_PENDING_FINE(422, "The member has a pending fine."),
	MEMBER_BLOCKED_BY_SCORE(422, "The member's score is below the minimum to borrow."),
	LOAN_LIMIT_REACHED(422, "The member already has the maximum number of active loans."),
	RENEWAL_LIMIT_REACHED(422, "The loan already used all its renewals."),
	RENEWAL_NOT_ALLOWED_OVERDUE(422, "An overdue loan cannot be renewed. Return it first."),

	INTERNAL_ERROR(500, "An unexpected error occurred.");

	private final int status;
	private final String message;

	ErrorCode(int status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatusCode status() {
		return HttpStatusCode.valueOf(status);
	}

	public String message() {
		return message;
	}

}
