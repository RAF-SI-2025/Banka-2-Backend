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
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.exchange.ExchangeService;
import rs.raf.banka2_bek.exchange.dto.ExchangeRateDto;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.payment.dto.CreatePaymentRequestDto;
import rs.raf.banka2_bek.payment.dto.PaymentDirection;
import rs.raf.banka2_bek.payment.dto.PaymentListItemDto;
import rs.raf.banka2_bek.payment.dto.PaymentResponseDto;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.payment.repository.PaymentAccountRepository;
import rs.raf.banka2_bek.payment.repository.PaymentRepository;
import rs.raf.banka2_bek.payment.service.PaymentReceiptPdfGenerator;
import rs.raf.banka2_bek.payment.service.PaymentService;
import rs.raf.banka2_bek.transaction.dto.TransactionListItemDto;
import rs.raf.banka2_bek.transaction.dto.TransactionResponseDto;
import rs.raf.banka2_bek.transaction.dto.TransactionType;
import rs.raf.banka2_bek.transaction.service.TransactionService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAccountRepository paymentAccountRepository;
    private final ClientRepository clientRepository;
    private final TransactionService transactionService;
    private final PaymentReceiptPdfGenerator paymentReceiptPdfGenerator;
    private final ExchangeService exchangeService;
    private static final int ORDER_NUMBER_MAX_RETRIES = 5;

    @Override
    @Transactional
    public PaymentResponseDto createPayment(CreatePaymentRequestDto request) {
        Account fromAccount = paymentAccountRepository.findForUpdateByAccountNumber(request.getFromAccount())
                .orElseThrow(() -> new IllegalArgumentException("Source account does not exist."));

        Account toAccount = paymentAccountRepository.findForUpdateByAccountNumber(request.getToAccount())
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

        Client client = getAuthenticatedClient();

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

        BigDecimal creditedAmount = amount.multiply(exRate);
        String paymentCode = request.getPaymentCode().getCode();

        fromAccount.setBalance(fromAccount.getBalance().subtract(amount.add(transactionFee)));
        fromAccount.setAvailableBalance(fromAccount.getAvailableBalance().subtract(amount.add(transactionFee)));
        fromAccount.setDailySpending(fromAccount.getDailySpending().add(amount));
        fromAccount.setMonthlySpending(fromAccount.getMonthlySpending().add(amount));

        toAccount.setBalance(toAccount.getBalance().add(creditedAmount));
        toAccount.setAvailableBalance(toAccount.getAvailableBalance().add(creditedAmount));

        Payment base = Payment.builder()
                .fromAccount(fromAccount)
                .toAccountNumber(request.getToAccount())
                .amount(amount)
                .fee(transactionFee)
                .currency(fromAccount.getCurrency())
                .paymentCode(paymentCode)
                .referenceNumber(request.getReferenceNumber())
                .purpose(request.getDescription())
                .status(PaymentStatus.COMPLETED)
                .createdBy(client)
                .build();

        Payment savedPayment = null;

        //Pokusava da generise jedinstven payment broj pomocu uk constrainta
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

        transactionService.recordPaymentSettlement(savedPayment, toAccount, client, creditedAmount);
        return toResponse(savedPayment, client.getId());
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
        Client client = getOptionalClient();
        if (client == null) return Page.empty(pageable);
        return paymentRepository.findByUserAccountsWithFilters(
                        client.getId(),
                        fromDate,
                        toDate,
                        minAmount,
                        maxAmount,
                        status,
                        pageable
                )
                .map(payment -> toListItem(payment, client.getId()));
    }

    @Override
    public PaymentResponseDto getPaymentById(Long paymentId) {
        Client client = getAuthenticatedClient();
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment with ID " + paymentId + " not found."));
        return toResponse(payment, client.getId());
    }

    @Override
    public byte[] getPaymentReceipt(Long paymentId) {
        Long clientId = getAuthenticatedClient().getId();
        TransactionResponseDto transaction = transactionService.getReceiptTransaction(paymentId, clientId);

        return paymentReceiptPdfGenerator.generate(transaction);
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
        if (f.equals(t)) return BigDecimal.ONE;

        // Exchange service provides rates in the form: 1 RSD = rate * CURRENCY.
        List<ExchangeRateDto> rates = exchangeService.getAllRates();
        Map<String, BigDecimal> rsdToCurrency = new HashMap<>();

        for (ExchangeRateDto rate : rates) {
            if (rate.getCurrency() != null) {
                rsdToCurrency.put(rate.getCurrency().toUpperCase(), BigDecimal.valueOf(rate.getRate()));
            }
        }

        BigDecimal rsdToFrom = rsdToCurrency.get(f);
        BigDecimal rsdToTo = rsdToCurrency.get(t);

        if (rsdToFrom == null || rsdToTo == null || rsdToFrom.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Unsupported currency pair: " + from + "/" + to);
        }

        // from->to = (RSD->to) / (RSD->from)
        return rsdToTo.divide(rsdToFrom, 10, RoundingMode.HALF_UP);
    }


    private PaymentResponseDto toResponse(Payment payment, Long authenticatedClientId) {
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
                .direction(resolveDirection(payment, authenticatedClientId))
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private PaymentListItemDto toListItem(Payment payment, Long authenticatedClientId) {
        PaymentDirection direction = resolveDirection(payment, authenticatedClientId);

        return PaymentListItemDto.builder()
                .id(payment.getId())
                .orderNumber(payment.getOrderNumber())
                .fromAccount(payment.getFromAccount() != null ? payment.getFromAccount().getAccountNumber() : null)
                .toAccount(payment.getToAccountNumber())
                .amount(payment.getAmount())
                .direction(direction)
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private PaymentDirection resolveDirection(Payment payment, Long authenticatedClientId) {
        if (payment.getFromAccount() == null || payment.getFromAccount().getClient() == null) {
            return PaymentDirection.INCOMING;
        }

        return payment.getFromAccount().getClient().getId().equals(authenticatedClientId)
                ? PaymentDirection.OUTGOING
                : PaymentDirection.INCOMING;
    }

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

    private Client getAuthenticatedClient() {
        Client client = getOptionalClient();
        if (client == null) throw new IllegalArgumentException("Authenticated client does not exist.");
        return client;
    }

    private Client getOptionalClient() {
        try {
            String username = getAuthenticatedUsername();
            return clientRepository.findByEmail(username).orElse(null);
        } catch (Exception e) {
            return null;
        }
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

