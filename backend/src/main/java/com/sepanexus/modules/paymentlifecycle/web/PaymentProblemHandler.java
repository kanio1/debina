package com.sepanexus.modules.paymentlifecycle.web;

import com.sepanexus.modules.paymentlifecycle.service.DuplicatePaymentException;
import com.sepanexus.modules.paymentlifecycle.service.IdempotencyConflictException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PaymentProblemHandler {

    @ExceptionHandler(DuplicatePaymentException.class)
    ProblemDetail duplicate(DuplicatePaymentException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyConflictException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ProblemDetail invalidRequest(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid payment submission", request);
    }

    private ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("correlationId", request.getAttribute(CorrelationIdFilter.ATTRIBUTE));
        return problem;
    }
}
