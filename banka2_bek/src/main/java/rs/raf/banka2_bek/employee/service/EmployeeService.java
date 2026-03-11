package rs.raf.banka2_bek.employee.service;

import org.springframework.data.domain.Page;
import rs.raf.banka2_bek.employee.dto.CreateEmployeeRequestDto;
import rs.raf.banka2_bek.employee.dto.EmployeeResponseDto;
import rs.raf.banka2_bek.employee.dto.UpdateEmployeeRequestDto;

/**
 * Service for employee CRUD and lifecycle operations.
 */
public interface EmployeeService {

    /**
     * Creates a new employee with a temporary password and activation token.
     * Sends an activation email (via event). Account remains inactive until activation.
     *
     * @param request creation data (personal info, email, username, position, etc.)
     * @return created employee DTO including the activation token (for testing/admin use)
     * @throws IllegalArgumentException if email or username already exists
     */
    EmployeeResponseDto createEmployee(CreateEmployeeRequestDto request);

    /**
     * Returns a paginated list of employees, optionally filtered by email, first name, last name, and position.
     *
     * @param page     zero-based page index
     * @param limit    page size
     * @param email    optional filter by email (partial match)
     * @param firstName optional filter by first name
     * @param lastName  optional filter by last name
     * @param position  optional filter by position
     * @return page of employee response DTOs
     */
    Page<EmployeeResponseDto> getEmployees(int page, int limit, String email,
                                           String firstName, String lastName, String position);

    /**
     * Updates an existing employee by ID. Only non-null fields in the request are applied.
     *
     * @param id      employee ID
     * @param request fields to update (all optional)
     * @return updated employee DTO
     * @throws IllegalArgumentException if employee not found
     */
    EmployeeResponseDto updateEmployee(Long id, UpdateEmployeeRequestDto request);

    /**
     * Deactivates the employee account (sets active to false). No-op if already deactivated.
     *
     * @param id employee ID
     * @throws IllegalArgumentException if employee not found
     * @throws IllegalStateException    if account is already deactivated
     */
    void deactivateEmployee(Long id);
}
