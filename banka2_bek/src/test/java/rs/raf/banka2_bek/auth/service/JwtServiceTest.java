package rs.raf.banka2_bek.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.employee.model.Employee;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "my-super-secret-key-for-jwt-token-generation-123456";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET);
    }

    // ============================================================
    // Helper: parse claims from a token
    // ============================================================

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ============================================================
    // generateAccessToken(User) tests
    // ============================================================

    @Test
    void generateAccessToken_user_containsEmail() {
        User user = createUser("marko@banka.rs", "CLIENT", true);
        String token = jwtService.generateAccessToken(user);

        assertThat(parseClaims(token).getSubject()).isEqualTo("marko@banka.rs");
    }

    @Test
    void generateAccessToken_user_containsRole() {
        User user = createUser("marko@banka.rs", "CLIENT", true);
        String token = jwtService.generateAccessToken(user);

        assertThat(parseClaims(token).get("role", String.class)).isEqualTo("CLIENT");
    }

    @Test
    void generateAccessToken_user_containsActiveClaim() {
        User user = createUser("marko@banka.rs", "CLIENT", true);
        String token = jwtService.generateAccessToken(user);

        assertThat(parseClaims(token).get("active", Boolean.class)).isTrue();
    }

    @Test
    void generateAccessToken_user_inactiveUser() {
        User user = createUser("marko@banka.rs", "CLIENT", false);
        String token = jwtService.generateAccessToken(user);

        assertThat(parseClaims(token).get("active", Boolean.class)).isFalse();
    }

    @Test
    void generateAccessToken_user_expiresIn15Minutes() {
        User user = createUser("marko@banka.rs", "CLIENT", true);
        long before = System.currentTimeMillis();
        String token = jwtService.generateAccessToken(user);
        long after = System.currentTimeMillis();

        Date expiration = parseClaims(token).getExpiration();
        long fifteenMinMs = 1000 * 60 * 15;

        // Expiration should be roughly 15 minutes from now
        assertThat(expiration.getTime()).isBetween(before + fifteenMinMs - 1000, after + fifteenMinMs + 1000);
    }

    @Test
    void generateAccessToken_user_doesNotContainRefreshType() {
        User user = createUser("marko@banka.rs", "CLIENT", true);
        String token = jwtService.generateAccessToken(user);

        assertThat(parseClaims(token).get("type", String.class)).isNull();
    }

    // ============================================================
    // generateAccessToken(Employee) tests
    // ============================================================

    @Test
    void generateAccessToken_employee_containsEmail() {
        Employee employee = createEmployee("ana@banka.rs", Set.of("READ", "WRITE"), true);
        String token = jwtService.generateAccessToken(employee);

        assertThat(parseClaims(token).getSubject()).isEqualTo("ana@banka.rs");
    }

    @Test
    void generateAccessToken_employee_roleIsEmployeeWithoutAdminPermission() {
        Employee employee = createEmployee("ana@banka.rs", Set.of("READ", "WRITE"), true);
        String token = jwtService.generateAccessToken(employee);

        assertThat(parseClaims(token).get("role", String.class)).isEqualTo("EMPLOYEE");
    }

    @Test
    void generateAccessToken_employee_roleIsAdminWithAdminPermission() {
        Employee employee = createEmployee("admin@banka.rs", Set.of("ADMIN", "READ"), true);
        String token = jwtService.generateAccessToken(employee);

        assertThat(parseClaims(token).get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void generateAccessToken_employee_nullPermissions_roleIsEmployee() {
        Employee employee = createEmployee("ana@banka.rs", null, true);
        String token = jwtService.generateAccessToken(employee);

        assertThat(parseClaims(token).get("role", String.class)).isEqualTo("EMPLOYEE");
    }

    @Test
    void generateAccessToken_employee_containsActiveClaim() {
        Employee employee = createEmployee("ana@banka.rs", Set.of("READ"), true);
        String token = jwtService.generateAccessToken(employee);

        assertThat(parseClaims(token).get("active", Boolean.class)).isTrue();
    }

    @Test
    void generateAccessToken_employee_inactiveEmployee() {
        Employee employee = createEmployee("ana@banka.rs", Set.of("READ"), false);
        String token = jwtService.generateAccessToken(employee);

        assertThat(parseClaims(token).get("active", Boolean.class)).isFalse();
    }

    @Test
    void generateAccessToken_employee_expiresIn15Minutes() {
        Employee employee = createEmployee("ana@banka.rs", Set.of("READ"), true);
        long before = System.currentTimeMillis();
        String token = jwtService.generateAccessToken(employee);
        long after = System.currentTimeMillis();

        Date expiration = parseClaims(token).getExpiration();
        long fifteenMinMs = 1000 * 60 * 15;

        assertThat(expiration.getTime()).isBetween(before + fifteenMinMs - 1000, after + fifteenMinMs + 1000);
    }

    // ============================================================
    // generateRefreshToken(User) tests
    // ============================================================

    @Test
    void generateRefreshToken_user_containsEmail() {
        User user = createUser("marko@banka.rs", "CLIENT", true);
        String token = jwtService.generateRefreshToken(user);

        assertThat(parseClaims(token).getSubject()).isEqualTo("marko@banka.rs");
    }

    @Test
    void generateRefreshToken_user_hasRefreshType() {
        User user = createUser("marko@banka.rs", "CLIENT", true);
        String token = jwtService.generateRefreshToken(user);

        assertThat(parseClaims(token).get("type", String.class)).isEqualTo("refresh");
    }

    @Test
    void generateRefreshToken_user_expiresIn7Days() {
        User user = createUser("marko@banka.rs", "CLIENT", true);
        long before = System.currentTimeMillis();
        String token = jwtService.generateRefreshToken(user);
        long after = System.currentTimeMillis();

        Date expiration = parseClaims(token).getExpiration();
        long sevenDaysMs = 1000L * 60 * 60 * 24 * 7;

        assertThat(expiration.getTime()).isBetween(before + sevenDaysMs - 1000, after + sevenDaysMs + 1000);
    }

    @Test
    void generateRefreshToken_user_doesNotContainRoleClaim() {
        User user = createUser("marko@banka.rs", "CLIENT", true);
        String token = jwtService.generateRefreshToken(user);

        assertThat(parseClaims(token).get("role", String.class)).isNull();
    }

    @Test
    void generateRefreshToken_user_doesNotContainActiveClaim() {
        User user = createUser("marko@banka.rs", "CLIENT", true);
        String token = jwtService.generateRefreshToken(user);

        assertThat(parseClaims(token).get("active", Boolean.class)).isNull();
    }

    // ============================================================
    // generateRefreshToken(Employee) tests
    // ============================================================

    @Test
    void generateRefreshToken_employee_containsEmail() {
        Employee employee = createEmployee("ana@banka.rs", Set.of("READ"), true);
        String token = jwtService.generateRefreshToken(employee);

        assertThat(parseClaims(token).getSubject()).isEqualTo("ana@banka.rs");
    }

    @Test
    void generateRefreshToken_employee_hasRefreshType() {
        Employee employee = createEmployee("ana@banka.rs", Set.of("READ"), true);
        String token = jwtService.generateRefreshToken(employee);

        assertThat(parseClaims(token).get("type", String.class)).isEqualTo("refresh");
    }

    @Test
    void generateRefreshToken_employee_expiresIn7Days() {
        Employee employee = createEmployee("ana@banka.rs", Set.of("READ"), true);
        long before = System.currentTimeMillis();
        String token = jwtService.generateRefreshToken(employee);
        long after = System.currentTimeMillis();

        Date expiration = parseClaims(token).getExpiration();
        long sevenDaysMs = 1000L * 60 * 60 * 24 * 7;

        assertThat(expiration.getTime()).isBetween(before + sevenDaysMs - 1000, after + sevenDaysMs + 1000);
    }

    // ============================================================
    // isRefreshToken() tests
    // ============================================================

    @Test
    void isRefreshToken_withRefreshTokenFromUser_returnsTrue() {
        User user = createUser("marko@banka.rs", "CLIENT", true);
        String token = jwtService.generateRefreshToken(user);

        assertThat(jwtService.isRefreshToken(token)).isTrue();
    }

    @Test
    void isRefreshToken_withRefreshTokenFromEmployee_returnsTrue() {
        Employee employee = createEmployee("ana@banka.rs", Set.of("READ"), true);
        String token = jwtService.generateRefreshToken(employee);

        assertThat(jwtService.isRefreshToken(token)).isTrue();
    }

    @Test
    void isRefreshToken_withAccessTokenFromUser_returnsFalse() {
        User user = createUser("marko@banka.rs", "CLIENT", true);
        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.isRefreshToken(token)).isFalse();
    }

    @Test
    void isRefreshToken_withAccessTokenFromEmployee_returnsFalse() {
        Employee employee = createEmployee("ana@banka.rs", Set.of("READ"), true);
        String token = jwtService.generateAccessToken(employee);

        assertThat(jwtService.isRefreshToken(token)).isFalse();
    }

    @Test
    void isRefreshToken_invalidToken_throwsException() {
        assertThatThrownBy(() -> jwtService.isRefreshToken("invalid.token.string"))
                .isInstanceOf(Exception.class);
    }

    // ============================================================
    // extractEmail() tests
    // ============================================================

    @Test
    void extractEmail_fromUserAccessToken() {
        User user = createUser("test@banka.rs", "CLIENT", true);
        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.extractEmail(token)).isEqualTo("test@banka.rs");
    }

    @Test
    void extractEmail_fromEmployeeAccessToken() {
        Employee employee = createEmployee("emp@banka.rs", Set.of("READ"), true);
        String token = jwtService.generateAccessToken(employee);

        assertThat(jwtService.extractEmail(token)).isEqualTo("emp@banka.rs");
    }

    @Test
    void extractEmail_fromRefreshToken() {
        User user = createUser("test@banka.rs", "CLIENT", true);
        String token = jwtService.generateRefreshToken(user);

        assertThat(jwtService.extractEmail(token)).isEqualTo("test@banka.rs");
    }

    @Test
    void extractEmail_invalidToken_throwsException() {
        assertThatThrownBy(() -> jwtService.extractEmail("garbage"))
                .isInstanceOf(Exception.class);
    }

    // ============================================================
    // isTokenValid() tests
    // ============================================================

    @Test
    void isTokenValid_validUserToken_returnsTrue() {
        User user = createUser("marko@banka.rs", "CLIENT", true);
        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.isTokenValid(token, user)).isTrue();
    }

    @Test
    void isTokenValid_wrongUser_returnsFalse() {
        User user1 = createUser("marko@banka.rs", "CLIENT", true);
        User user2 = createUser("other@banka.rs", "CLIENT", true);
        String token = jwtService.generateAccessToken(user1);

        assertThat(jwtService.isTokenValid(token, user2)).isFalse();
    }

    // ============================================================
    // Access vs Refresh token differences
    // ============================================================

    @Test
    void accessToken_hasRoleClaim_refreshToken_doesNot() {
        User user = createUser("marko@banka.rs", "CLIENT", true);

        String access = jwtService.generateAccessToken(user);
        String refresh = jwtService.generateRefreshToken(user);

        assertThat(parseClaims(access).get("role", String.class)).isEqualTo("CLIENT");
        assertThat(parseClaims(refresh).get("role", String.class)).isNull();
    }

    @Test
    void accessToken_hasActiveClaim_refreshToken_doesNot() {
        User user = createUser("marko@banka.rs", "CLIENT", true);

        String access = jwtService.generateAccessToken(user);
        String refresh = jwtService.generateRefreshToken(user);

        assertThat(parseClaims(access).get("active", Boolean.class)).isTrue();
        assertThat(parseClaims(refresh).get("active", Boolean.class)).isNull();
    }

    @Test
    void refreshToken_expiresLaterThanAccessToken() {
        User user = createUser("marko@banka.rs", "CLIENT", true);

        String access = jwtService.generateAccessToken(user);
        String refresh = jwtService.generateRefreshToken(user);

        Date accessExp = parseClaims(access).getExpiration();
        Date refreshExp = parseClaims(refresh).getExpiration();

        assertThat(refreshExp).isAfter(accessExp);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private User createUser(String email, String role, boolean active) {
        User user = new User();
        user.setEmail(email);
        user.setRole(role);
        user.setActive(active);
        user.setPassword("password123");
        user.setFirstName("Test");
        user.setLastName("User");
        return user;
    }

    private Employee createEmployee(String email, Set<String> permissions, boolean active) {
        return Employee.builder()
                .email(email)
                .permissions(permissions)
                .active(active)
                .firstName("Test")
                .lastName("Employee")
                .password("password123")
                .saltPassword("salt")
                .phone("0611234567")
                .address("Test Address 1")
                .username("testuser")
                .position("Developer")
                .department("IT")
                .gender("M")
                .dateOfBirth(java.time.LocalDate.of(1990, 1, 1))
                .build();
    }
}
