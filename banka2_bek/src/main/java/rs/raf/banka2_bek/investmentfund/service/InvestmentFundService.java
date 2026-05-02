package rs.raf.banka2_bek.investmentfund.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.service.EmployeeUserDetails;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.investmentfund.dto.ClientFundPositionDto;
import rs.raf.banka2_bek.investmentfund.model.ClientFundPosition;
import rs.raf.banka2_bek.investmentfund.repository.ClientFundPositionRepository;

import java.util.Comparator;
import java.util.List;

/**
 * T12 — liste pozicija u investicionim fondovima.
 * Spec: Celina 4 (Moj portfolio, Napomene 1 i 2) + zadatak T12.
 *
 * Konvencija „klijent koji je vlasnik banke“ (po Napomenama 1 i 2):
 *   bankine investicije se vode kao ClientFundPosition pod posebnim klijentom
 *   koji predstavlja vlasnika banke. Taj klijent moze biti:
 *     - sama banka kao pravno lice (T12 seed: „Banka 2 d.o.o.“), ili
 *     - bilo ko drugi (npr. fizicki osnivac ili druga firma) ko je registrovan
 *       kao Client i postavljen kao vlasnik banke.
 *   Email tog klijenta se konfigurise preko bank.owner-client-email.
 *
 * Granice ovog servisa: samo listMyPositions i listBankPositions.
 * Ostale fond metode (createFund, listDiscovery, getFundDetails, invest, withdraw,
 * performanse, likvidacija) su vlasnistvo T7/T8/T9/T10.
 */
@Service
@RequiredArgsConstructor
public class InvestmentFundService {

    private final ClientFundPositionRepository clientFundPositionRepository;
    private final ClientRepository clientRepository;

    @Value("${bank.owner-client-email}")
    private String bankOwnerClientEmail;

    /**
     * "Moj portfolio" -> tab "Moji fondovi" (lista pozicija u fondovima).
     * Pravilo (zadatak T12 + Celina 4 + Napomene 1 i 2):
     *   - User (klijent)              -> pozicije tog klijenta po njegovom email-u.
     *                                    Ako se email tog User-a poklapa sa
     *                                    bank.owner-client-email, vidi bankine
     *                                    pozicije (legitimno — taj klijent JESTE
     *                                    vlasnik banke).
     *   - EmployeeUserDetails (sup.)  -> pozicije klijenta-vlasnika banke
     *                                    (bank.owner-client-email). Zaposleni nije
     *                                    Client i nema licne pozicije; po Napomeni 1+2
     *                                    bankine investicije se vode pod klijentom
     *                                    vlasnikom (taj klijent moze biti i sama
     *                                    banka kao pravno lice).
     *                                    Napomena za FE: po Celini 4 (sekcija „Dodatak
     *                                    za Moj portfolio“) supervizorov „Moji fondovi“
     *                                    tab strogo gledano prikazuje fondove kojima
     *                                    upravlja, NE pozicije. Ali zadataci.txt
     *                                    eksplicitno kaze da listMyPositions koriste i
     *                                    supervizori, pa se ovde u T12 poklapamo sa
     *                                    tom interpretacijom (semantika „moje“ za
     *                                    supervizora = pozicije institucije koju
     *                                    reprezentuje). T7 ce dodati zaseban endpoint
     *                                    za fondove kojima supervizor upravlja
     *                                    (getManagedFunds ili sl.).
     *   - Anonimni / neprepoznat      -> prazna lista.
     */
    @Transactional(readOnly = true)
    public List<ClientFundPositionDto> listMyPositions(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return List.of();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return findPositionsByClientEmail(user.getEmail());
        }
        if (principal instanceof EmployeeUserDetails) {
            return findPositionsByClientEmail(bankOwnerClientEmail);
        }
        return List.of();
    }

    /**
     * Pozicije gde je ucesnik klijent koji je vlasnik banke (po Napomeni 2 — taj
     * klijent moze biti sama banka kao pravno lice ili neko drugo lice/firma vlasnik) —
     * "Pozicije u fondovima" tab u Profit Banke portalu (Celina 4 + zadatak T12).
     */
    @Transactional(readOnly = true)
    public List<ClientFundPositionDto> listBankPositions() {
        return findPositionsByClientEmail(bankOwnerClientEmail);
    }

    private List<ClientFundPositionDto> findPositionsByClientEmail(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        return clientRepository
                .findByEmail(email)
                .map(client -> clientFundPositionRepository.findByClient_Id(client.getId()).stream()
                        .map(this::toDto)
                        .sorted(Comparator.comparing(ClientFundPositionDto::fundName))
                        .toList())
                .orElseGet(List::of);
    }

    private ClientFundPositionDto toDto(ClientFundPosition p) {
        var manager = p.getFund().getManager();
        return ClientFundPositionDto.builder()
                .fundId(p.getFund().getId())
                .fundName(p.getFund().getName())
                .managerName(manager == null ? null : manager.getFirstName())
                .managerLastname(manager == null ? null : manager.getLastName())
                .clientId(p.getClient().getId())
                .totalInvestedAmountRsd(p.getTotalInvestedAmount())
                .build();
    }
}
