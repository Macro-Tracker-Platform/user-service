package com.olehprukhnytskyi.macrotrackeruserservice.exception;

import com.olehprukhnytskyi.dto.ProblemDetails;
import io.opentelemetry.api.trace.Span;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpStatusExceptionHandler {
    // The common library's generic advice would otherwise turn these into HTTP 500.
    @ExceptionHandler({ResponseStatusException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ProblemDetails> handleHttpStatus(Exception exception) {
        ErrorResponse error = (ErrorResponse) exception;
        var problem = error.getBody();
        String code = "HTTP_" + error.getStatusCode().value();
        if (exception instanceof ResponseStatusException statusException
                && statusException.getReason() != null
                && statusException.getReason().matches("[A-Z][A-Z0-9_]+")) {
            code = statusException.getReason();
        }
        var trace = Span.current().getSpanContext();
        var body = ProblemDetails.builder()
                .title(problem.getTitle())
                .status(error.getStatusCode().value())
                .detail(problem.getDetail())
                .code(code)
                .traceId(trace.isValid() ? trace.getTraceId() : "N/A")
                .build();
        return ResponseEntity.status(error.getStatusCode())
                .headers(error.getHeaders())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
