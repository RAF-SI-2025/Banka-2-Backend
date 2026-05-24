package rs.raf.banka2_bek.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public class RegisterRequestDto {

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 32, message = "Password must be between 8 and 32 characters")
    @Pattern(
            regexp = "^(?=(?:.*\\d){2,})(?=.*[a-z])(?=.*[A-Z]).{8,32}$",
            message = "Password must contain at least 2 digits, 1 uppercase and 1 lowercase letter"
    )
    private String password;

    @Size(max = 50)
    private String username;

    @Size(max = 20)
    @Pattern(
            regexp = "^\\+?[0-9]{6,20}$",
            message = "Telefon sme da sadrzi samo cifre uz opcioni vodeci znak +"
    )
    private String phone;

    @Size(max = 255)
    private String address;

    private Long dateOfBirth;

    @Size(max = 10)
    private String gender;

    public RegisterRequestDto() {
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Long getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(Long dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    @AssertTrue(message = "Datum rodjenja ne sme biti u buducnosti")
    public boolean isDateOfBirthValid() {
        return dateOfBirth == null
                || dateOfBirth <= java.time.Instant.now().toEpochMilli();
    }
}