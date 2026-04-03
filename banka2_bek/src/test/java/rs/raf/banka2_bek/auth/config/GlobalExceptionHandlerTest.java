package rs.raf.banka2_bek.auth.config;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import rs.raf.banka2_bek.auth.dto.MessageResponseDto;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ── MethodArgumentNotValidException → 400 ───────────────────────

    @Test
    void handleValidationException_returnsBadRequest() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "email", "Email is required"));

        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("setUp"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<MessageResponseDto> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Email is required");
    }

    @Test
    void handleValidationException_noFieldErrors_returnsDefaultMessage() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");

        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("setUp"), -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<MessageResponseDto> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Validation error");
    }

    // ── EntityNotFoundException → 404 ───────────────────────────────

    @Test
    void handleEntityNotFound_returnsNotFound() {
        EntityNotFoundException ex = new EntityNotFoundException("Employee not found");

        ResponseEntity<MessageResponseDto> response = handler.handleEntityNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Employee not found");
    }

    // ── IllegalArgumentException → 400 ──────────────────────────────

    @Test
    void handleBadRequest_returnsBadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid input");

        ResponseEntity<MessageResponseDto> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Invalid input");
    }

    // ── IllegalStateException → 403 ─────────────────────────────────

    @Test
    void handleForbidden_returnsForbidden() {
        IllegalStateException ex = new IllegalStateException("Account is deactivated");

        ResponseEntity<MessageResponseDto> response = handler.handleForbidden(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Account is deactivated");
    }

    // ── RuntimeException → 400 ──────────────────────────────────────

    @Test
    void handleRuntimeException_returnsBadRequest() {
        RuntimeException ex = new RuntimeException("Something went wrong");

        ResponseEntity<MessageResponseDto> response = handler.handleRuntimeException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Something went wrong");
    }

    // ── AccessDeniedException → 403 ─────────────────────────────────

    @Test
    void handleAccessDenied_returnsForbidden() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ResponseEntity<MessageResponseDto> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Access denied");
    }
}
