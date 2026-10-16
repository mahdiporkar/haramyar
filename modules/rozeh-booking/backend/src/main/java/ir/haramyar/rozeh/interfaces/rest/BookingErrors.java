package ir.haramyar.rozeh.interfaces.rest;

import io.hiveplatform.spring.HiveException;
import io.hiveplatform.spring.PlatformErrors;
import ir.haramyar.rozeh.application.BookingNotFound;
import ir.haramyar.rozeh.application.port.ConcurrentModification;
import ir.haramyar.rozeh.domain.model.BookingRuleViolation;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Explicit business errors in the Hive PlatformError shape; codes are stable API contract. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class BookingErrors {
  @ExceptionHandler(BookingRuleViolation.class)
  ResponseEntity<Map<String, Object>> rule(BookingRuleViolation violation) {
    HttpStatus status = switch (violation.code()) {
      case INVALID_VALUE, CEREMONY_END_NOT_AFTER_START -> HttpStatus.BAD_REQUEST;
      case ACTOR_NOT_PERMITTED, NOT_BOOKING_OWNER, MOBILE_VERIFICATION_REQUIRED -> HttpStatus.FORBIDDEN;
      case INVALID_TRANSITION, PROVIDER_SCHEDULE_CONFLICT -> HttpStatus.CONFLICT;
      default -> HttpStatus.UNPROCESSABLE_ENTITY;
    };
    return PlatformErrors.body(status, "BOOKING_" + violation.code().name(), violation.getMessage());
  }

  @ExceptionHandler(BookingNotFound.class)
  ResponseEntity<Map<String, Object>> notFound(BookingNotFound missing) {
    return PlatformErrors.body(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", missing.getMessage());
  }

  @ExceptionHandler(ConcurrentModification.class)
  ResponseEntity<Map<String, Object>> stale(ConcurrentModification stale) {
    return PlatformErrors.body(HttpStatus.CONFLICT, "REVISION_CONFLICT", stale.getMessage());
  }

  @ExceptionHandler(HiveException.class)
  ResponseEntity<Map<String, Object>> hive(HiveException error) {
    return PlatformErrors.body(error.status(), error.code(), error.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException invalid) {
    var field = invalid.getBindingResult().getFieldError();
    return PlatformErrors.body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", field == null ? "Invalid request" : field.getField() + " is invalid");
  }
}
