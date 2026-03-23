package rs.raf.banka2_bek.actuary.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActuaryInfoRepository extends JpaRepository<ActuaryInfo, Long> {

    Optional<ActuaryInfo> findByEmployeeId(Long employeeId);

    List<ActuaryInfo> findAllByActuaryType(ActuaryType actuaryType);

    @Query("SELECT a FROM ActuaryInfo a WHERE a.actuaryType = :type " +
            "AND (:email IS NULL OR LOWER(a.employee.email) LIKE LOWER(CONCAT('%', :email, '%'))) " +
            "AND (:firstName IS NULL OR LOWER(a.employee.firstName) LIKE LOWER(CONCAT('%', :firstName, '%'))) " +
            "AND (:lastName IS NULL OR LOWER(a.employee.lastName) LIKE LOWER(CONCAT('%', :lastName, '%'))) " +
            "AND (:position IS NULL OR LOWER(a.employee.position) LIKE LOWER(CONCAT('%', :position, '%')))")


    List <ActuaryInfo> findByTypeAndFilters(
            @Param("type") ActuaryType type,
            @Param("email") String email,
            @Param("firstName") String firstName,
            @Param("lastName") String lastName,
            @Param("position") String position
    );
}
