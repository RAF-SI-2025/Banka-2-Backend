package rs.raf.banka2_bek.interbank.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import rs.raf.banka2_bek.interbank.controller.InterbankInboundController;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice(assignableTypes = InterbankInboundController.class)
public class InterbankExceptionHandler {


    @ExceptionHandler(InterbankExceptions.InterbankAuthException.class)
    public ResponseEntity<String> handleAuth(InterbankExceptions.InterbankAuthException e) {
        return ResponseEntity.status(401).build();
    }

    @ExceptionHandler(InterbankExceptions.InterbankProtocolException.class)
    public ResponseEntity<String> handleProtocol(InterbankExceptions.InterbankProtocolException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<String> handleJson(JsonProcessingException e) {
        return ResponseEntity.badRequest().body("Malformed envelope: " + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Void> handleGeneral(Exception e) {
        log.error("Unexpected error processing inbound message", e);
        return ResponseEntity.internalServerError().build();
    }
}
