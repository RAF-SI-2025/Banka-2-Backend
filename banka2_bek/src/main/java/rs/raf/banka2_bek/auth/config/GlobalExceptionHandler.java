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

    /**
     * Spring AOP wrap-uje sve nehvtacene RuntimeException-e unutar @Transactional
     * metoda u TransactionSystemException sa generic porukom "Could not commit JPA
     * transaction". Pravu poruku korisnika dobijemo unwrap-ovanjem cause lanca.
     *
     * Bez ovog handler-a, korisnik vidi cryptic "Could not commit JPA transaction"
     * umesto stvarne poruke (npr. "Racun ne pripada klijentu", "Nedovoljno sredstava").
     * Prijavljeno 14.05.2026 vece-4: Ana fund invest + OTC accept padaju sa generic
     * porukom umesto pravih BE validation poruka.
     */
    @ExceptionHandler(org.springframework.transaction.TransactionSystemException.class)
    public ResponseEntity<MessageResponseDto> handleTransactionSystemException(
            org.springframework.transaction.TransactionSystemException ex) {
        Throwable cause = ex.getMostSpecificCause();
        // Unwrap chain dok ne nadjemo non-Spring exception
        while (cause != null
                && cause.getCause() != null
                && cause.getCause() != cause
                && (cause instanceof org.springframework.transaction.TransactionException
                    || cause instanceof org.springframework.dao.DataAccessException)) {
            cause = cause.getCause();
        }
        String message = cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()
                ? cause.getMessage()
                : "Doslo je do greske prilikom obrade zahteva.";
        // Mapiraj poznate exception tipove u prikladan HTTP status
        HttpStatus status = HttpStatus.BAD_REQUEST;
        if (cause instanceof EntityNotFoundException) {
            status = HttpStatus.NOT_FOUND;
        } else if (cause instanceof AccessDeniedException) {
            status = HttpStatus.FORBIDDEN;
        } else if (cause instanceof AuthenticationFailedException
                || cause instanceof AccountLockoutService.AccountLockedException) {
            status = HttpStatus.UNAUTHORIZED;
        }
        return ResponseEntity.status(status).body(new MessageResponseDto(message));
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
