package rs.raf.banka2_bek.employee.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rs.raf.banka2_bek.employee.dto.ActivateAccountRequestDto;
import rs.raf.banka2_bek.employee.service.EmployeeAuthService;

import java.util.Map;

@Tag(name = "Auth", description = "Authentication and account activation API")
@RestController
@RequestMapping("/auth-employee")
@RequiredArgsConstructor
public class EmployeeAuthController {

    private final EmployeeAuthService employeeAuthService;

    @Operation(summary = "Activate account", description = "Activates an employee account using the token sent by email. Token is single-use and time-limited (24h).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account activated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid, expired or already used token")
    })
    @PostMapping("/activate")
    public ResponseEntity<Map<String, String>> activate(@Valid @RequestBody ActivateAccountRequestDto request) {
        employeeAuthService.activateAccount(request.getToken());
        return ResponseEntity.ok(Map.of("message", "Account activated successfully"));
    }
}
