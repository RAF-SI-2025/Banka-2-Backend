package rs.raf.banka2_bek.transaction.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.transaction.dto.TransactionListItemDto;
import rs.raf.banka2_bek.transaction.dto.TransactionResponseDto;
import rs.raf.banka2_bek.transaction.dto.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionService {

    List<TransactionResponseDto> recordPaymentSettlement(Payment payment, Account toAccount, User initiatedBy);

    Page<TransactionListItemDto> getTransactions(Pageable pageable);

    Page<TransactionListItemDto> getTransactions(
            Pageable pageable,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            TransactionType type
    );

    TransactionResponseDto getTransactionById(Long transactionId);

}
