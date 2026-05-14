package rs.raf.banka2_bek.interbank.exception;

/*
================================================================================
 EXCEPTION HIJERARHIJA ZA INTER-BANK SLOJ (PROTOKOL §2.9-§2.11)
--------------------------------------------------------------------------------
 InterbankException (base RuntimeException)
   ├─ InterbankCommunicationException  — HTTP/mrezne greske (timeout, 5xx,
   │                                     unknown bank, network failure)
   ├─ InterbankAuthException           — §2.10 los X-Api-Key (401 napred-nazad)
   ├─ InterbankProtocolException       — validacija (nevalidan envelope,
   │                                     unknown messageType, unbalanced tx,
   │                                     malformed JSON)
   ├─ InterbankIdempotencyException    — §2.2 konflikti pri trajnom belezenju
   │                                     idempotence kljuceva
   └─ InterbankTransactionStuckException — retry scheduler odustao posle MAX_RETRY

 HTTP MAPING (u @RestControllerAdvice):
   InterbankCommunicationException     -> 502 Bad Gateway
   InterbankAuthException              -> 401 Unauthorized (outbound problem)
                                       -> 401 takodje za inbound los token
   InterbankProtocolException          -> 400 Bad Request
   InterbankIdempotencyException       -> 500 (retry pokupi)
   InterbankTransactionStuckException  -> 500 (manuelna intervencija)
================================================================================
*/
public final class InterbankExceptions {

    private InterbankExceptions() {
    }

    public static class InterbankException extends RuntimeException {
        public InterbankException(String message) {
            super(message);
        }

        public InterbankException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class InterbankCommunicationException extends InterbankException {
        public InterbankCommunicationException(String message) {
            super(message);
        }

        public InterbankCommunicationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * §2.10 — los ili nedostaje X-Api-Key.
     */
    public static class InterbankAuthException extends InterbankException {
        public InterbankAuthException(String message) {
            super(message);
        }
    }

    public static class InterbankProtocolException extends InterbankException {
        public InterbankProtocolException(String message) {
            super(message);
        }
    }

    /**
     * §3.3 — PUT /negotiations/{rn}/{id} pozvan kad nije turn pozivaoca, ILI je
     * pregovor vec zatvoren. Spec eksplicitno trazi HTTP 409 Conflict (ne 400),
     * jer to nije malformed input — to je validan zahtev koji konflitkuje sa
     * trenutnim stanjem resursa.
     */
    public static class InterbankNegotiationConflictException extends InterbankException {
        public InterbankNegotiationConflictException(String message) {
            super(message);
        }
    }

    /**
     * §3.7 — GET /user/{rn}/{id} za nepostojeci ID. Spec trazi HTTP 404 Not Found
     * (ne 400). Razlikujemo od ostalih protocol greska.
     */
    public static class InterbankUserNotFoundException extends InterbankException {
        public InterbankUserNotFoundException(String message) {
            super(message);
        }
    }

    /**
     * §2.2 — duplikat pri belezenju idempotence kljuca ili stale cached response.
     */
    public static class InterbankIdempotencyException extends InterbankException {
        public InterbankIdempotencyException(String message) {
            super(message);
        }
    }

    public static class InterbankTransactionStuckException extends InterbankException {
        public InterbankTransactionStuckException(String message) {
            super(message);
        }
    }
}
