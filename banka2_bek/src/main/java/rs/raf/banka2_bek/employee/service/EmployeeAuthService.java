package rs.raf.banka2_bek.employee.service;

/**
 * Service for authentication and account activation.
 */
public interface EmployeeAuthService {

    /**
     * Activates an employee account using the one-time token sent by email.
     * Validates token (exists, not used, not invalidated, not expired), then sets the employee as active
     * and marks the token as used and invalidated.
     *
     * @param tokenValue the activation token string from the activation link/request
     * @throws IllegalArgumentException if token is invalid, already used, invalidated, or expired
     * @throws IllegalStateException    if the account is already active
     */
    void activateAccount(String tokenValue);
}
