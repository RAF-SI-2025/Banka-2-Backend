package rs.raf.banka2_bek.actuary.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActuaryInfoRepository extends JpaRepository<ActuaryInfo, Long> {

    Optional<ActuaryInfo> findByEmployeeId(Long employeeId);

    // TODO: Dodati query metode po potrebi:
    // - findAllByActuaryType(ActuaryType type) - za filtriranje agenata/supervizora
    // - findByEmployeeEmail(String email) - za pretragu po email-u
    // - findByEmployee_FirstNameContainingAndEmployee_LastNameContaining(...) - za filtriranje
    List<ActuaryInfo> findAllByActuaryType(ActuaryType actuaryType);
}
