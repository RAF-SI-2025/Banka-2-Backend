package rs.raf.banka2_bek.transaction.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.transaction.dto.TransactionListItemDto;
import rs.raf.banka2_bek.transaction.dto.TransactionResponseDto;
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
    private final UserRepository userRepository;


    @Override
    public List<TransactionResponseDto> recordPaymentSettlement(Payment payment, Account toAccount, User initiatedBy) {
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
                .credit(amount)
                .balanceAfter(orZero(toAccount.getBalance()))
                .availableAfter(orZero(toAccount.getAvailableBalance()))
                .build();

        return transactionRepository.saveAll(List.of(debitTx, creditTx)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public Page<TransactionListItemDto> getTransactions(Pageable pageable) {
        User currentUser = getAuthenticatedUser();
        return transactionRepository.findByAccountUserId(currentUser.getId(), pageable)
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
        User currentUser = getAuthenticatedUser();
        return transactionRepository.findPaymentTransactionsByAccountUserIdAndFilters(
                        currentUser.getId(),
                        fromDate,
                        toDate,
                        minAmount,
                        maxAmount,
                        type,
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

    private TransactionResponseDto toResponse(Transaction transaction) {
        TransactionType type = transaction.getPayment() != null ? TransactionType.PAYMENT : TransactionType.TRANSFER;

        return TransactionResponseDto.builder()
                .id(transaction.getId())
                .type(type)
                .accountNumber(transaction.getAccount() != null ? transaction.getAccount().getAccountNumber() : null)
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
                .debit(transaction.getDebit())
                .credit(transaction.getCredit())
                .createdAt(transaction.getCreatedAt())
                .build();
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new IllegalArgumentException("Authenticated user does not exist."));
        }

        throw new IllegalArgumentException("Authenticated user is required.");
    }

    private BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal resolveAmount(Transaction transaction) {
        BigDecimal debit = orZero(transaction.getDebit());
        return debit.compareTo(BigDecimal.ZERO) > 0 ? debit : orZero(transaction.getCredit());
    }
}
