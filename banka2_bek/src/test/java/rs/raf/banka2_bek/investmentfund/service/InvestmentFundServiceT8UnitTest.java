package rs.raf.banka2_bek.investmentfund.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import rs.raf.banka2_bek.account.model.*;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.actuary.model.ActuaryInfo;
import rs.raf.banka2_bek.actuary.model.ActuaryType;
import rs.raf.banka2_bek.actuary.repository.ActuaryInfoRepository;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.company.repository.CompanyRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.InvestFundDto;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.WithdrawFundDto;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.ClientFundPositionDto;
import rs.raf.banka2_bek.investmentfund.dto.InvestmentFundDtos.ClientFundTransactionDto;
import rs.raf.banka2_bek.investmentfund.model.*;
import rs.raf.banka2_bek.investmentfund.repository.*;
import rs.raf.banka2_bek.order.service.CurrencyConversionService;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.repository.ListingRepository;
import rs.raf.banka2_bek.order.exception.InsufficientFundsException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvestmentFundServiceT8UnitTest {

    @Mock private FundValueSnapshotRepository fundValueSnapshotRepository;
    @Mock private InvestmentFundRepository investmentFundRepository;
    @Mock private ClientFundPositionRepository clientFundPositionRepository;
    @Mock private ClientFundTransactionRepository clientFundTransactionRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private ActuaryInfoRepository actuaryInfoRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private FundValueCalculator fundValueCalculator;
    @Mock private FundLiquidationService fundLiquidationService;
    @Mock private CurrencyConversionService currencyConversionService;

    @InjectMocks
    private InvestmentFundService service;

    private final AtomicLong txId = new AtomicLong(1);
    private final AtomicLong positionId = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "bankOwnerClientEmail", "banka2.doo@banka.rs");

        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(clientFundTransactionRepository.save(any(ClientFundTransaction.class))).thenAnswer(invocation -> {
            ClientFundTransaction tx = invocation.getArgument(0);
            if (tx.getId() == null) {
                tx.setId(txId.getAndIncrement());
            }
            return tx;
        });

        when(clientFundPositionRepository.save(any(ClientFundPosition.class))).thenAnswer(invocation -> {
            ClientFundPosition position = invocation.getArgument(0);
            if (position.getId() == null) {
                position.setId(positionId.getAndIncrement());
            }
            return position;
        });

        when(fundValueCalculator.computeFundValue(any())).thenReturn(BigDecimal.ZERO);
        when(fundValueCalculator.computeProfit(any())).thenReturn(BigDecimal.ZERO);
        when(clientFundPositionRepository.findByFundId(anyLong())).thenReturn(List.of());
    }

    @Test
    @DisplayName("T8 UNIT: klijent investira uz FX konverziju i placa 1% komisiju")
    void invest_clientWithFxConversion_chargesOnePercentCommission() {
        Long fundId = 1L;
        Long clientId = 10L;
        Long sourceAccountId = 101L;
        Long fundAccountId = 201L;
        Long bankEurAccountId = 301L;

        Client client = client(clientId, "Stefan", "Jovanovic");
        Currency eur = currency(1L, "EUR");
        Currency rsd = currency(8L, "RSD");

        InvestmentFund fund = fund(fundId, fundAccountId, "Banka 2 Stable Income", new BigDecimal("1000"));

        Account sourceAccount = clientAccount(sourceAccountId, client, eur, new BigDecimal("1000.0000"));
        Account fundAccount = bankAccount(fundAccountId, rsd, AccountCategory.FUND, new BigDecimal("0.0000"));
        Account bankEurAccount = bankAccount(bankEurAccountId, eur, AccountCategory.BANK_TRADING, new BigDecimal("0.0000"));

        when(investmentFundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findForUpdateById(fundAccountId)).thenReturn(Optional.of(fundAccount));
        when(accountRepository.findFirstByAccountCategoryAndCurrency_Code(AccountCategory.BANK_TRADING, "EUR"))
                .thenReturn(Optional.of(bankEurAccount));
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientFundPositionRepository.findByFundIdAndUserIdAndUserRole(fundId, clientId, UserRole.CLIENT))
                .thenReturn(Optional.empty());

        when(currencyConversionService.convertForPurchase(
                eq(new BigDecimal("10000.0000")),
                eq("RSD"),
                eq("EUR"),
                eq(true)
        )).thenReturn(new CurrencyConversionService.ConversionResult(
                new BigDecimal("86.0000"),
                new BigDecimal("1.0000"),
                new BigDecimal("0.008600"),
                new BigDecimal("0.008500")
        ));

        ClientFundPositionDto result = service.invest(
                fundId,
                new InvestFundDto(new BigDecimal("10000"), "RSD", sourceAccountId),
                clientId,
                UserRole.CLIENT
        );

        assertNotNull(result);
        assertEquals(fundId, result.getFundId());
        assertEquals(clientId, result.getUserId());
        assertEquals(UserRole.CLIENT, result.getUserRole());
        assertBd("10000.0000", result.getTotalInvested());

        assertBd("913.0000", sourceAccount.getAvailableBalance());
        assertBd("913.0000", sourceAccount.getBalance());
        assertBd("10000.0000", fundAccount.getAvailableBalance());
        assertBd("10000.0000", fundAccount.getBalance());
        assertBd("1.0000", bankEurAccount.getAvailableBalance());
        assertBd("1.0000", bankEurAccount.getBalance());

        ArgumentCaptor<ClientFundTransaction> txCaptor = ArgumentCaptor.forClass(ClientFundTransaction.class);
        verify(clientFundTransactionRepository, atLeast(2)).save(txCaptor.capture());

        ClientFundTransaction lastTx = txCaptor.getAllValues().get(txCaptor.getAllValues().size() - 1);
        assertEquals(ClientFundTransactionStatus.COMPLETED, lastTx.getStatus());
        assertTrue(lastTx.isInflow());
        assertBd("10000.0000", lastTx.getAmountRsd());
    }

    @Test
    @DisplayName("T8 UNIT: supervizor investira u ime banke uz FX konverziju i placa 0% komisije")
    void invest_supervisorWithFxConversion_chargesZeroCommission() {
        Long fundId = 1L;
        Long supervisorId = 5L;
        Long bankClientId = 50L;
        Long sourceAccountId = 102L;
        Long fundAccountId = 202L;

        Currency eur = currency(1L, "EUR");
        Currency rsd = currency(8L, "RSD");

        Client bankClient = client(bankClientId, "Banka 2", "d.o.o.");
        InvestmentFund fund = fund(fundId, fundAccountId, "Banka 2 Stable Income", new BigDecimal("1000"));

        Account sourceAccount = bankAccount(sourceAccountId, eur, AccountCategory.BANK_TRADING, new BigDecimal("1000.0000"));
        Account fundAccount = bankAccount(fundAccountId, rsd, AccountCategory.FUND, new BigDecimal("0.0000"));

        when(investmentFundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findForUpdateById(fundAccountId)).thenReturn(Optional.of(fundAccount));
        when(actuaryInfoRepository.findByEmployeeId(supervisorId)).thenReturn(Optional.of(supervisorInfo()));
        when(clientRepository.findByEmail("banka2.doo@banka.rs")).thenReturn(Optional.of(bankClient));
        when(clientRepository.findById(bankClientId)).thenReturn(Optional.of(bankClient));
        when(clientFundPositionRepository.findByFundIdAndUserIdAndUserRole(fundId, bankClientId, UserRole.CLIENT))
                .thenReturn(Optional.empty());

        when(currencyConversionService.convertForPurchase(
                eq(new BigDecimal("10000.0000")),
                eq("RSD"),
                eq("EUR"),
                eq(false)
        )).thenReturn(new CurrencyConversionService.ConversionResult(
                new BigDecimal("85.0000"),
                BigDecimal.ZERO,
                new BigDecimal("0.008500"),
                new BigDecimal("0.008500")
        ));

        ClientFundPositionDto result = service.invest(
                fundId,
                new InvestFundDto(new BigDecimal("10000"), "RSD", sourceAccountId),
                supervisorId,
                UserRole.EMPLOYEE
        );

        assertNotNull(result);
        assertEquals(bankClientId, result.getUserId());
        assertEquals(UserRole.CLIENT, result.getUserRole());
        assertBd("10000.0000", result.getTotalInvested());

        assertBd("915.0000", sourceAccount.getAvailableBalance());
        assertBd("10000.0000", fundAccount.getAvailableBalance());

        verify(accountRepository, never())
                .findFirstByAccountCategoryAndCurrency_Code(AccountCategory.BANK_TRADING, "EUR");
    }

    @Test
    @DisplayName("T8 UNIT: withdraw prolazi odmah kada fond ima dovoljno likvidnih sredstava")
    void withdraw_whenFundHasEnoughCash_completesImmediately() {
        Long fundId = 1L;
        Long clientId = 10L;
        Long fundAccountId = 201L;
        Long destinationAccountId = 101L;

        Client client = client(clientId, "Stefan", "Jovanovic");
        Currency rsd = currency(8L, "RSD");

        InvestmentFund fund = fund(fundId, fundAccountId, "Banka 2 Stable Income", new BigDecimal("1000"));
        Account fundAccount = bankAccount(fundAccountId, rsd, AccountCategory.FUND, new BigDecimal("10000.0000"));
        Account destinationAccount = clientAccount(destinationAccountId, client, rsd, new BigDecimal("1000.0000"));

        ClientFundPosition position = position(1L, fundId, clientId, UserRole.CLIENT, new BigDecimal("10000.0000"));

        when(investmentFundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(fundAccountId)).thenReturn(Optional.of(fundAccount));
        when(accountRepository.findForUpdateById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientFundPositionRepository.findByFundIdAndUserIdAndUserRole(fundId, clientId, UserRole.CLIENT))
                .thenReturn(Optional.of(position));

        ClientFundTransactionDto result = service.withdraw(
                fundId,
                new WithdrawFundDto(new BigDecimal("5000"), destinationAccountId),
                clientId,
                UserRole.CLIENT
        );

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertFalse(result.isInflow());
        assertBd("5000.0000", result.getAmountRsd());

        assertBd("5000.0000", fundAccount.getAvailableBalance());
        assertBd("5000.0000", fundAccount.getBalance());
        assertBd("6000.0000", destinationAccount.getAvailableBalance());
        assertBd("6000.0000", destinationAccount.getBalance());

        verify(clientFundPositionRepository).save(argThat(p ->
                p.getTotalInvested().compareTo(new BigDecimal("5000.0000")) == 0
        ));
        verify(fundLiquidationService, never()).liquidateFor(anyLong(), any());
    }

    @Test
    @DisplayName("T8 UNIT: withdraw ide u PENDING i poziva T9 kada fond nema dovoljno cash-a")
    void withdraw_whenFundHasInsufficientCash_setsPendingAndCallsLiquidation() {
        Long fundId = 1L;
        Long clientId = 10L;
        Long fundAccountId = 201L;
        Long destinationAccountId = 101L;

        Client client = client(clientId, "Stefan", "Jovanovic");
        Currency rsd = currency(8L, "RSD");

        InvestmentFund fund = fund(fundId, fundAccountId, "Banka 2 Stable Income", new BigDecimal("1000"));
        Account fundAccount = bankAccount(fundAccountId, rsd, AccountCategory.FUND, new BigDecimal("1000.0000"));
        Account destinationAccount = clientAccount(destinationAccountId, client, rsd, new BigDecimal("0.0000"));

        ClientFundPosition position = position(1L, fundId, clientId, UserRole.CLIENT, new BigDecimal("5000.0000"));

        when(investmentFundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(fundAccountId)).thenReturn(Optional.of(fundAccount));
        when(accountRepository.findForUpdateById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientFundPositionRepository.findByFundIdAndUserIdAndUserRole(fundId, clientId, UserRole.CLIENT))
                .thenReturn(Optional.of(position));

        ClientFundTransactionDto result = service.withdraw(
                fundId,
                new WithdrawFundDto(new BigDecimal("5000"), destinationAccountId),
                clientId,
                UserRole.CLIENT
        );

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        assertFalse(result.isInflow());
        assertBd("5000.0000", result.getAmountRsd());
        assertNotNull(result.getFailureReason());

        assertBd("1000.0000", fundAccount.getAvailableBalance());
        assertBd("0.0000", destinationAccount.getAvailableBalance());

        verify(clientFundPositionRepository).delete(position);
        verify(fundLiquidationService).liquidateFor(eq(fundId), argThat(amount ->
                amount.compareTo(new BigDecimal("4000.0000")) == 0
        ));
    }

    @Test
    @DisplayName("T8 UNIT: invest odbija uplatu ispod minimalnog uloga")
    void invest_whenAmountBelowMinimum_throwsIllegalArgumentException() {
        Long fundId = 1L;
        Long clientId = 10L;
        Long sourceAccountId = 101L;
        Long fundAccountId = 201L;

        Client client = client(clientId, "Stefan", "Jovanovic");
        Currency rsd = currency(8L, "RSD");

        InvestmentFund fund = fund(fundId, fundAccountId, "Banka 2 Stable Income", new BigDecimal("1000"));
        Account sourceAccount = clientAccount(sourceAccountId, client, rsd, new BigDecimal("10000.0000"));
        Account fundAccount = bankAccount(fundAccountId, rsd, AccountCategory.FUND, BigDecimal.ZERO);

        when(investmentFundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findForUpdateById(fundAccountId)).thenReturn(Optional.of(fundAccount));
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.invest(
                        fundId,
                        new InvestFundDto(new BigDecimal("500"), "RSD", sourceAccountId),
                        clientId,
                        UserRole.CLIENT
                )
        );

        assertTrue(ex.getMessage().contains("najmanje"));
        verify(clientFundTransactionRepository, never()).save(any(ClientFundTransaction.class));
        verify(clientFundPositionRepository, never()).save(any(ClientFundPosition.class));
    }

    @Test
    @DisplayName("T8 UNIT: invest odbija kada klijent pokusa da koristi tudji racun")
    void invest_whenClientUsesAnotherClientAccount_throwsAccessDenied() {
        Long fundId = 1L;
        Long loggedClientId = 10L;
        Long otherClientId = 99L;
        Long sourceAccountId = 101L;
        Long fundAccountId = 201L;

        Client otherClient = client(otherClientId, "Petar", "Petrovic");
        Currency rsd = currency(8L, "RSD");

        InvestmentFund fund = fund(fundId, fundAccountId, "Banka 2 Stable Income", new BigDecimal("1000"));
        Account sourceAccount = clientAccount(sourceAccountId, otherClient, rsd, new BigDecimal("10000.0000"));
        Account fundAccount = bankAccount(fundAccountId, rsd, AccountCategory.FUND, BigDecimal.ZERO);

        when(investmentFundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findForUpdateById(fundAccountId)).thenReturn(Optional.of(fundAccount));

        assertThrows(
                AccessDeniedException.class,
                () -> service.invest(
                        fundId,
                        new InvestFundDto(new BigDecimal("5000"), "RSD", sourceAccountId),
                        loggedClientId,
                        UserRole.CLIENT
                )
        );

        verify(clientFundTransactionRepository, never()).save(any(ClientFundTransaction.class));
        verify(clientFundPositionRepository, never()).save(any(ClientFundPosition.class));
    }

    @Test
    @DisplayName("T8 UNIT: invest odbija kada nema dovoljno sredstava na racunu")
    void invest_whenInsufficientFunds_throwsInsufficientFundsException() {
        Long fundId = 1L;
        Long clientId = 10L;
        Long sourceAccountId = 101L;
        Long fundAccountId = 201L;

        Client client = client(clientId, "Stefan", "Jovanovic");
        Currency rsd = currency(8L, "RSD");

        InvestmentFund fund = fund(fundId, fundAccountId, "Banka 2 Stable Income", new BigDecimal("1000"));
        Account sourceAccount = clientAccount(sourceAccountId, client, rsd, new BigDecimal("500.0000"));
        Account fundAccount = bankAccount(fundAccountId, rsd, AccountCategory.FUND, BigDecimal.ZERO);

        when(investmentFundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findForUpdateById(fundAccountId)).thenReturn(Optional.of(fundAccount));
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        assertThrows(
                InsufficientFundsException.class,
                () -> service.invest(
                        fundId,
                        new InvestFundDto(new BigDecimal("1000"), "RSD", sourceAccountId),
                        clientId,
                        UserRole.CLIENT
                )
        );

        assertBd("500.0000", sourceAccount.getAvailableBalance());
        assertBd("0.0000", fundAccount.getAvailableBalance());
        verify(clientFundTransactionRepository, never()).save(any(ClientFundTransaction.class));
    }

    @Test
    @DisplayName("T8 UNIT: invest odbija kada sourceAccount i fundAccount imaju isti ID")
    void invest_whenSourceAccountIsFundAccount_throwsIllegalArgumentException() {
        Long fundId = 1L;
        Long clientId = 10L;
        Long sameAccountId = 201L;

        Client client = client(clientId, "Stefan", "Jovanovic");
        Currency rsd = currency(8L, "RSD");

        InvestmentFund fund = fund(fundId, sameAccountId, "Banka 2 Stable Income", new BigDecimal("1000"));
        Account sameAccount = clientAccount(sameAccountId, client, rsd, new BigDecimal("10000.0000"));

        when(investmentFundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(sameAccountId)).thenReturn(Optional.of(sameAccount));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.invest(
                        fundId,
                        new InvestFundDto(new BigDecimal("5000"), "RSD", sameAccountId),
                        clientId,
                        UserRole.CLIENT
                )
        );

        assertTrue(ex.getMessage().contains("ne moze biti isti"));
        verify(clientFundTransactionRepository, never()).save(any(ClientFundTransaction.class));
    }

    @Test
    @DisplayName("T8 UNIT: withdraw odbija kada je trazeni iznos veci od pozicije")
    void withdraw_whenRequestedAmountGreaterThanPosition_throwsIllegalArgumentException() {
        Long fundId = 1L;
        Long clientId = 10L;
        Long fundAccountId = 201L;
        Long destinationAccountId = 101L;

        Client client = client(clientId, "Stefan", "Jovanovic");
        Currency rsd = currency(8L, "RSD");

        InvestmentFund fund = fund(fundId, fundAccountId, "Banka 2 Stable Income", new BigDecimal("1000"));
        Account fundAccount = bankAccount(fundAccountId, rsd, AccountCategory.FUND, new BigDecimal("10000.0000"));
        Account destinationAccount = clientAccount(destinationAccountId, client, rsd, BigDecimal.ZERO);

        ClientFundPosition position = position(1L, fundId, clientId, UserRole.CLIENT, new BigDecimal("3000.0000"));

        when(investmentFundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(fundAccountId)).thenReturn(Optional.of(fundAccount));
        when(accountRepository.findForUpdateById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientFundPositionRepository.findByFundIdAndUserIdAndUserRole(fundId, clientId, UserRole.CLIENT))
                .thenReturn(Optional.of(position));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.withdraw(
                        fundId,
                        new WithdrawFundDto(new BigDecimal("5000"), destinationAccountId),
                        clientId,
                        UserRole.CLIENT
                )
        );

        assertTrue(ex.getMessage().contains("veci od pozicije"));
        verify(clientFundTransactionRepository, never()).save(any(ClientFundTransaction.class));
        verify(fundLiquidationService, never()).liquidateFor(anyLong(), any());
    }

    @Test
    @DisplayName("T8 UNIT: withdraw sa null amount povlaci celu poziciju")
    void withdraw_whenAmountIsNull_redeemsEntirePosition() {
        Long fundId = 1L;
        Long clientId = 10L;
        Long fundAccountId = 201L;
        Long destinationAccountId = 101L;

        Client client = client(clientId, "Stefan", "Jovanovic");
        Currency rsd = currency(8L, "RSD");

        InvestmentFund fund = fund(fundId, fundAccountId, "Banka 2 Stable Income", new BigDecimal("1000"));
        Account fundAccount = bankAccount(fundAccountId, rsd, AccountCategory.FUND, new BigDecimal("10000.0000"));
        Account destinationAccount = clientAccount(destinationAccountId, client, rsd, new BigDecimal("1000.0000"));

        ClientFundPosition position = position(1L, fundId, clientId, UserRole.CLIENT, new BigDecimal("5000.0000"));

        when(investmentFundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(fundAccountId)).thenReturn(Optional.of(fundAccount));
        when(accountRepository.findForUpdateById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientFundPositionRepository.findByFundIdAndUserIdAndUserRole(fundId, clientId, UserRole.CLIENT))
                .thenReturn(Optional.of(position));

        ClientFundTransactionDto result = service.withdraw(
                fundId,
                new WithdrawFundDto(null, destinationAccountId),
                clientId,
                UserRole.CLIENT
        );

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertBd("5000.0000", result.getAmountRsd());

        assertBd("5000.0000", fundAccount.getAvailableBalance());
        assertBd("6000.0000", destinationAccount.getAvailableBalance());

        verify(clientFundPositionRepository).delete(position);
        verify(fundLiquidationService, never()).liquidateFor(anyLong(), any());
    }

    @Test
    @DisplayName("T8 UNIT: withdraw na devizni racun klijenta naplacuje 1% FX proviziju")
    void withdraw_clientToForeignAccount_chargesOnePercentFxFee() {
        Long fundId = 1L;
        Long clientId = 10L;
        Long fundAccountId = 201L;
        Long destinationAccountId = 101L;
        Long bankEurAccountId = 301L;

        Client client = client(clientId, "Stefan", "Jovanovic");
        Currency rsd = currency(8L, "RSD");
        Currency eur = currency(1L, "EUR");

        InvestmentFund fund = fund(fundId, fundAccountId, "Banka 2 Stable Income", new BigDecimal("1000"));
        Account fundAccount = bankAccount(fundAccountId, rsd, AccountCategory.FUND, new BigDecimal("10000.0000"));
        Account destinationAccount = clientAccount(destinationAccountId, client, eur, BigDecimal.ZERO);
        Account bankEurAccount = bankAccount(bankEurAccountId, eur, AccountCategory.BANK_TRADING, BigDecimal.ZERO);

        ClientFundPosition position = position(1L, fundId, clientId, UserRole.CLIENT, new BigDecimal("5000.0000"));

        when(investmentFundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(fundAccountId)).thenReturn(Optional.of(fundAccount));
        when(accountRepository.findForUpdateById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));
        when(accountRepository.findFirstByAccountCategoryAndCurrency_Code(AccountCategory.BANK_TRADING, "EUR"))
                .thenReturn(Optional.of(bankEurAccount));
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientFundPositionRepository.findByFundIdAndUserIdAndUserRole(fundId, clientId, UserRole.CLIENT))
                .thenReturn(Optional.of(position));
        when(currencyConversionService.convert(new BigDecimal("5000.0000"), "RSD", "EUR"))
                .thenReturn(new BigDecimal("42.5000"));

        ClientFundTransactionDto result = service.withdraw(
                fundId,
                new WithdrawFundDto(new BigDecimal("5000"), destinationAccountId),
                clientId,
                UserRole.CLIENT
        );

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertBd("5000.0000", result.getAmountRsd());

        assertBd("5000.0000", fundAccount.getAvailableBalance());
        assertBd("42.0750", destinationAccount.getAvailableBalance());
        assertBd("0.4250", bankEurAccount.getAvailableBalance());
    }

    @Test
    @DisplayName("T8 UNIT: supervizor withdraw na devizni bankin racun ne placa FX proviziju")
    void withdraw_supervisorToForeignBankAccount_chargesZeroFxFee() {
        Long fundId = 1L;
        Long supervisorId = 5L;
        Long bankClientId = 50L;
        Long fundAccountId = 201L;
        Long destinationAccountId = 102L;

        Currency rsd = currency(8L, "RSD");
        Currency eur = currency(1L, "EUR");

        Client bankClient = client(bankClientId, "Banka 2", "d.o.o.");

        InvestmentFund fund = fund(fundId, fundAccountId, "Banka 2 Stable Income", new BigDecimal("1000"));
        Account fundAccount = bankAccount(fundAccountId, rsd, AccountCategory.FUND, new BigDecimal("10000.0000"));
        Account destinationAccount = bankAccount(destinationAccountId, eur, AccountCategory.BANK_TRADING, BigDecimal.ZERO);

        ClientFundPosition position = position(1L, fundId, bankClientId, UserRole.CLIENT, new BigDecimal("5000.0000"));

        when(investmentFundRepository.findById(fundId)).thenReturn(Optional.of(fund));
        when(accountRepository.findForUpdateById(fundAccountId)).thenReturn(Optional.of(fundAccount));
        when(accountRepository.findForUpdateById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));
        when(actuaryInfoRepository.findByEmployeeId(supervisorId)).thenReturn(Optional.of(supervisorInfo()));
        when(clientRepository.findByEmail("banka2.doo@banka.rs")).thenReturn(Optional.of(bankClient));
        when(clientFundPositionRepository.findByFundIdAndUserIdAndUserRole(fundId, bankClientId, UserRole.CLIENT))
                .thenReturn(Optional.of(position));
        when(currencyConversionService.convert(new BigDecimal("5000.0000"), "RSD", "EUR"))
                .thenReturn(new BigDecimal("42.5000"));

        ClientFundTransactionDto result = service.withdraw(
                fundId,
                new WithdrawFundDto(new BigDecimal("5000"), destinationAccountId),
                supervisorId,
                UserRole.EMPLOYEE
        );

        assertNotNull(result);
        assertEquals("COMPLETED", result.getStatus());
        assertBd("5000.0000", result.getAmountRsd());

        assertBd("5000.0000", fundAccount.getAvailableBalance());
        assertBd("42.5000", destinationAccount.getAvailableBalance());

        verify(accountRepository, never())
                .findFirstByAccountCategoryAndCurrency_Code(AccountCategory.BANK_TRADING, "EUR");
    }

    private InvestmentFund fund(Long id, Long accountId, String name, BigDecimal minimumContribution) {
        InvestmentFund fund = new InvestmentFund();
        fund.setId(id);
        fund.setName(name);
        fund.setDescription("Test fond");
        fund.setMinimumContribution(minimumContribution);
        fund.setManagerEmployeeId(5L);
        fund.setAccountId(accountId);
        fund.setCreatedAt(LocalDateTime.now());
        fund.setActive(true);
        return fund;
    }

    private Currency currency(Long id, String code) {
        return Currency.builder()
                .id(id)
                .code(code)
                .name(code)
                .symbol(code)
                .country("Test")
                .active(true)
                .build();
    }

    private Client client(Long id, String firstName, String lastName) {
        Client client = new Client();
        client.setId(id);
        client.setFirstName(firstName);
        client.setLastName(lastName);
        client.setEmail(firstName.toLowerCase() + "@test.rs");
        client.setActive(true);
        return client;
    }

    private Company company() {
        return Company.builder()
                .id(1L)
                .name("Banka 2")
                .registrationNumber("22200022")
                .taxNumber("222000222")
                .address("Beograd")
                .active(true)
                .build();
    }

    private Account clientAccount(Long id, Client client, Currency currency, BigDecimal amount) {
        return Account.builder()
                .id(id)
                .accountNumber("2220001123456789" + id)
                .accountType(AccountType.CHECKING)
                .accountSubtype(AccountSubtype.STANDARD)
                .currency(currency)
                .client(client)
                .balance(amount)
                .availableBalance(amount)
                .reservedAmount(BigDecimal.ZERO)
                .accountCategory(AccountCategory.CLIENT)
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Account bankAccount(Long id, Currency currency, AccountCategory category, BigDecimal amount) {
        return Account.builder()
                .id(id)
                .accountNumber("222000100000000" + id)
                .accountType(AccountType.BUSINESS)
                .accountSubtype(AccountSubtype.STANDARD)
                .currency(currency)
                .company(company())
                .balance(amount)
                .availableBalance(amount)
                .reservedAmount(BigDecimal.ZERO)
                .accountCategory(category)
                .status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ActuaryInfo supervisorInfo() {
        ActuaryInfo info = new ActuaryInfo();
        info.setId(1L);
        info.setActuaryType(ActuaryType.SUPERVISOR);
        info.setNeedApproval(false);
        return info;
    }

    private ClientFundPosition position(Long id, Long fundId, Long userId, String userRole, BigDecimal totalInvested) {
        ClientFundPosition position = new ClientFundPosition();
        position.setId(id);
        position.setFundId(fundId);
        position.setUserId(userId);
        position.setUserRole(userRole);
        position.setTotalInvested(totalInvested);
        position.setLastModifiedAt(LocalDateTime.now());
        return position;
    }

    private void assertBd(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}