package rs.raf.banka2_bek.account.service.implementation;

import rs.raf.banka2_bek.account.dto.AccountResponseDto;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.account.service.AccountService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.company.dto.CompanyDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountServiceImplementation implements AccountService {

    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository; //made client repo - nije ga bilo

    private Account checkAuth(Long accountId) throws IllegalStateException {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account with ID " + accountId + " not found."
                ));

        Client client = getAuthenticatedClient();
        if (account.getClient() == null || !account.getClient().getId().equals(client.getId())) {
            throw new IllegalStateException(
                    "You do not have access to account with ID " + accountId + "."
            );
        }
        return account;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponseDto> getMyAccounts() {
        Client client = getAuthenticatedClient();
        List<Account> accounts = accountRepository
                .findByClientIdAndStatusOrderByAvailableBalanceDesc(
                        client.getId(), AccountStatus.ACTIVE
                );
        return accounts.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponseDto getAccountById(Long accountId) {
        return toResponse(checkAuth(accountId));
    }

    @Override
    @Transactional
    public AccountResponseDto updateAccountName(Long accountId, String newName) {
        Account account = accountRepository.findById(accountId).orElseThrow(()->new IllegalArgumentException("Account eith ID " + accountId + "not found. "));

        //samo vlasnik moze da menja naziv
        Client client = getAuthenticatedClient();
        if (account.getClient() == null || !account.getClient().getId().equals(client.getId())) {
            throw new IllegalStateException(
                    "You do not have access to account with ID " + accountId + "."
            );
        }

        // novo ime ne sme biti isto kao staro
        if (newName.equals(account.getName())) {
            throw new IllegalStateException(
                    "New account name must differ from the current name."
            );
        }
        //novo ime mora da se ne poklapa izmedju racuna
        boolean nameExists = accountRepository
                .findByClientIdAndStatusOrderByAvailableBalanceDesc(client.getId(), AccountStatus.ACTIVE)
                .stream()
                .anyMatch(a -> newName.equals(a.getName()) && !a.getId().equals(accountId));

        if (nameExists) {
            throw new IllegalStateException(
                    "Account name '" + newName + "' is already used by another account."
            );
        }

        account.setName(newName);
        accountRepository.save(account);

        return toResponse(account);
    }

    @Override
    public AccountResponseDto updateAccountLimits(Long accountId, BigDecimal dailyLimit, BigDecimal monthlyLimit) {
        Account account = checkAuth(accountId);

        // TODO: Verifikacija transakcije (mobilna aplikacija)
        // Kada bude implementirana mobilna aplikacija, ovde treba dodati
        // poziv ka verifikacionom servisu koji proverava da li je korisnik
        // potvrdio promenu na mobilnom (npr. approval code ili OTP).
        // Flow iz specifikacije:
        //   1. Klijent zatrazi promenu limita na laptopu
        //   2. Klijent potvrdjuje na mobilnom ("approve transaction" dugme)
        //   3. Kod traje 5 min, nakon 3 neuspesna pokusaja otkazuje se

        if (dailyLimit != null) {
            account.setDailyLimit(dailyLimit);
        }
        if (monthlyLimit != null) {
            account.setMonthlyLimit(monthlyLimit);
        }

        accountRepository.save(account);

        return toResponse(account);
    }


    private AccountResponseDto toResponse(Account account) {
        // rezervisana sredstva = stanje - raspolozivo stanje
        BigDecimal reservedFunds = account.getBalance()
                .subtract(account.getAvailableBalance());

        // ime vlasnika
        String ownerName;
        if (account.getClient() != null) {
            ownerName = account.getClient().getFirstName() + " "
                    + account.getClient().getLastName();
        } else if (account.getCompany() != null) {
            ownerName = account.getCompany().getName();
        } else {
            ownerName = "N/A";
        }

        // ime zaposlenog koji je kreirao racun
        String employeeName = account.getEmployee().getFirstName() + " "
                + account.getEmployee().getLastName();

        AccountResponseDto.AccountResponseDtoBuilder builder = AccountResponseDto.builder()
                .id(account.getId())
                .name(account.getName())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType() != null
                        ? account.getAccountType().name() : null)
                .accountSubType(account.getAccountSubtype() != null
                        ? account.getAccountSubtype().name() : null)
                .status(account.getStatus().name())
                .ownerName(ownerName)
                .balance(account.getBalance())
                .availableBalance(account.getAvailableBalance())
                .reservedFunds(reservedFunds)
                .currencyCode(account.getCurrency().getCode())
                .dailyLimit(account.getDailyLimit())
                .monthlyLimit(account.getMonthlyLimit())
                .expirationDate(account.getExpirationDate())
                .createdAt(account.getCreatedAt())
                .createdByEmployee(employeeName);

        // podaci o firmi (samo za poslovne racune)
        if (account.getCompany() != null) {
            Company company = account.getCompany();
            builder.company(CompanyDto.builder()
                    .id(company.getId())
                    .name(company.getName())
                    .registrationNumber(company.getRegistrationNumber())
                    .taxNumber(company.getTaxNumber())
                    .activityCode(company.getActivityCode())
                    .address(company.getAddress())
                    .build());
        }

        return builder.build();
    }



    // helper za autentifikaciju klijenta
    private Client getAuthenticatedClient() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated.");
        }

        String email;
        Object principal = auth.getPrincipal();

        if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else if (principal instanceof String s) {
            email = s;
        } else {
            throw new IllegalStateException(
                    "Unable to determine user email from security context."
            );
        }

        return clientRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Client with email " + email + " not found."
                ));
    }
}
