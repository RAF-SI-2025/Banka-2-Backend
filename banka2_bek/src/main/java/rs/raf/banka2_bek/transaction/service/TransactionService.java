package rs.raf.banka2_bek.transaction.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.payment.model.Payment;
import rs.raf.banka2_bek.transaction.dto.TransactionListItemDto;
import rs.raf.banka2_bek.transaction.dto.TransactionResponseDto;
import rs.raf.banka2_bek.transaction.dto.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionService {

    List<TransactionResponseDto> recordPaymentSettlement(
            Payment payment,
            Account toAccount,
            Client initiatedBy,
            BigDecimal creditedAmount
    );

    /**
     * T2-013 FX 3-fazni lanac (Celina 2 §255-258): kada se placanje izvrsava
     * preko bankinih racuna za konverziju valuta, perzistuju se sve faze
     * kao zasebni transaction redovi:
     *   1) Klijent EUR → Bank EUR (amount + fee)
     *   2) Bank EUR → Bank toCurrency (internal konverzija, isti iznos kao 1 u source)
     *   3) Bank toCurrency → Klijent toCurrency (creditedAmount)
     * Same-currency placanja zovu staru `recordPaymentSettlement` putanju.
     */
    List<TransactionResponseDto> recordCrossCurrencyPaymentSettlement(
            Payment payment,
            Account toAccount,
            Account bankFromAccount,
            Account bankToAccount,
            Client initiatedBy,
            BigDecimal totalFromClient,
            BigDecimal creditedAmount
    );

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

    TransactionResponseDto getReceiptTransaction(Long transactionId, Long clientId);

}
