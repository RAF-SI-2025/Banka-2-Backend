package rs.raf.banka2_bek.payment.service.implementation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentListItemDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.payment.repository.AccountRepository;
import rs.raf.banka2_bek.payment.repository.PaymentRepository;
import rs.raf.banka2_bek.payment.service.PaymentService;
import rs.raf.banka2_bek.transaction.dto.TransactionListItemDto;
import rs.raf.banka2_bek.transaction.dto.TransactionType;
import rs.raf.banka2_bek.transaction.service.TransactionService;
import rs.raf.banka2_bek.transfer.model.TransferType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    //TODO: zameniti import
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final TransactionService transactionService;
    private static final int ORDER_NUMBER_MAX_RETRIES = 5;

    //TODO: skloniti ovo kada se uvede pravi FX servis
    private static final Set<String> SUPPORTED = Set.of(
            "EUR", "CHF", "USD", "GBP", "JPY", "CAD", "AUD", "RSD"
    );

    // 1 unit of key currency = value in RSD
    private static final Map<String, BigDecimal> TO_RSD = Map.of(
            "RSD", new BigDecimal("1.0000"),
            "EUR", new BigDecimal("117.2000"),
            "CHF", new BigDecimal("122.8000"),
            "USD", new BigDecimal("108.5000"),
            "GBP", new BigDecimal("137.4000"),
            "JPY", new BigDecimal("0.7300"),
            "CAD", new BigDecimal("80.1000"),
            "AUD", new BigDecimal("71.6000")
    );

    //Trenutno podrzava samo placanja u okviru iste banke
    @Override
    @Transactional
    public PaymentResponseDto createPayment(CreatePaymentRequestDto request) {
        Account fromAccount = accountRepository.findForUpdateByAccountNumber(request.getFromAccount())
                .orElseThrow(() -> new IllegalArgumentException("Source account does not exist."));

        Account toAccount = accountRepository.findForUpdateByAccountNumber(request.getToAccount())
                .orElseThrow(() -> new IllegalArgumentException("Destination account does not exist."));

        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Source account is not active.");
        }

        if (toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Destination account is not active.");
        }

        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new IllegalArgumentException("Source and destination accounts must be different.");
        }

        User client = getAuthenticatedClient();

        if (fromAccount.getClient() == null || !fromAccount.getClient().getId().equals(client.getId())) {
            throw new IllegalArgumentException("Source account does not belong to the authenticated client.");
        }

        BigDecimal amount = request.getAmount();

        if (fromAccount.getDailyLimit() == null
                || fromAccount.getDailySpending().add(amount).compareTo(fromAccount.getDailyLimit()) > 0) {
            throw new IllegalArgumentException("Daily transfer limit exceeded for the source account.");
        }

        if (fromAccount.getMonthlyLimit() == null
                || fromAccount.getMonthlySpending().add(amount).compareTo(fromAccount.getMonthlyLimit()) > 0) {
            throw new IllegalArgumentException("Monthly transfer limit exceeded for the source account.");
        }

        if (fromAccount.getAvailableBalance() == null || fromAccount.getAvailableBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient funds in the source account.");
        }

        BigDecimal transactionFee = BigDecimal.ZERO;
        BigDecimal exRate = BigDecimal.ONE;

        if (!fromAccount.getCurrency().getId().equals(toAccount.getCurrency().getId())) {
            transactionFee = amount.multiply(new BigDecimal("0.005"));
            exRate = getFxRate(fromAccount.getCurrency().getCode(), toAccount.getCurrency().getCode());
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(amount.add(transactionFee)));
        fromAccount.setAvailableBalance(fromAccount.getAvailableBalance().subtract(amount.add(transactionFee)));
        fromAccount.setDailySpending(fromAccount.getDailySpending().add(amount));
        fromAccount.setMonthlySpending(fromAccount.getMonthlySpending().add(amount));

        toAccount.setBalance(toAccount.getBalance().add(amount.multiply(exRate)));
        toAccount.setAvailableBalance(toAccount.getAvailableBalance().add(amount.multiply(exRate)));

        Payment base = Payment.builder()
                .fromAccount(fromAccount)
                .toAccountNumber(request.getToAccount())
                .amount(amount)
                .fee(transactionFee)
                .currency(fromAccount.getCurrency())
                .paymentCode(request.getPaymentCode())
                .referenceNumber(request.getReferenceNumber())
                .purpose(request.getDescription())
                .status(PaymentStatus.COMPLETED)
                .createdBy(client)
                .build();

        Payment savedPayment = null;

        for (int attempt = 1; attempt <= ORDER_NUMBER_MAX_RETRIES; attempt++) {
            try {
                base.setOrderNumber(generateOrderNumber());
                savedPayment = paymentRepository.saveAndFlush(base); // force DB unique check now
                break;
            } catch (DataIntegrityViolationException ex) {
                String msg = ex.getMostSpecificCause().getMessage();
                if (msg == null) throw ex;

                String lower = msg.toLowerCase();
                if (!(lower.contains("order_number") || lower.contains("uk") || lower.contains("unique")))
                    throw ex;
            }
        }

        if (savedPayment == null) {
            throw new IllegalStateException("Failed to generate unique order number.");
        }

        transactionService.recordPaymentSettlement(savedPayment, toAccount, client);
        return toResponse(savedPayment);
    }

    @Override
    public Page<PaymentListItemDto> getPayments(
            Pageable pageable,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            PaymentStatus status
    ) {
        User client = getAuthenticatedClient();
        return paymentRepository.findByUserAccountsWithFilters(
                        client.getId(),
                        fromDate,
                        toDate,
                        minAmount,
                        maxAmount,
                        status,
                        pageable
                )
                .map(this::toListItem);
    }

    @Override
    public PaymentResponseDto getPaymentById(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment with ID " + paymentId + " not found."));
        return toResponse(payment);
    }

    @Override
    public Page<TransactionListItemDto> getPaymentHistory(
            Pageable pageable,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            TransactionType type
    ) {
        return transactionService.getTransactions(pageable, fromDate, toDate, minAmount, maxAmount, type);
               // .map(this::toPaymentHistoryItem);
    }

    private BigDecimal getFxRate(String from, String to) {
        String f = from.toUpperCase();
        String t = to.toUpperCase();

        if (!SUPPORTED.contains(f) || !SUPPORTED.contains(t)) {
            throw new IllegalArgumentException("Unsupported currency pair: " + from + "/" + to);
        }
        if (f.equals(t)) return BigDecimal.ONE;

        BigDecimal fromToRsd = TO_RSD.get(f);
        BigDecimal toToRsd = TO_RSD.get(t);

        // (from -> RSD) / (to -> RSD) = from -> to
        return fromToRsd.divide(toToRsd, 10, RoundingMode.HALF_UP);
    }


    private PaymentResponseDto toResponse(Payment payment) {
        return PaymentResponseDto.builder()
                .id(payment.getId())
                .orderNumber(payment.getOrderNumber())
                .fromAccount(payment.getFromAccount() != null ? payment.getFromAccount().getAccountNumber() : null)
                .toAccount(payment.getToAccountNumber())
                .amount(payment.getAmount())
                .fee(payment.getFee())
                .paymentCode(payment.getPaymentCode())
                .referenceNumber(payment.getReferenceNumber())
                .description(payment.getPurpose())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private PaymentListItemDto toListItem(Payment payment) {
        return PaymentListItemDto.builder()
                .id(payment.getId())
                .orderNumber(payment.getOrderNumber())
                .fromAccount(payment.getFromAccount() != null ? payment.getFromAccount().getAccountNumber() : null)
                .toAccount(payment.getToAccountNumber())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    /*private PaymentListItemDto toPaymentHistoryItem(TransactionListItemDto transaction) {
        return PaymentListItemDto.builder()
                .id(transaction.getPaymentId() != null ? transaction.getPaymentId() : transaction.getId())
                .orderNumber(transaction.getOrderNumber())
                .fromAccount(transaction.getFromAccount())
                .toAccount(transaction.getToAccount())
                .amount(transaction.getAmount() != null ? transaction.getAmount() : resolveAmount(transaction))
                .status(transaction.getStatus())
                .createdAt(transaction.getCreatedAt())
                .build();
    } */

    private String getAuthenticatedUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }

        throw new IllegalArgumentException("Authenticated user is required.");
    }

    private User getAuthenticatedClient() {
        String username = getAuthenticatedUsername();
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated client does not exist."));
    }

    private String generateOrderNumber() {
        return "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private BigDecimal resolveAmount(TransactionListItemDto transaction) {
        BigDecimal debit = transaction.getDebit() == null ? BigDecimal.ZERO : transaction.getDebit();
        return debit.compareTo(BigDecimal.ZERO) > 0
                ? debit
                : (transaction.getCredit() == null ? BigDecimal.ZERO : transaction.getCredit());
    }
}

