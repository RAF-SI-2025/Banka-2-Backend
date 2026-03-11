package rs.raf.banka2_bek.employee.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import rs.raf.banka2_bek.employee.dto.*;
import rs.raf.banka2_bek.employee.service.EmployeeService;

@Tag(name = "Employee", description = "Employee management API")
@RestController
@RequestMapping("/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @Operation(summary = "Create employee", description = "Creates a new employee. Sends activation email with token. Account is inactive until activated.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Employee created", content = @Content(schema = @Schema(implementation = EmployeeResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation error or duplicate email/username")
    })
    @PostMapping
    public ResponseEntity<EmployeeResponseDto> createEmployee(@Valid @RequestBody CreateEmployeeRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(employeeService.createEmployee(request));
    }

    @Operation(summary = "Get employees (paginated)", description = "Returns a paginated list of employees with optional filters: email, firstName, lastName, position.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of employees (content: array of EmployeeResponseDto, totalElements, totalPages, etc.)")
    })
    @GetMapping
    public ResponseEntity<Page<EmployeeResponseDto>> getEmployees(
            @Parameter(description = "Page index (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "Filter by email") @RequestParam(required = false) String email,
            @Parameter(description = "Filter by first name") @RequestParam(required = false) String firstName,
            @Parameter(description = "Filter by last name") @RequestParam(required = false) String lastName,
            @Parameter(description = "Filter by position") @RequestParam(required = false) String position) {
        return ResponseEntity.ok(employeeService.getEmployees(page, limit, email, firstName, lastName, position));
    }

    @Operation(summary = "Update employee", description = "Updates an existing employee by ID. Only provided fields are updated.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated employee", content = @Content(schema = @Schema(implementation = EmployeeResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponseDto> updateEmployee(
            @Parameter(description = "Employee ID") @PathVariable Long id,
            @Valid @RequestBody UpdateEmployeeRequestDto request) {
        return ResponseEntity.ok(employeeService.updateEmployee(id, request));
    }

    @Operation(summary = "Deactivate employee", description = "Sets employee account to inactive. Idempotent if already deactivated.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Employee deactivated"),
            @ApiResponse(responseCode = "400", description = "Already deactivated"),
            @ApiResponse(responseCode = "404", description = "Employee not found")
    })
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateEmployee(
            @Parameter(description = "Employee ID") @PathVariable Long id) {
        employeeService.deactivateEmployee(id);
        return ResponseEntity.ok().build();
    }
}
