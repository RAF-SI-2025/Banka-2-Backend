package rs.raf.banka2_bek.investmentFund.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.ClientFundPositionDto;
import rs.raf.banka2_bek.investmentfund.model.ClientFundPosition;
import rs.raf.banka2_bek.investmentfund.model.InvestmentFund;
import rs.raf.banka2_bek.investmentfund.repository.ClientFundPositionRepository;
import rs.raf.banka2_bek.investmentfund.repository.InvestmentFundRepository;
import rs.raf.banka2_bek.investmentfund.service.InvestmentFundService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T12 — Mockito unit testovi za listMyPositions / listBankPositions.
 *
 * Spec ref:
 *   - Celina 4 (Nova) "Moj portfolio -> Moji fondovi" (listMyPositions)
 *   - Celina 4 (Nova) §4406-4435 Napomena 1+2 "Banka kao klijent fonda" (listBankPositions)
 *   - Profit Banke "Pozicije u fondovima" tab (consumer od listBankPositions)
 *
 * Pokriva:
 *   - happy path mapiranja u DTO
 *   - prazni listovi (null parametri, prazna baza)
 *   - email-based resolvovanje banka client_id-ja
 *   - graceful fallback kad banka klijent nije seed-ovan
 */
@ExtendWith(MockitoExtension.class)
class InvestmentFundServicePositionsTest {

    @Mock
    private InvestmentFundRepository investmentFundRepository;
    @Mock
    private ClientFundPositionRepository clientFundPositionRepository;
    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private InvestmentFundService service;

    private static final String BANK_EMAIL = "banka2.doo@banka.rs";

    @BeforeEach
    void setUp() {
        // @Value se ne resolvuje sa MockitoExtension-om — koristimo
        // ReflectionTestUtils da postavimo polje rucno. Inace bi
        // bankOwnerClientEmail bio null i lookup bi se srusio.
        ReflectionTestUtils.setField(service, "bankOwnerClientEmail", BANK_EMAIL);
    }

    private ClientFundPosition position(Long id, Long fundId, Long userId, String role, String invested) {
        ClientFundPosition p = new ClientFundPosition();
        p.setId(id);
        p.setFundId(fundId);
        p.setUserId(userId);
        p.setUserRole(role);
        p.setTotalInvested(new BigDecimal(invested));
        p.setLastModifiedAt(LocalDateTime.now());
        return p;
    }

    private InvestmentFund fund(Long id, String name) {
        InvestmentFund f = new InvestmentFund();
        f.setId(id);
        f.setName(name);
        return f;
    }

    // ─── listMyPositions ──────────────────────────────────────────────────

    @Test
    @DisplayName("listMyPositions — vraca pozicije korisnika sa popunjenim fundName-om")
    void listMyPositions_happyPath() {
        ClientFundPosition p1 = position(101L, 1L, 5L, "CLIENT", "10000.00");
        ClientFundPosition p2 = position(102L, 2L, 5L, "CLIENT", "25000.00");
        when(clientFundPositionRepository.findByUserIdAndUserRole(5L, "CLIENT"))
                .thenReturn(List.of(p1, p2));
        when(investmentFundRepository.findAllById(any()))
                .thenReturn(List.of(fund(1L, "Stable Income"), fund(2L, "Tech Growth")));

        List<ClientFundPositionDto> result = service.listMyPositions(5L, "CLIENT");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ClientFundPositionDto::getFundName)
                .containsExactlyInAnyOrder("Stable Income", "Tech Growth");
        assertThat(result).extracting(ClientFundPositionDto::getUserId)
                .containsOnly(5L);
        assertThat(result).extracting(ClientFundPositionDto::getUserRole)
                .containsOnly("CLIENT");
        // Izvedena polja su jos uvek null (FundValueCalculator nije gotov u T12).
        assertThat(result).allMatch(d -> d.getCurrentValue() == null
                && d.getPercentOfFund() == null
                && d.getProfit() == null);
    }

    @Test
    @DisplayName("listMyPositions — vraca prazan list kad korisnik nema pozicija")
    void listMyPositions_emptyForUserWithoutPositions() {
        when(clientFundPositionRepository.findByUserIdAndUserRole(99L, "CLIENT"))
                .thenReturn(List.of());

        List<ClientFundPositionDto> result = service.listMyPositions(99L, "CLIENT");

        assertThat(result).isEmpty();
        // Ne sme zvati findAllById ako nema pozicija — fail fast.
        verify(investmentFundRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("listMyPositions — null userId vraca prazan list (defensive)")
    void listMyPositions_nullUserId() {
        List<ClientFundPositionDto> result = service.listMyPositions(null, "CLIENT");

        assertThat(result).isEmpty();
        verify(clientFundPositionRepository, never()).findByUserIdAndUserRole(any(), anyString());
    }

    @Test
    @DisplayName("listMyPositions — blank userRole vraca prazan list (defensive)")
    void listMyPositions_blankRole() {
        List<ClientFundPositionDto> result = service.listMyPositions(5L, "  ");

        assertThat(result).isEmpty();
        verify(clientFundPositionRepository, never()).findByUserIdAndUserRole(any(), anyString());
    }

    // ─── listBankPositions ────────────────────────────────────────────────

    @Test
    @DisplayName("listBankPositions — resolvuje banka client_id po email-u i vraca njegove pozicije")
    void listBankPositions_happyPath() {
        Client bankClient = new Client();
        bankClient.setId(10L);
        bankClient.setEmail(BANK_EMAIL);
        when(clientRepository.findByEmail(BANK_EMAIL)).thenReturn(Optional.of(bankClient));

        ClientFundPosition p = position(201L, 1L, 10L, "CLIENT", "250000.00");
        when(clientFundPositionRepository.findByUserIdAndUserRole(10L, "CLIENT"))
                .thenReturn(List.of(p));
        when(investmentFundRepository.findAllById(any()))
                .thenReturn(List.of(fund(1L, "Stable Income")));

        List<ClientFundPositionDto> result = service.listBankPositions();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFundName()).isEqualTo("Stable Income");
        assertThat(result.get(0).getUserId()).isEqualTo(10L);
        assertThat(result.get(0).getTotalInvested()).isEqualByComparingTo("250000.00");
    }

    @Test
    @DisplayName("listBankPositions — graceful fallback kad bank client nije seed-ovan")
    void listBankPositions_missingBankClient() {
        when(clientRepository.findByEmail(BANK_EMAIL)).thenReturn(Optional.empty());

        List<ClientFundPositionDto> result = service.listBankPositions();

        assertThat(result).isEmpty();
        // Ne sme dalje zvati pozicije ako banka klijent ne postoji.
        verify(clientFundPositionRepository, never()).findByUserIdAndUserRole(any(), anyString());
    }

    @Test
    @DisplayName("listBankPositions — vraca prazan list kad banka nema pozicija u fondovima")
    void listBankPositions_bankExistsButNoPositions() {
        Client bankClient = new Client();
        bankClient.setId(10L);
        when(clientRepository.findByEmail(BANK_EMAIL)).thenReturn(Optional.of(bankClient));
        when(clientFundPositionRepository.findByUserIdAndUserRole(eq(10L), eq("CLIENT")))
                .thenReturn(List.of());

        List<ClientFundPositionDto> result = service.listBankPositions();

        assertThat(result).isEmpty();
    }
}
