package ir.haramyar.martyrs.interfaces.rest;

import io.hiveplatform.spring.HiveException;
import io.hiveplatform.spring.PlatformErrors;
import ir.haramyar.martyrs.application.Ports;
import ir.haramyar.martyrs.domain.EditorialRuleViolation;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class EditorialErrors {
  @ExceptionHandler(EditorialRuleViolation.class)
  ResponseEntity<Map<String, Object>> rule(EditorialRuleViolation violation) {
    HttpStatus status = switch (violation.code()) {
      case INVALID_VALUE -> HttpStatus.BAD_REQUEST;
      case ACTOR_NOT_PERMITTED, FOUR_EYES_REQUIRED -> HttpStatus.FORBIDDEN;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case INVALID_TRANSITION, NOT_EDITABLE -> HttpStatus.CONFLICT;
      case LIMIT_EXCEEDED, NOT_READY_FOR_REVIEW -> HttpStatus.UNPROCESSABLE_ENTITY;
    };
    return PlatformErrors.body(status, "EDITORIAL_" + violation.code().name(), violation.getMessage());
  }

  @ExceptionHandler(Ports.ConcurrentModification.class)
  ResponseEntity<Map<String, Object>> stale(Ports.ConcurrentModification stale) {
    return PlatformErrors.body(HttpStatus.CONFLICT, "REVISION_CONFLICT", stale.getMessage());
  }

  @ExceptionHandler(HiveException.class)
  ResponseEntity<Map<String, Object>> hive(HiveException error) {
    return PlatformErrors.body(error.status(), error.code(), error.getMessage());
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException error) {
    return PlatformErrors.body(HttpStatus.PAYLOAD_TOO_LARGE, "EDITORIAL_LIMIT_EXCEEDED", "Images may not exceed 5 MiB");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException invalid) {
    var field = invalid.getBindingResult().getFieldError();
    return PlatformErrors.body(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", field == null ? "Invalid request" : field.getField() + " is invalid");
  }
}
