package rs.raf.banka2_bek.actuary.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rs.raf.banka2_bek.employee.model.Employee;

import java.math.BigDecimal;

/**
 * Entitet koji cuva aktuarske podatke za zaposlene (agente i supervizore).
 * Svaki admin je ujedno i supervizor. Agent ima limit, supervizor nema.
 *
 * Specifikacija: Celina 3 - Aktuari
 */
@Entity
@Table(name = "actuary_info")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActuaryInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // TODO: Povezati sa Employee entitetom
    // - OneToOne veza sa Employee tabelom
    // - Zaposleni moze biti agent ILI supervizor (ne oba)
    // - Svaki admin automatski postaje supervizor
    @OneToOne
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    // TODO: Enum sa vrednostima AGENT, SUPERVISOR
    // - Admin je automatski SUPERVISOR
    // - Samo admini mogu dodeljivati/oduzimati ove role
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActuaryType actuaryType;

    // TODO: Limit - maksimalan iznos novca koji agent moze da potrosi dnevno
    // - Izrazeno u RSD
    // - Supervizor NEMA limit (ovo polje je null za supervizore)
    // - Menja se od strane supervizora po potrebi
    @Column(name = "daily_limit")
    private BigDecimal dailyLimit;

    // TODO: Used Limit - koliko je agent vec potrosio danas
    // - Resetuje se automatski na kraju svakog dana (23:59)
    // - Supervizor moze rucno da resetuje usedLimit na 0
    // - Menja se pri svakoj transakciji
    // - Konverzija u RSD ako je trgovina u drugoj valuti (bez provizije)
    @Column(name = "used_limit")
    private BigDecimal usedLimit;

    // TODO: Need Approval flag
    // - Ako je true, SVAKI order ovog agenta mora da odobri supervizor
    // - Supervizor uvek ima false
    // - Menja se od strane supervizora po potrebi
    @Column(name = "need_approval", nullable = false)
    private boolean needApproval;
}
