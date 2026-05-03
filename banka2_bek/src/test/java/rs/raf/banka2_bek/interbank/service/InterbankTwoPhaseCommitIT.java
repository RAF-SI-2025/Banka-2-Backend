package rs.raf.banka2_bek.interbank.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import rs.raf.banka2_bek.IntegrationTestCleanup;
import rs.raf.banka2_bek.TestObjectMapperConfig;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.interbank.model.InterbankMessage;
import rs.raf.banka2_bek.interbank.model.InterbankMessageStatus;
import rs.raf.banka2_bek.interbank.model.InterbankTransaction;
import rs.raf.banka2_bek.interbank.model.InterbankTransactionStatus;
import rs.raf.banka2_bek.interbank.protocol.*;
import rs.raf.banka2_bek.interbank.repository.InterbankMessageRepository;
import rs.raf.banka2_bek.interbank.repository.InterbankTransactionRepository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * §2.8.5 Integration test: 2PC flow with real @Transactional boundaries and H2.
 * InterbankClient is mocked to simulate partner bank responses.
 */
@Import(TestObjectMapperConfig.class)
@SpringBootTest
@ActiveProfiles("test")
class InterbankTwoPhaseCommitIT {

    private static final int MY_RN     = 222;
    private static final int REMOTE_RN = 999;

    @MockitoBean
    private InterbankClient interbankClient;

    @Autowired private TransactionExecutorService transactionExecutorService;
    @Autowired private AccountRepository          accountRepository;
    @Autowired private ClientRepository           clientRepository;
    @Autowired private EmployeeRepository         employeeRepository;
    @Autowired private CurrencyRepository         currencyRepository;
    @Autowired private InterbankTransactionRepository txRepo;
    @Autowired private InterbankMessageRepository    messageRepo;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource   dataSource;

    @BeforeEach
    void resetDatabase() {
        IntegrationTestCleanup.truncateAllTables(dataSource);
    }

    // =========================================================================
    // Test 1 — Local-only transfer
    // =========================================================================

