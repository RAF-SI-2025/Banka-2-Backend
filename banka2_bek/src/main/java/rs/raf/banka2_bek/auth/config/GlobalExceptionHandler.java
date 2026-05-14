package rs.raf.banka2_bek.auth.config;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import rs.raf.banka2_bek.auth.dto.MessageResponseDto;
import rs.raf.banka2_bek.auth.exception.AuthenticationFailedException;
import rs.raf.banka2_bek.auth.service.AccountLockoutService;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MessageResponseDto> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(f -> f.getDefaultMessage())
                .orElse("Validation error");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new MessageResponseDto(message));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<MessageResponseDto> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new MessageResponseDto(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<MessageResponseDto> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new MessageResponseDto(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<MessageResponseDto> handleForbidden(IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new MessageResponseDto(ex.getMessage()));
    }

    /**
     * Spec Celina 1 Sc 2/3/5/14 — autentifikacioni neuspesi vracaju 401, ne
     * generic 400 (Bug T1-001/002/005/012 prijavljen 12.05.2026 manuelnim
     * testiranjem). Mora biti pre handleRuntimeException jer
     * AuthenticationFailedException extends RuntimeException — Spring bira
     * najspecifičniji handler.
     */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<MessageResponseDto> handleAuthenticationFailed(AuthenticationFailedException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new MessageResponseDto(ex.getMessage()));
    }

    /**
     * Account lockout posle 5 neuspeha — takodje mora biti pre RuntimeException
     * handler-a. Vraca 401 (sa SR porukom — vidi {@code AccountLockoutService}).
     */
    @ExceptionHandler(AccountLockoutService.AccountLockedException.class)
    public ResponseEntity<MessageResponseDto> handleAccountLocked(AccountLockoutService.AccountLockedException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(new MessageResponseDto(ex.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<MessageResponseDto> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new MessageResponseDto(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<MessageResponseDto> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(new MessageResponseDto(ex.getMessage()));
    }
}
