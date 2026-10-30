package com.playbit.exception;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException ex) { return ResponseEntity.status(ex.status).body(Map.of("message", ex.getMessage())); }
    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class}) ResponseEntity<?> bad(Exception ex) { return ResponseEntity.badRequest().body(Map.of("message", "Invalid request. Check the supplied fields.")); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> conflict(Exception ex) { return ResponseEntity.status(409).body(Map.of("message", "This value or action already exists. Refresh and try again.")); }
}
