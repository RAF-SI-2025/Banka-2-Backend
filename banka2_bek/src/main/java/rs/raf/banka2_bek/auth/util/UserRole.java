package rs.raf.banka2_bek.auth.util;

/**
 * String konstante za uloge koje sistem koristi u tri razlicita sloja:
 * <ul>
 *   <li>{@code JWT.role} claim — "ADMIN" / "EMPLOYEE" / "CLIENT"</li>
 *   <li>{@link Order#userRole} / {@link OtcOffer#buyerRole} i slicno — "EMPLOYEE" / "CLIENT"</li>
 *   <li>Spring Security {@code ROLE_ADMIN} / {@code ROLE_EMPLOYEE} — prefix verzija</li>
 * </ul>
 *
 * Ovaj util zeli da eliminise 25+ mest u kodu gde su ova imena hardkodovana
 * kao string literali i da dobijemo kompajlersku check za tipograske greske.
 * Naglasak nije na true enum-u (zbog kompatibilnosti sa JWT claim-om i JPA
 * {@code String} kolonama), vec na imenovanim konstantama.
 */
public final class UserRole {

    private UserRole() {}

    /** JWT role + Order.userRole za zaposlene. */
    public static final String EMPLOYEE = "EMPLOYEE";

    /** JWT role + Order.userRole za klijente. */
    public static final String CLIENT = "CLIENT";

    /** JWT role za administratora. */
    public static final String ADMIN = "ADMIN";

    /** Permisija ime (Employee.permissions) za supervizora. */
    public static final String SUPERVISOR = "SUPERVISOR";

    /** Permisija ime (Employee.permissions) za agenta. */
    public static final String AGENT = "AGENT";

    /** Spring Security authority prefix za ADMIN rolu. */
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    /** Spring Security authority prefix za EMPLOYEE rolu. */
    public static final String ROLE_EMPLOYEE = "ROLE_EMPLOYEE";

    /** Spring Security authority prefix za CLIENT rolu. */
    public static final String ROLE_CLIENT = "ROLE_CLIENT";

    /** Spring Security authority prefix za FUND rolu. */
    public static final String FUND = "FUND";

    public static boolean isEmployee(String role) {
        return EMPLOYEE.equals(role);
    }

    public static boolean isClient(String role) {
        return CLIENT.equals(role);
    }

    public static boolean isAdmin(String role) {
        return ADMIN.equals(role);
    }
}
