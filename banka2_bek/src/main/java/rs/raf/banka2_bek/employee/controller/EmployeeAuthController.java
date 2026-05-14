package rs.raf.banka2_bek.employee.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.raf.banka2_bek.employee.dto.ActivateAccountRequestDto;
import rs.raf.banka2_bek.employee.dto.ActivationTokenStatusDto;
import rs.raf.banka2_bek.employee.service.EmployeeAuthService;

import java.util.Map;

@Tag(name = "Auth", description = "Authentication and account activation API")
@RestController
@RequestMapping("/auth-employee")
@RequiredArgsConstructor
public class EmployeeAuthController {

    private final EmployeeAuthService employeeAuthService;

    @Operation(summary = "Activate account", description = "Activates an employee account using the token sent by email and sets the user's password. Token is single-use and time-limited (24h).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account activated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid, expired or already used token")
    })
    @PostMapping("/activate")
    public ResponseEntity<Map<String, String>> activate(@Valid @RequestBody ActivateAccountRequestDto request) {
        employeeAuthService.activateAccount(request.getToken(), request.getPassword());
        return ResponseEntity.ok(Map.of("message", "Account activated successfully"));
    }

    /**
     * Spec Sc 9 + ad-hoc bag 12.05.2026: FE pre-check stanja tokena pre
     * renderovanja forme za aktivaciju. Endpoint je javan (bez auth-a) —
     * tokens su sami po sebi tajni (~UUID-4, 128-bit entropy).
     */
    @Operation(summary = "Activation token status",
            description = "Returns the current status of an activation token (VALID/USED/EXPIRED/INVALID/ALREADY_ACTIVE) without consuming it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status returned")
    })
    @GetMapping("/activation-token/{token}/status")
    public ResponseEntity<ActivationTokenStatusDto> tokenStatus(@PathVariable("token") String token) {
        return ResponseEntity.ok(employeeAuthService.getTokenStatus(token));
    }
}
