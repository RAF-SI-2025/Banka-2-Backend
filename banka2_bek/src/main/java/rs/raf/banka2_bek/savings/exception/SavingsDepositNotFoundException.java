package rs.raf.banka2_bek.savings.exception;

/**
 * Bacaj kad korisnik trazi stedni depozit koji ne postoji (po id-u). Mapira se
 * u HTTP 404 Not Found preko {@link SavingsExceptionHandler}.
 *
 * Razdvojen od {@link IllegalArgumentException} (koji ide u 400 Bad Request) da
 * bi FE mogao da razlikuje "ne postoji" od "los input".
 */
public class SavingsDepositNotFoundException extends RuntimeException {

    public SavingsDepositNotFoundException(Long depositId) {
        super("Depozit ne postoji: id=" + depositId);
    }

    public SavingsDepositNotFoundException(String message) {
        super(message);
    }
}
