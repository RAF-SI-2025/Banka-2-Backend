package rs.raf.banka2_bek.transaction.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.transaction.dto.TransactionListItemDto;
import rs.raf.banka2_bek.transaction.dto.TransactionResponseDto;
import rs.raf.banka2_bek.transaction.dto.TransactionDirection;
import rs.raf.banka2_bek.transaction.dto.TransactionType;
import rs.raf.banka2_bek.transaction.model.Transaction;
import rs.raf.banka2_bek.transaction.repository.TransactionRepository;
import rs.raf.banka2_bek.transaction.service.TransactionService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {
    private final TransactionRepository transactionRepository;
    private final ClientRepository clientRepository;


    @Override
    public List<TransactionResponseDto> recordPaymentSettlement(
            Payment payment,
            Account toAccount,
            Client initiatedBy,
            BigDecimal creditedAmount
    ) {
        Account fromAccount = payment.getFromAccount();
        BigDecimal amount = payment.getAmount();

        Transaction debitTx = Transaction.builder()
                .account(fromAccount)
                .currency(fromAccount.getCurrency())
                .client(initiatedBy)
                .payment(payment)
                .description(payment.getPurpose())
                .debit(amount)
                .credit(BigDecimal.ZERO)
                .balanceAfter(orZero(fromAccount.getBalance()))
                .availableAfter(orZero(fromAccount.getAvailableBalance()))
                .build();

        Transaction creditTx = Transaction.builder()
                .account(toAccount)
                .currency(toAccount.getCurrency())
                .client(initiatedBy)
                .payment(payment)
                .description(payment.getPurpose())
                .debit(BigDecimal.ZERO)
                .credit(creditedAmount)
                .balanceAfter(orZero(toAccount.getBalance()))
                .availableAfter(orZero(toAccount.getAvailableBalance()))
                .build();

        return transactionRepository.saveAll(List.of(debitTx, creditTx)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<TransactionResponseDto> recordCrossCurrencyPaymentSettlement(
            Payment payment,
            Account toAccount,
            Account bankFromAccount,
            Account bankToAccount,
            Client initiatedBy,
            BigDecimal totalFromClient,
            BigDecimal creditedAmount
    ) {
        Account fromAccount = payment.getFromAccount();

        // Faza 1: Klijent {fromCurrency} → Bank {fromCurrency} (amount + provizija)
        Transaction step1Debit = Transaction.builder()
                .account(fromAccount)
                .currency(fromAccount.getCurrency())
                .client(initiatedBy)
                .payment(payment)
                .description((payment.getPurpose() != null ? payment.getPurpose() : "") + " [faza 1: klijent → banka]")
                .debit(totalFromClient)
                .credit(BigDecimal.ZERO)
                .balanceAfter(orZero(fromAccount.getBalance()))
                .availableAfter(orZero(fromAccount.getAvailableBalance()))
                .build();
        Transaction step1Credit = Transaction.builder()
                .account(bankFromAccount)
                .currency(bankFromAccount.getCurrency())
                .client(initiatedBy)
                .payment(payment)
                .description("FX inflow " + fromAccount.getCurrency().getCode() + " [faza 1: klijent → banka]")
                .debit(BigDecimal.ZERO)
                .credit(totalFromClient)
                .balanceAfter(orZero(bankFromAccount.getBalance()))
                .availableAfter(orZero(bankFromAccount.getAvailableBalance()))
                .build();

        // Faza 2: Bank {fromCurrency} → Bank {toCurrency} (interna konverzija)
        Transaction step2Debit = Transaction.builder()
                .account(bankFromAccount)
                .currency(bankFromAccount.getCurrency())
                .client(initiatedBy)
                .payment(payment)
                .description("FX swap " + fromAccount.getCurrency().getCode() + "→" + toAccount.getCurrency().getCode() + " [faza 2: interna konverzija]")
                .debit(payment.getAmount())
                .credit(BigDecimal.ZERO)
                .balanceAfter(orZero(bankFromAccount.getBalance()))
                .availableAfter(orZero(bankFromAccount.getAvailableBalance()))
                .build();
        Transaction step2Credit = Transaction.builder()
                .account(bankToAccount)
                .currency(bankToAccount.getCurrency())
                .client(initiatedBy)
                .payment(payment)
                .description("FX swap " + fromAccount.getCurrency().getCode() + "→" + toAccount.getCurrency().getCode() + " [faza 2: interna konverzija]")
                .debit(BigDecimal.ZERO)
                .credit(creditedAmount)
                .balanceAfter(orZero(bankToAccount.getBalance()))
                .availableAfter(orZero(bankToAccount.getAvailableBalance()))
                .build();

        // Faza 3: Bank {toCurrency} → Primalac {toCurrency}
        Transaction step3Debit = Transaction.builder()
                .account(bankToAccount)
                .currency(bankToAccount.getCurrency())
                .client(initiatedBy)
                .payment(payment)
                .description("FX outflow " + toAccount.getCurrency().getCode() + " [faza 3: banka → primalac]")
                .debit(creditedAmount)
                .credit(BigDecimal.ZERO)
                .balanceAfter(orZero(bankToAccount.getBalance()))
                .availableAfter(orZero(bankToAccount.getAvailableBalance()))
                .build();
        Transaction step3Credit = Transaction.builder()
                .account(toAccount)
                .currency(toAccount.getCurrency())
                .client(initiatedBy)
                .payment(payment)
                .description((payment.getPurpose() != null ? payment.getPurpose() : "") + " [faza 3: banka → primalac]")
                .debit(BigDecimal.ZERO)
                .credit(creditedAmount)
                .balanceAfter(orZero(toAccount.getBalance()))
                .availableAfter(orZero(toAccount.getAvailableBalance()))
                .build();

        return transactionRepository.saveAll(
                List.of(step1Debit, step1Credit, step2Debit, step2Credit, step3Debit, step3Credit)
        ).stream().map(this::toResponse).toList();
    }

    @Override
    public Page<TransactionListItemDto> getTransactions(Pageable pageable) {
        Client currentUser = getAuthenticatedUser();
        return transactionRepository.findByAccountClientId(currentUser.getId(), pageable)
                .map(this::toListItem);
    }

    @Override
    public Page<TransactionListItemDto> getTransactions(
            Pageable pageable,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            TransactionType type
    ) {
        Client currentUser = getAuthenticatedUser();
        return transactionRepository.findTransactionsByAccountUserIdAndFilters(
                        currentUser.getId(),
                        fromDate,
                        toDate,
                        minAmount,
                        maxAmount,
                        type == null ? null : type.name(),
                        pageable
                )
                .map(this::toListItem);
    }

    @Override
    public TransactionResponseDto getTransactionById(Long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found."));
        return toResponse(transaction);
    }

    @Override
    public TransactionResponseDto getReceiptTransaction(Long transactionId, Long clientId) {
        Transaction transaction = transactionRepository.findReceiptTransactionForClient(transactionId, clientId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Transaction with ID " + transactionId + " not found for authenticated client."
                ));
        return toResponse(transaction);
    }

    private TransactionResponseDto toResponse(Transaction transaction) {
        TransactionType type = transaction.getPayment() != null ? TransactionType.PAYMENT : TransactionType.TRANSFER;

        return TransactionResponseDto.builder()
                .id(transaction.getId())
                .type(type)
                .accountNumber(transaction.getAccount() != null ? transaction.getAccount().getAccountNumber() : null)
                .toAccountNumber(resolveToAccountNumber(transaction))
                .currencyCode(transaction.getCurrency() != null ? transaction.getCurrency().getCode() : null)
                .description(transaction.getDescription())
                .debit(transaction.getDebit())
                .credit(transaction.getCredit())
                .reserved(transaction.getReserved())
                .reservedUsed(transaction.getReservedUsed())
                .balanceAfter(transaction.getBalanceAfter())
                .availableAfter(transaction.getAvailableAfter())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private TransactionListItemDto toListItem(Transaction transaction) {
        return TransactionListItemDto.builder()
                .id(transaction.getId())
                .accountNumber(transaction.getAccount() != null ? transaction.getAccount().getAccountNumber() : null)
                .type(transaction.getPayment() != null ? TransactionType.PAYMENT : TransactionType.TRANSFER)
                .direction(resolveDirection(transaction))
                .debit(transaction.getDebit())
                .credit(transaction.getCredit())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private TransactionDirection resolveDirection(Transaction transaction) {
        BigDecimal debit = orZero(transaction.getDebit());
        return debit.compareTo(BigDecimal.ZERO) > 0
                ? TransactionDirection.OUTGOING
                : TransactionDirection.INCOMING;
    }

    private Client getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return clientRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new IllegalArgumentException("Authenticated user does not exist."));
        }

        throw new IllegalArgumentException("Authenticated user is required.");
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String resolveToAccountNumber(Transaction transaction) {
        if (transaction.getPayment() != null) {
            return transaction.getPayment().getToAccountNumber();
        }
        if (transaction.getTransfer() != null && transaction.getTransfer().getToAccount() != null) {
            return transaction.getTransfer().getToAccount().getAccountNumber();
        }
        return null;
    }
}
