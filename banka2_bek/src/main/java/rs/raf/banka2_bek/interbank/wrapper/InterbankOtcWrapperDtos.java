package rs.raf.banka2_bek.interbank.wrapper;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO-ovi za FE-facing wrapper rute (`/interbank/otc/*`). Format polja matchuje
 * TypeScript tipove u {@code Banka-2-Frontend/src/types/celina4.ts}
 * ({@code OtcInterbankListing}, {@code OtcInterbankOffer}, ...).
 */
public final class InterbankOtcWrapperDtos {

    private InterbankOtcWrapperDtos() {}

    /**
     * Stavka iz "OTC Listings" tab-a — jedan ticker × jedan seller (klijent ili
     * zaposleni) iz druge banke. FE ovo prikazuje u tabeli sa filter-om po
     * tickeru i banci.
     */
    public record OtcInterbankListing(
            String bankCode,
            String sellerPublicId,
            String sellerName,
            String listingTicker,
            String listingName,
            String listingCurrency,
            BigDecimal currentPrice,
            BigDecimal availableQuantity,
            String sellerRole // "CLIENT" ili "EMPLOYEE", null ako partner ne posalje
    ) {}

    public record OtcInterbankOffer(
            String offerId,
            String listingTicker,
            String listingName,
            String listingCurrency,
            BigDecimal currentPrice,
            String buyerBankCode,
            String buyerUserId,
            String buyerName,
            String sellerBankCode,
            String sellerUserId,
            String sellerName,
            BigDecimal quantity,
            BigDecimal pricePerStock,
            BigDecimal premium,
            LocalDate settlementDate,
            String waitingOnBankCode,
            String waitingOnUserId,
            boolean myTurn,
            String status,
            LocalDateTime lastModifiedAt,
            String lastModifiedByName
    ) {}

    public record CreateOtcInterbankOfferRequest(
            @NotBlank String sellerBankCode,
            @NotBlank String sellerUserId,
            @NotBlank String listingTicker,
            @NotNull @Positive BigDecimal quantity,
            @NotNull @Positive BigDecimal pricePerStock,
            @NotNull BigDecimal premium,
            @NotNull LocalDate settlementDate
    ) {}

    public record CounterOtcInterbankOfferRequest(
            String offerId,
            @NotNull @Positive BigDecimal quantity,
            @NotNull @Positive BigDecimal pricePerStock,
            @NotNull BigDecimal premium,
            @NotNull LocalDate settlementDate
    ) {}

    public record OtcInterbankContract(
            String id,
            Long listingId,
            String listingTicker,
            String listingName,
            String listingCurrency,
            String buyerUserId,
            String buyerBankCode,
            String buyerName,
            String sellerUserId,
            String sellerBankCode,
            String sellerName,
            BigDecimal quantity,
            BigDecimal strikePrice,
            BigDecimal premium,
            BigDecimal currentPrice,
            LocalDate settlementDate,
            String status,
            LocalDateTime createdAt,
            LocalDateTime exercisedAt
    ) {}

    public record InterbankTransactionDto(
            Long id,
            String transactionId,
            String type,
            String status,
            String currentPhase,
            String senderBankCode,
            String receiverBankCode,
            BigDecimal amount,
            String currency,
            String listingTicker,
            BigDecimal quantity,
            BigDecimal strikePrice,
            LocalDateTime createdAt,
            LocalDateTime committedAt,
            LocalDateTime abortedAt,
            int retryCount,
            String failureReason
    ) {}

    /** Wrapper za listu listinga (FE povremeno trazi celu listu odjednom). */
    public record OtcListingsResponse(List<OtcInterbankListing> listings) {}
}
