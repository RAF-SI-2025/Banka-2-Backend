package rs.raf.banka2_bek.employee.service;

import rs.raf.banka2_bek.employee.dto.ActivationTokenStatusDto;

/**
 * Service for authentication and account activation.
 */
public interface EmployeeAuthService {

    /**
     * Activates an employee account using the one-time token sent by email.
     * Validates token (exists, not used, not invalidated, not expired), then sets the employee as active,
     * updates the password with the new one provided by the user, and marks the token as used and invalidated.
     *
     * @param tokenValue the activation token string from the activation link/request
     * @param newPassword the password chosen by the employee during activation
     * @throws IllegalArgumentException if token is invalid, already used, invalidated, or expired
     * @throws IllegalStateException    if the account is already active
     */
    void activateAccount(String tokenValue, String newPassword);

    /**
     * Vraca stanje aktivacionog tokena. FE poziva ovo na mount-u
     * {@code ActivateAccountPage} da bi sprecio renderovanje forme kad
     * je token vec iskoriscen ili istekao (Bug Scenario 9 Tim 1).
     *
     * Nikad ne baca exception — uvek vrati DTO sa statusom (VALID, USED,
     * EXPIRED, INVALID, ALREADY_ACTIVE). Endpoint je javan (bez auth-a)
     * jer ga zove neaktivirani zaposleni preko email linka.
     */
    ActivationTokenStatusDto getTokenStatus(String tokenValue);
}
