package rs.raf.banka2_bek.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class PasswordResetDto {


    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "New password cannot be blank")
    private String newPassword;

    public PasswordResetDto() {
    }

    public PasswordResetDto(String token, String newPassword) {
        this.token = token;
        this.newPassword = newPassword;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
    public String getNewPassword() {
        return newPassword;
    }
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
