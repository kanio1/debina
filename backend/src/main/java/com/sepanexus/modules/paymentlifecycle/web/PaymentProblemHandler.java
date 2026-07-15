package com.sepanexus.modules.paymentlifecycle.web;

import com.sepanexus.modules.paymentlifecycle.ingress.SignatureVerificationFailedException;
import com.sepanexus.modules.paymentlifecycle.ingress.XmlHardeningRejectedException;
import com.sepanexus.modules.paymentlifecycle.isoadapter.CanonicalMappingException;
import com.sepanexus.modules.paymentlifecycle.isoadapter.MissingPrimaryIdentifierException;
import com.sepanexus.modules.paymentlifecycle.service.IdempotencyConflictException;
import com.sepanexus.modules.paymentlifecycle.service.PaymentNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PaymentProblemHandler {

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyConflictException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail notFound(PaymentNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ProblemDetail invalidRequest(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid payment submission", request);
    }

    /** A data-integrity violation (EPIC-21 Story 21.2), never a normal client-facing outcome — no
     * table name or internal identifier detail in the response, only a correlation ID to trace it. */
    @ExceptionHandler(MissingPrimaryIdentifierException.class)
    ProblemDetail missingPrimaryIdentifier(MissingPrimaryIdentifierException exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Payment data integrity error", request);
    }

    @ExceptionHandler(SignatureVerificationFailedException.class)
    ProblemDetail signatureFailed(SignatureVerificationFailedException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "pain.001 submission rejected: signature "
                + "verification failed", request);
        problem.setProperty("errorCode", "SIGNATURE_FAILED");
        problem.setProperty("reasonCode", exception.reasonCode());
        return problem;
    }

    @ExceptionHandler(XmlHardeningRejectedException.class)
    ProblemDetail xmlHardeningRejected(XmlHardeningRejectedException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "pain.001 submission rejected: malformed "
                + "or unsafe XML", request);
        problem.setProperty("errorCode", XmlHardeningRejectedException.ERROR_CODE);
        return problem;
    }

    @ExceptionHandler(CanonicalMappingException.class)
    ProblemDetail canonicalMappingFailed(CanonicalMappingException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(HttpStatus.UNPROCESSABLE_ENTITY, "pain.001 submission rejected: "
                + exception.error().code(), request);
        problem.setProperty("errorCode", exception.error().code().name());
        problem.setProperty("fieldPath", exception.error().fieldPath());
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("correlationId", request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
        return problem;
    }
}
