package rs.raf.banka2_bek.company.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.company.model.Company;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "authorized_persons",
    uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "company_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthorizedPerson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Fizičko lice koje je ovlašćeno da zastupa kompaniju
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User user;

    // Kompanija za koju je ovlašćeno
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
