package rs.raf.banka2_bek.client.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.raf.banka2_bek.client.model.Client;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByEmail(String email);
}
