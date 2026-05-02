package rs.raf.banka2_bek.investmentfund.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.service.EmployeeUserDetails;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.investmentfund.dto.ClientFundPositionDto;
import rs.raf.banka2_bek.investmentfund.model.ClientFundPosition;
import rs.raf.banka2_bek.investmentfund.model.InvestmentFund;
import rs.raf.banka2_bek.investmentfund.repository.ClientFundPositionRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentFundServiceTest {

    private static final String BANK_CLIENT_EMAIL = "banka2.entity@banka2.rs";

    @Mock
    private ClientFundPositionRepository clientFundPositionRepository;

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private InvestmentFundService investmentFundService;

    @BeforeEach
    void injectBankEmail() {
        ReflectionTestUtils.setField(investmentFundService, "bankOwnerClientEmail", BANK_CLIENT_EMAIL);
    }

    private static ClientFundPosition position(long posId, Client client, long fundId, String fundName, String amount) {
        return position(posId, client, fundId, fundName, amount, defaultManager());
    }

    private static ClientFundPosition position(long posId, Client client, long fundId, String fundName,
                                               String amount, Employee manager) {
        InvestmentFund fund = mock(InvestmentFund.class);
        when(fund.getId()).thenReturn(fundId);
        when(fund.getName()).thenReturn(fundName);
        when(fund.getManager()).thenReturn(manager);
        return ClientFundPosition.builder()
                .id(posId)
                .client(client)
                .fund(fund)
                .totalInvestedAmount(new BigDecimal(amount))
                .build();
    }

    private static Employee defaultManager() {
        return Employee.builder()
                .id(1L)
                .firstName("Nikola")
                .lastName("Milenković")
                .build();
    }

    private static Authentication authForUser(User user) {
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    private static Authentication authForEmployee(EmployeeUserDetails employee) {
        return new UsernamePasswordAuthenticationToken(employee, null, employee.getAuthorities());
    }

    // ── listMyPositions: klijent ─────────────────────────────────────────────

    @Test
    void listMyPositions_mapsRowsForClientUser() {
        User user = new User();
        user.setEmail("stefan.jovanovic@gmail.com");
        user.setRole("CLIENT");

        Client client = mock(Client.class);
        when(client.getId()).thenReturn(1L);

        ClientFundPosition pos = position(99L, client, 10L, "Alpha Seed Fund", "10000.0000");

        when(clientRepository.findByEmail("stefan.jovanovic@gmail.com")).thenReturn(Optional.of(client));
        when(clientFundPositionRepository.findByClient_Id(1L)).thenReturn(List.of(pos));

        List<ClientFundPositionDto> result = investmentFundService.listMyPositions(authForUser(user));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).fundName()).isEqualTo("Alpha Seed Fund");
        assertThat(result.get(0).clientId()).isEqualTo(1L);
        assertThat(result.get(0).totalInvestedAmountRsd()).isEqualByComparingTo("10000.0000");
        assertThat(result.get(0).managerName()).isEqualTo("Nikola");
        assertThat(result.get(0).managerLastname()).isEqualTo("Milenković");
    }

    @Test
    void listMyPositions_managerWithNullNames_returnsNullManagerFields() {
        User user = new User();
        user.setEmail("client@gmail.com");
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(5L);

        Employee managerWithNoName = Employee.builder().id(2L).build();
        ClientFundPosition pos = position(1L, client, 10L, "Fund X", "100.0000", managerWithNoName);

        when(clientRepository.findByEmail("client@gmail.com")).thenReturn(Optional.of(client));
        when(clientFundPositionRepository.findByClient_Id(5L)).thenReturn(List.of(pos));

        List<ClientFundPositionDto> result = investmentFundService.listMyPositions(authForUser(user));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).managerName()).isNull();
        assertThat(result.get(0).managerLastname()).isNull();
    }

    @Test
    void listMyPositions_nullManager_returnsNullManagerFields() {
        User user = new User();
        user.setEmail("client2@gmail.com");
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(6L);

        ClientFundPosition pos = position(1L, client, 11L, "Fund Y", "200.0000", null);

        when(clientRepository.findByEmail("client2@gmail.com")).thenReturn(Optional.of(client));
        when(clientFundPositionRepository.findByClient_Id(6L)).thenReturn(List.of(pos));

        List<ClientFundPositionDto> result = investmentFundService.listMyPositions(authForUser(user));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).managerName()).isNull();
        assertThat(result.get(0).managerLastname()).isNull();
    }

    @Test
    void listMyPositions_clientUserMissingInClientsTable_returnsEmpty() {
        User user = new User();
        user.setEmail("ghost@nowhere.rs");
        user.setRole("CLIENT");

        when(clientRepository.findByEmail("ghost@nowhere.rs")).thenReturn(Optional.empty());

        assertThat(investmentFundService.listMyPositions(authForUser(user))).isEmpty();
        verify(clientFundPositionRepository, never()).findByClient_Id(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void listMyPositions_clientWithNoPositions_returnsEmpty() {
        User user = new User();
        user.setEmail("empty@gmail.com");
        user.setRole("CLIENT");

        Client client = mock(Client.class);
        when(client.getId()).thenReturn(7L);

        when(clientRepository.findByEmail("empty@gmail.com")).thenReturn(Optional.of(client));
        when(clientFundPositionRepository.findByClient_Id(7L)).thenReturn(List.of());

        assertThat(investmentFundService.listMyPositions(authForUser(user))).isEmpty();
    }

    @Test
    void listMyPositions_sortsResultsByFundNameAscending() {
        User user = new User();
        user.setEmail("sorter@gmail.com");
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(2L);

        ClientFundPosition gamma = position(1L, client, 30L, "Gamma Fund", "300.0000");
        ClientFundPosition alpha = position(2L, client, 10L, "Alpha Fund", "100.0000");
        ClientFundPosition beta  = position(3L, client, 20L, "Beta Fund",  "200.0000");

        when(clientRepository.findByEmail("sorter@gmail.com")).thenReturn(Optional.of(client));
        when(clientFundPositionRepository.findByClient_Id(2L)).thenReturn(List.of(gamma, alpha, beta));

        List<ClientFundPositionDto> result = investmentFundService.listMyPositions(authForUser(user));

        assertThat(result).extracting(ClientFundPositionDto::fundName)
                .containsExactly("Alpha Fund", "Beta Fund", "Gamma Fund");
    }

    // ── listMyPositions: supervizor (Napomena 2 — vidi bankine pozicije) ─────

    @Test
    void listMyPositions_supervisorReturnsBankPositions() {
        Employee employee = Employee.builder()
                .id(99L)
                .firstName("Nikola")
                .lastName("Milenković")
                .email("nikola.milenkovic@banka.rs")
                .username("nikola.milenkovic")
                .build();
        EmployeeUserDetails employeeUserDetails = new EmployeeUserDetails(employee);

        Client bankClient = mock(Client.class);
        when(bankClient.getId()).thenReturn(42L);
        ClientFundPosition pos = position(1L, bankClient, 10L, "Alpha Seed Fund", "2500000.0000");

        when(clientRepository.findByEmail(BANK_CLIENT_EMAIL)).thenReturn(Optional.of(bankClient));
        when(clientFundPositionRepository.findByClient_Id(42L)).thenReturn(List.of(pos));

        List<ClientFundPositionDto> result = investmentFundService.listMyPositions(authForEmployee(employeeUserDetails));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).clientId()).isEqualTo(42L);
        verify(clientRepository, never()).findByEmail("nikola.milenkovic@banka.rs");
    }

    // ── listMyPositions: ivični slučajevi za auth ────────────────────────────

    @Test
    void listMyPositions_nullAuthentication_returnsEmpty() {
        assertThat(investmentFundService.listMyPositions(null)).isEmpty();
    }

    @Test
    void listMyPositions_nullPrincipal_returnsEmpty() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                null, null, List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertThat(investmentFundService.listMyPositions(auth)).isEmpty();
    }

    @Test
    void listMyPositions_unknownPrincipalType_returnsEmpty() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "anonymous-string", null, List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        assertThat(investmentFundService.listMyPositions(auth)).isEmpty();
    }

    // ── listBankPositions ────────────────────────────────────────────────────

    @Test
    void listBankPositions_resolvesBankClient() {
        Client bankClient = mock(Client.class);
        when(bankClient.getId()).thenReturn(42L);
        ClientFundPosition pos = position(1L, bankClient, 10L, "Alpha Seed Fund", "2500000.0000");

        when(clientRepository.findByEmail(BANK_CLIENT_EMAIL)).thenReturn(Optional.of(bankClient));
        when(clientFundPositionRepository.findByClient_Id(42L)).thenReturn(List.of(pos));

        List<ClientFundPositionDto> result = investmentFundService.listBankPositions();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).clientId()).isEqualTo(42L);
        assertThat(result.get(0).fundName()).isEqualTo("Alpha Seed Fund");
        assertThat(result.get(0).managerName()).isEqualTo("Nikola");
        assertThat(result.get(0).managerLastname()).isEqualTo("Milenković");
    }

    @Test
    void listBankPositions_bankClientMissing_returnsEmpty() {
        when(clientRepository.findByEmail(BANK_CLIENT_EMAIL)).thenReturn(Optional.empty());

        assertThat(investmentFundService.listBankPositions()).isEmpty();
        verify(clientFundPositionRepository, never()).findByClient_Id(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void listBankPositions_blankConfiguredEmail_returnsEmpty() {
        ReflectionTestUtils.setField(investmentFundService, "bankOwnerClientEmail", "   ");

        assertThat(investmentFundService.listBankPositions()).isEmpty();
        verify(clientRepository, never()).findByEmail(org.mockito.ArgumentMatchers.anyString());
    }
}
