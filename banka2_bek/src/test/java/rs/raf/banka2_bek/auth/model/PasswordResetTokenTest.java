package rs.raf.banka2_bek.auth.model;

import org.junit.jupiter.api.Test;
import rs.raf.banka2_bek.employee.model.Employee;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetTokenTest {

    @Test
    void defaultConstructorCreatesEmptyToken() {
        PasswordResetToken token = new PasswordResetToken();
        assertThat(token.getId()).isNull();
        assertThat(token.getToken()).isNull();
        assertThat(token.getUser()).isNull();
        assertThat(token.getEmployee()).isNull();
        assertThat(token.getExpiresAt()).isNull();
        assertThat(token.getUsed()).isFalse();
    }

    @Test
    void userConstructorSetsAllFields() {
        User user = new User();
        LocalDateTime expires = LocalDateTime.now().plusHours(1);

        PasswordResetToken token = new PasswordResetToken("abc123", user, expires, false);

        assertThat(token.getToken()).isEqualTo("abc123");
        assertThat(token.getUser()).isSameAs(user);
        assertThat(token.getExpiresAt()).isEqualTo(expires);
        assertThat(token.getUsed()).isFalse();
        assertThat(token.getEmployee()).isNull();
    }

    @Test
    void employeeConstructorSetsAllFields() {
        Employee employee = Employee.builder().id(5L).build();
        LocalDateTime expires = LocalDateTime.now().plusHours(2);

        PasswordResetToken token = new PasswordResetToken("emp-token", employee, expires, true);

        assertThat(token.getToken()).isEqualTo("emp-token");
        assertThat(token.getEmployee()).isSameAs(employee);
        assertThat(token.getExpiresAt()).isEqualTo(expires);
        assertThat(token.getUsed()).isTrue();
        assertThat(token.getUser()).isNull();
    }

    @Test
    void settersUpdateAllFields() {
        PasswordResetToken token = new PasswordResetToken();
        User user = new User();
        Employee employee = Employee.builder().id(1L).build();
        LocalDateTime expires = LocalDateTime.of(2026, 4, 13, 12, 0);

        token.setId(42L);
        token.setToken("tok");
        token.setUser(user);
        token.setEmployee(employee);
        token.setExpiresAt(expires);
        token.setUsed(true);

        assertThat(token.getId()).isEqualTo(42L);
        assertThat(token.getToken()).isEqualTo("tok");
        assertThat(token.getUser()).isSameAs(user);
        assertThat(token.getEmployee()).isSameAs(employee);
        assertThat(token.getExpiresAt()).isEqualTo(expires);
        assertThat(token.getUsed()).isTrue();
    }
}
