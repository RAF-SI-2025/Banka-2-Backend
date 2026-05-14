package rs.raf.banka2_bek.interbank.controller.exception_handler;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import rs.raf.banka2_bek.interbank.controller.OtcNegotiationController;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;

import java.util.Map;

@RestControllerAdvice(assignableTypes = OtcNegotiationController.class)
public class OtcNegotiationExceptionHandler {

    @ExceptionHandler(InterbankExceptions.InterbankCommunicationException.class)
    public ResponseEntity<Map<String, String>> handleCommunication(InterbankExceptions.InterbankCommunicationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InterbankExceptions.InterbankAuthException.class)
    public ResponseEntity<Map<String, String>> handleAuth(InterbankExceptions.InterbankAuthException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InterbankExceptions.InterbankProtocolException.class)
    public ResponseEntity<Map<String, String>> handleProtocol(InterbankExceptions.InterbankProtocolException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    /** §3.3 — turn violation ili zatvoreni pregovor -> 409 Conflict. */
    @ExceptionHandler(InterbankExceptions.InterbankNegotiationConflictException.class)
    public ResponseEntity<Map<String, String>> handleNegotiationConflict(
            InterbankExceptions.InterbankNegotiationConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    /** §3.7 — nepostojeci user ID -> 404 Not Found. */
    @ExceptionHandler(InterbankExceptions.InterbankUserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(
            InterbankExceptions.InterbankUserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InterbankExceptions.InterbankIdempotencyException.class)
    public ResponseEntity<Map<String, String>> handleIdempotency(InterbankExceptions.InterbankIdempotencyException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InterbankExceptions.InterbankTransactionStuckException.class)
    public ResponseEntity<Map<String, String>> handleStuck(InterbankExceptions.InterbankTransactionStuckException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(InterbankExceptions.InterbankException.class)
    public ResponseEntity<Map<String, String>> handleGenericInterbank(InterbankExceptions.InterbankException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }
}