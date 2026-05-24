package rs.raf.banka2_bek.transfers.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.notification.model.NotificationType;
import rs.raf.banka2_bek.notification.service.NotificationService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.exchange.dto.CalculateExchangeResponseDto;
import rs.raf.banka2_bek.payment.model.PaymentStatus;
import rs.raf.banka2_bek.transfers.model.Transfer;
import rs.raf.banka2_bek.transfers.model.TransferType;
import rs.raf.banka2_bek.transfers.dto.TransferFxRequestDto;
import rs.raf.banka2_bek.transfers.dto.TransferInternalRequestDto;
import rs.raf.banka2_bek.transfers.dto.TransferResponseDto;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.transfers.repository.TransferRepository;
import rs.raf.banka2_bek.exchange.ExchangeService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// TODO [B4 - Okidaci notifikacija | Nosilac: Petar Poznanovic]
// Nakon sto je transfer uspesno izvrsen, pozvati notifikacioni servis
// da posaljeIn-app i email notifikaciju korisniku.
// Primer tacaka integracije:
//   - executeInternalTransfer() -> posle konacnog transfer.setStatus(COMPLETED) i save:
//       notifikacioniServis.posaljiUspesanTransferNotifikaciju(transfer);
//   - executeFxTransfer() -> analogno posle FX konverzije i save.
// Notifikacioni servis treba da bude injektovan kao zavisnost (@Autowired ili konstruktor).
@Slf4j
@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final ExchangeService exchangeService;
    private final ClientRepository clientRepository;

    private final NotificationService notificationService;

    @Value("${bank.registration-number}")
    private String bankRegistrationNumber;

    public TransferService(TransferRepository transferRepository,
                           AccountRepository accountRepository,
                           ExchangeService exchangeService,
                           ClientRepository clientRepository,
                           NotificationService notificationService) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.exchangeService = exchangeService;
        this.clientRepository = clientRepository;
        this.notificationService = notificationService;
    }

    private TransferResponseDto mapToDto(Transfer transfer) {
        TransferResponseDto response = new TransferResponseDto();
        response.setId(transfer.getId());
        response.setOrderNumber(transfer.getOrderNumber());
        response.setFromAccountNumber(transfer.getFromAccount().getAccountNumber());
        response.setToAccountNumber(transfer.getToAccount().getAccountNumber());
        response.setAmount(transfer.getFromAmount());
        response.setToAmount(transfer.getToAmount());
        response.setFromCurrency(transfer.getFromCurrency().getCode());
        response.setToCurrency(transfer.getToCurrency().getCode());
        response.setExchangeRate(transfer.getExchangeRate());
        response.setCommission(transfer.getCommission());
        response.setClientFirstName(transfer.getCreatedBy().getFirstName());
        response.setClientLastName(transfer.getCreatedBy().getLastName());
        response.setStatus(transfer.getStatus());
        response.setCreatedAt(transfer.getCreatedAt());
        return response;
    }


    @Transactional
    public TransferResponseDto internalTransfer(TransferInternalRequestDto request) {

        // Pessimistic lock to prevent double-spending
        Account fromAccount = accountRepository.findForUpdateByAccountNumber(request.getFromAccountNumber())
                .orElseThrow(() -> new RuntimeException("From account not found"));

        Account toAccount = accountRepository.findForUpdateByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new RuntimeException("To account not found"));

        if (request.getFromAccountNumber().equals(request.getToAccountNumber())) {
            throw new RuntimeException("Accounts must be different");
        }

        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Source account is not active");
        }
        if (toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Destination account is not active");
        }

        Client actor = getAuthenticatedClient();
        ensureAccess(actor, fromAccount);
        ensureAccess(actor, toAccount);

        // Auto-detect FX: ako su razlicite valute, preusmeri na FX transfer
        if (!fromAccount.getCurrency().getId().equals(toAccount.getCurrency().getId())) {
            TransferFxRequestDto fxRequest = new TransferFxRequestDto();
            fxRequest.setFromAccountNumber(request.getFromAccountNumber());
            fxRequest.setToAccountNumber(request.getToAccountNumber());
            fxRequest.setAmount(request.getAmount());
            return fxTransfer(fxRequest);
        }

        if (fromAccount.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient funds");
        }

        fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
        fromAccount.setAvailableBalance(fromAccount.getAvailableBalance().subtract(request.getAmount()));
        toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));
        toAccount.setAvailableBalance(toAccount.getAvailableBalance().add(request.getAmount()));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        Transfer transfer = new Transfer();
        transfer.setOrderNumber(UUID.randomUUID().toString().replace("-", "").substring(0, 30));
        transfer.setFromAccount(fromAccount);
        transfer.setToAccount(toAccount);
        transfer.setFromAmount(request.getAmount());
        transfer.setToAmount(request.getAmount());
        transfer.setFromCurrency(fromAccount.getCurrency());
        transfer.setToCurrency(toAccount.getCurrency());
        transfer.setExchangeRate(null);
        transfer.setCommission(BigDecimal.ZERO);
        transfer.setTransferType(TransferType.INTERNAL_TRANSFER);
        transfer.setStatus(PaymentStatus.COMPLETED);
        transfer.setCreatedBy(actor);

        transferRepository.save(transfer);

        try {
            notificationService.notify(
                    actor.getId(),
                    "CLIENT",
                    NotificationType.TRANSFER,
                    "Transfer izvršen",
                    "Vaš interni transfer od " + request.getAmount() + " " + fromAccount.getCurrency().getCode() + " je uspešno izvršen.",
                    "TRANSFER",
                    transfer.getId()
            );
        } catch (Exception e) {
            log.warn("Failed to send transfer notification: {}", e.getMessage());
        }

        return mapToDto(transfer);
    }

    @Transactional
    public TransferResponseDto fxTransfer(TransferFxRequestDto request) {

        // Pessimistic lock on client accounts
        Account fromAccount = accountRepository.findForUpdateByAccountNumber(request.getFromAccountNumber())
                .orElseThrow(() -> new RuntimeException("From account not found"));

        Account toAccount = accountRepository.findForUpdateByAccountNumber(request.getToAccountNumber())
                .orElseThrow(() -> new RuntimeException("To account not found"));

        if (request.getFromAccountNumber().equals(request.getToAccountNumber())) {
            throw new RuntimeException("Accounts must be different");
        }

        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Source account is not active");
        }
        if (toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new RuntimeException("Destination account is not active");
        }

        Client actor = getAuthenticatedClient();
        ensureAccess(actor, fromAccount);
        ensureAccess(actor, toAccount);

        if (fromAccount.getCurrency().getId().equals(toAccount.getCurrency().getId())) {
            throw new RuntimeException("Accounts must have different currencies");
        }

        // Calculate commission: 0.5% of transfer amount
        BigDecimal commissionRate = new BigDecimal("0.005");
        BigDecimal commissionAmount = request.getAmount().multiply(commissionRate).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal totalDebit = request.getAmount().add(commissionAmount);

        // Check client can pay amount + commission
        if (fromAccount.getAvailableBalance().compareTo(totalDebit) < 0) {
            throw new RuntimeException("Nedovoljno sredstava. Potrebno: " + totalDebit + " " +
                    fromAccount.getCurrency().getCode() + " (iznos + provizija 0.5%)");
        }

        // Lock bank accounts for the two currencies involved
        String fromCurrencyCode = fromAccount.getCurrency().getCode();
        String toCurrencyCode = toAccount.getCurrency().getCode();

        Account bankFromAccount = accountRepository.findBankAccountForUpdateByCurrency(bankRegistrationNumber, fromCurrencyCode)
                .orElseThrow(() -> new RuntimeException("Bank account for " + fromCurrencyCode + " not found"));
        Account bankToAccount = accountRepository.findBankAccountForUpdateByCurrency(bankRegistrationNumber, toCurrencyCode)
                .orElseThrow(() -> new RuntimeException("Bank account for " + toCurrencyCode + " not found"));

        // Calculate exchange (includes 2% markup in rate)
        CalculateExchangeResponseDto exchangeResult = exchangeService.calculateCross(
                request.getAmount().doubleValue(),
                fromCurrencyCode,
                toCurrencyCode
        );

        BigDecimal toAmount = BigDecimal.valueOf(exchangeResult.getConvertedAmount());
        BigDecimal exchangeRate = BigDecimal.valueOf(exchangeResult.getExchangeRate());

        // Check bank has enough of the target currency
        if (bankToAccount.getAvailableBalance().compareTo(toAmount) < 0) {
            throw new RuntimeException("Bank does not have enough " + toCurrencyCode + " reserves");
        }

        // 1. Client pays source currency + commission
        fromAccount.setBalance(fromAccount.getBalance().subtract(totalDebit));
        fromAccount.setAvailableBalance(fromAccount.getAvailableBalance().subtract(totalDebit));

        // 2. Bank receives source currency + commission (amount + commission goes to bank)
        bankFromAccount.setBalance(bankFromAccount.getBalance().add(totalDebit));
        bankFromAccount.setAvailableBalance(bankFromAccount.getAvailableBalance().add(totalDebit));

        // 3. Bank pays target currency
        bankToAccount.setBalance(bankToAccount.getBalance().subtract(toAmount));
        bankToAccount.setAvailableBalance(bankToAccount.getAvailableBalance().subtract(toAmount));

        // 4. Client receives target currency
        toAccount.setBalance(toAccount.getBalance().add(toAmount));
        toAccount.setAvailableBalance(toAccount.getAvailableBalance().add(toAmount));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
        accountRepository.save(bankFromAccount);
        accountRepository.save(bankToAccount);

        Transfer transfer = new Transfer();
        transfer.setOrderNumber(UUID.randomUUID().toString().replace("-", "").substring(0, 30));
        transfer.setFromAccount(fromAccount);
        transfer.setToAccount(toAccount);
        transfer.setFromAmount(request.getAmount());
        transfer.setToAmount(toAmount);
        transfer.setFromCurrency(fromAccount.getCurrency());
        transfer.setToCurrency(toAccount.getCurrency());
        transfer.setExchangeRate(exchangeRate);
        transfer.setCommission(commissionAmount);
        transfer.setTransferType(TransferType.EXCHANGE);
        transfer.setStatus(PaymentStatus.COMPLETED);
        transfer.setCreatedBy(actor);

        transferRepository.save(transfer);

        try {
            notificationService.notify(
                    actor.getId(),
                    "CLIENT",
                    NotificationType.TRANSFER,
                    "FX transfer izvršen",
                    "Vaš devizni transfer od " + request.getAmount() + " " + fromAccount.getCurrency().getCode() + " je uspešno izvršen.",
                    "TRANSFER",
                    transfer.getId()
            );
        } catch (Exception e) {
            log.warn("Failed to send FX transfer notification: {}", e.getMessage());
        }

        return mapToDto(transfer);
    }

    public List<TransferResponseDto> getAllTransfers(Client client, String accountNumber, java.time.LocalDate fromDate, java.time.LocalDate toDate) {
        List<Transfer> transfers = transferRepository.findByCreatedByOrderByCreatedAtDesc(client);

        java.time.LocalDateTime fromDateTime = fromDate != null ? fromDate.atStartOfDay() : null;
        java.time.LocalDateTime toDateTime = toDate != null ? toDate.atTime(23, 59, 59) : null;

        List<TransferResponseDto> result = new ArrayList<>();
        for (Transfer transfer : transfers) {
            if (accountNumber != null && !accountNumber.isBlank()) {
                String fromAcc = transfer.getFromAccount().getAccountNumber();
                String toAcc = transfer.getToAccount().getAccountNumber();
                if (!accountNumber.equals(fromAcc) && !accountNumber.equals(toAcc)) {
                    continue;
                }
            }
            if (fromDateTime != null && transfer.getCreatedAt().isBefore(fromDateTime)) continue;
            if (toDateTime != null && transfer.getCreatedAt().isAfter(toDateTime)) continue;
            result.add(mapToDto(transfer));
        }
        return result;
    }

    public TransferResponseDto getTransferById(Long id) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transfer not found"));
        return mapToDto(transfer);
    }

    // ---------- Helpers ----------

    private void ensureAccess(Client actor, Account account) {
        if (actor == null) throw new RuntimeException("Authenticated client not found");

        if (account.getClient() != null && account.getClient().getId().equals(actor.getId())) {
            return;
        }
        if (account.getCompany() != null && account.getCompany().getAuthorizedPersons() != null) {
            boolean authorized = account.getCompany().getAuthorizedPersons().stream()
                    .anyMatch(ap -> ap.getClient() != null && ap.getClient().getId().equals(actor.getId()));
            if (authorized) return;
        }
        throw new RuntimeException("You do not have access to the specified account");
    }

    private Client getAuthenticatedClient() {
        String email = getAuthenticatedEmail();
        return clientRepository.findByEmail(email).orElseThrow(() ->
                new RuntimeException("Client not found for authenticated user"));
    }

    private String getAuthenticatedEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("User is not authenticated");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        if (principal instanceof String s) {
            return s;
        }
        throw new RuntimeException("Unable to resolve user email");
    }
}
