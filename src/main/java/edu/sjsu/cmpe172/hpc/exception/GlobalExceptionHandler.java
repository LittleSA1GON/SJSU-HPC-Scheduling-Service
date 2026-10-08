package edu.sjsu.cmpe172.hpc.exception;
import edu.sjsu.cmpe172.hpc.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.servlet.resource.NoResourceFoundException;
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> applicationError(ApiException ex, HttpServletRequest req) {
        return error(ex.status(), ex.getMessage(), req);
    }
    @ExceptionHandler({BindException.class, ConstraintViolationException.class,
                       HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
                       ServletRequestBindingException.class})
    ResponseEntity<ApiError> invalidInput(Exception ex, HttpServletRequest req) {
        String message = "Invalid request. Check the required fields and date formats.";
        if (ex instanceof BindException validation && validation.getFieldError() != null) {
            message = validation.getFieldError().getField() + ": " + validation.getFieldError().getDefaultMessage();
        }
        return error(HttpStatus.BAD_REQUEST, message, req);
    }
    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, NoResourceFoundException.class,
                       HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ApiError> routingError(Exception ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(((ErrorResponse) ex).getStatusCode().value());
        return error(status, status.getReasonPhrase(), req);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpectedError(Exception ex, HttpServletRequest req) {
        log.error("Request failed: {}", req.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to process this request. Please try again.", req);
    }
    private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest req) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(),
            status.name(), message, req.getRequestURI()));
    }
}
