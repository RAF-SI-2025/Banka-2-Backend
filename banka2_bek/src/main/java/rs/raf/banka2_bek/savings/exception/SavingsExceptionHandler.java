package rs.raf.banka2_bek.savings.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice(basePackages = "rs.raf.banka2_bek.savings.controller")
public class SavingsExceptionHandler {

    @ExceptionHandler(SavingsDepositNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(SavingsDepositNotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(500).body(Map.of("message", ex.getMessage()));
    }
}
