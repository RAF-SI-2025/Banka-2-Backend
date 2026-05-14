package rs.raf.banka2_bek.auth.exception;

/**
 * Bacaja se kad authentikacija ne uspe (pogresan email, pogresna lozinka,
 * neaktivan nalog). Mapira se na HTTP 401 Unauthorized u {@code GlobalExceptionHandler}.
 *
 * Spec Celina 1, Sc 2/3/5/14: login pokusaji sa nevalidnim kredencijalima
 * ili na deaktiviranom nalogu treba da vracaju autentifikacionu gresku, ne
 * generic 400 Bad Request (Bug T1-001/002/005/012 prijavljen 12.05.2026).
 *
 * Sec note: poruka treba da bude GENERIC ("Neispravni unos") da ne otkriva
 * da li email postoji u sistemu. Ovo se primenjuje za pogresnu lozinku +
 * nepostojeci email. Za deaktiviran nalog ostavljamo informativnu poruku
 * jer korisnik ionako mora da kontaktira admin-a za reaktivaciju.
 */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}