    @Test
    @DisplayName("Local-only: balanced tx → commit, sender balance debited, receiver credited")
    void localOnly_balancedTx_balancesTransferCorrectly() {
        Client owner = seedClient("it-local@test.com");
        Employee emp = seedEmployee("emp-local@test.com", "emp-local");
        Currency rsd = seedCurrency("RSD");

        String senderNum   = "222000000000001111";
        String receiverNum = "222000000000002222";
        seedAccount(senderNum,   owner, emp, rsd, new BigDecimal("500.00"));
        seedAccount(receiverNum, owner, emp, rsd, BigDecimal.ZERO);

        // amount > 0 = debit (account receives), amount < 0 = credit (account gives)
        Transaction tx = transactionExecutorService.formTransaction(List.of(
                new Posting(new TxAccount.Account(receiverNum), BigDecimal.valueOf(100),
                        new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD))),
                new Posting(new TxAccount.Account(senderNum),   BigDecimal.valueOf(-100),
                        new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD)))
        ), "IT local transfer", null, "289", "test");

        transactionExecutorService.execute(tx);

        Account sender   = accountRepository.findByAccountNumber(senderNum).orElseThrow();
        Account receiver = accountRepository.findByAccountNumber(receiverNum).orElseThrow();

        assertThat(sender.getBalance()).isEqualByComparingTo("400.00");
        assertThat(sender.getAvailableBalance()).isEqualByComparingTo("400.00");
        assertThat(sender.getReservedAmount()).isEqualByComparingTo("0.00");

        assertThat(receiver.getBalance()).isEqualByComparingTo("100.00");
        assertThat(receiver.getAvailableBalance()).isEqualByComparingTo("100.00");

        InterbankTransaction ibTx = txRepo.findByTransactionRoutingNumberAndTransactionIdString(
                tx.transactionId().routingNumber(), tx.transactionId().id()).orElseThrow();
        assertThat(ibTx.getStatus()).isEqualTo(InterbankTransactionStatus.COMMITTED);

        verifyNoInteractions(interbankClient);
    }

    // =========================================================================
    // Test 2 — Cross-bank: remote bank votes YES → commit
    // =========================================================================

    @Test
    @DisplayName("Cross-bank: remote YES → COMMIT_TX sent, local balance debited after commit")
    void crossBank_remoteVotesYes_localBalanceDebited() {
        Client owner = seedClient("it-yes@test.com");
        Employee emp = seedEmployee("emp-yes@test.com", "emp-yes");
        Currency rsd = seedCurrency("RSD");

        String localNum  = "222000000000003333";
        String remoteNum = "999000000000004444";
        seedAccount(localNum, owner, emp, rsd, new BigDecimal("500.00"));

        Transaction tx = transactionExecutorService.formTransaction(List.of(
                new Posting(new TxAccount.Account(remoteNum), BigDecimal.valueOf(100),
                        new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD))),
                new Posting(new TxAccount.Account(localNum),  BigDecimal.valueOf(-100),
                        new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD)))
        ), "IT cross-bank YES", null, "289", "test");

        when(interbankClient.sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class)))
                .thenReturn(new TransactionVote(TransactionVote.Vote.YES, List.of()));
        when(interbankClient.sendMessage(eq(REMOTE_RN), eq(MessageType.COMMIT_TX), any(), eq(Void.class)))
                .thenReturn(null);

        transactionExecutorService.execute(tx);

        Account local = accountRepository.findByAccountNumber(localNum).orElseThrow();
        assertThat(local.getBalance()).isEqualByComparingTo("400.00");
        assertThat(local.getAvailableBalance()).isEqualByComparingTo("400.00");
        assertThat(local.getReservedAmount()).isEqualByComparingTo("0.00");

        InterbankTransaction ibTx = txRepo.findByTransactionRoutingNumberAndTransactionIdString(
                tx.transactionId().routingNumber(), tx.transactionId().id()).orElseThrow();
        assertThat(ibTx.getStatus()).isEqualTo(InterbankTransactionStatus.COMMITTED);

        verify(interbankClient).sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class));
        verify(interbankClient).sendMessage(eq(REMOTE_RN), eq(MessageType.COMMIT_TX), any(), eq(Void.class));
        verify(interbankClient, never()).sendMessage(any(Integer.class), eq(MessageType.ROLLBACK_TX), any(), any());

        List<InterbankMessage> messages = messageRepo.findAll();
        assertThat(messages).hasSize(2);
        assertThat(messages).anySatisfy(m -> {
            assertThat(m.getMessageType()).isEqualTo(MessageType.NEW_TX);
            assertThat(m.getStatus()).isEqualTo(InterbankMessageStatus.SENT);
        });
        assertThat(messages).anySatisfy(m -> {
            assertThat(m.getMessageType()).isEqualTo(MessageType.COMMIT_TX);
            assertThat(m.getStatus()).isEqualTo(InterbankMessageStatus.SENT);
        });
    }

    // =========================================================================
    // Test 3 — Cross-bank: remote bank votes NO → rollback
    // =========================================================================

    @Test
    @DisplayName("Cross-bank: remote NO → ROLLBACK_TX sent, local balance fully restored")
    void crossBank_remoteVotesNo_localBalanceRestored() {
        Client owner = seedClient("it-no@test.com");
        Employee emp = seedEmployee("emp-no@test.com", "emp-no");
        Currency rsd = seedCurrency("RSD");

        String localNum  = "222000000000005555";
        String remoteNum = "999000000000006666";
        seedAccount(localNum, owner, emp, rsd, new BigDecimal("500.00"));

        Transaction tx = transactionExecutorService.formTransaction(List.of(
                new Posting(new TxAccount.Account(remoteNum), BigDecimal.valueOf(100),
                        new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD))),
                new Posting(new TxAccount.Account(localNum),  BigDecimal.valueOf(-100),
                        new Asset.Monas(new MonetaryAsset(CurrencyCode.RSD)))
        ), "IT cross-bank NO", null, "289", "test");

        when(interbankClient.sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class)))
                .thenReturn(new TransactionVote(TransactionVote.Vote.NO, List.of()));
        when(interbankClient.sendMessage(eq(REMOTE_RN), eq(MessageType.ROLLBACK_TX), any(), eq(Void.class)))
                .thenReturn(null);

        transactionExecutorService.execute(tx);

        Account local = accountRepository.findByAccountNumber(localNum).orElseThrow();
        assertThat(local.getBalance()).isEqualByComparingTo("500.00");
        assertThat(local.getAvailableBalance()).isEqualByComparingTo("500.00");
        assertThat(local.getReservedAmount()).isEqualByComparingTo("0.00");

        InterbankTransaction ibTx = txRepo.findByTransactionRoutingNumberAndTransactionIdString(
                tx.transactionId().routingNumber(), tx.transactionId().id()).orElseThrow();
        assertThat(ibTx.getStatus()).isEqualTo(InterbankTransactionStatus.ROLLED_BACK);

        verify(interbankClient).sendMessage(eq(REMOTE_RN), eq(MessageType.NEW_TX), any(), eq(TransactionVote.class));
        verify(interbankClient).sendMessage(eq(REMOTE_RN), eq(MessageType.ROLLBACK_TX), any(), eq(Void.class));
        verify(interbankClient, never()).sendMessage(any(Integer.class), eq(MessageType.COMMIT_TX), any(), any());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Client seedClient(String email) {
        Client c = new Client();
        c.setFirstName("Test");
        c.setLastName("IT");
        c.setDateOfBirth(LocalDate.of(1990, 1, 1));
        c.setGender("M");
        c.setEmail(email);
        c.setPhone("+381600000001");
        c.setAddress("Test Address");
        c.setPassword("x");
        c.setSaltPassword("salt");
        c.setActive(true);
        return clientRepository.save(c);
    }

    private Employee seedEmployee(String email, String username) {
        return employeeRepository.save(Employee.builder()
                .firstName("Emp").lastName("IT")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender("M").email(email).phone("+381600000000")
                .address("Test").username(username)
                .password("x").saltPassword("salt")
                .position("QA").department("IT")
                .active(true).permissions(Set.of())
                .build());
    }

    private Currency seedCurrency(String code) {
        Long id;
        List<Long> ids = jdbcTemplate.query(
                "select id from currencies where code = ?",
                (rs, i) -> rs.getLong(1), code);
        if (ids.isEmpty()) {
            jdbcTemplate.update(
                    "insert into currencies(code, name, symbol, country, description, active) values (?,?,?,?,?,?)",
                    code, code + " currency", code.charAt(0) + "", "RS", "test", true);
            id = jdbcTemplate.queryForObject("select id from currencies where code = ?", Long.class, code);
        } else {
            id = ids.get(0);
        }
        return currencyRepository.findById(id).orElseThrow();
    }

    private void seedAccount(String number, Client owner, Employee emp,
                              Currency currency, BigDecimal balance) {
        accountRepository.save(Account.builder()
                .accountNumber(number)
                .accountType(AccountType.CHECKING)
                .currency(currency)
                .client(owner)
                .employee(emp)
                .status(AccountStatus.ACTIVE)
                .balance(balance)
                .availableBalance(balance)
                .reservedAmount(BigDecimal.ZERO)
                .dailyLimit(new BigDecimal("999999999.00"))
                .monthlyLimit(new BigDecimal("999999999.00"))
                .dailySpending(BigDecimal.ZERO)
                .monthlySpending(BigDecimal.ZERO)
                .build());
    }
}
