package rs.raf.banka2_bek.account.service.implementation;

import rs.raf.banka2_bek.account.dto.AccountResponseDto;
import rs.raf.banka2_bek.account.dto.CreateAccountDto;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.account.service.AccountService;
import rs.raf.banka2_bek.account.util.AccountNumberUtils;
import rs.raf.banka2_bek.card.service.CardService;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.company.model.Company;
import rs.raf.banka2_bek.company.model.AuthorizedPerson;
import rs.raf.banka2_bek.company.repository.CompanyRepository;
import rs.raf.banka2_bek.company.dto.CompanyDto;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import rs.raf.banka2_bek.account.event.AccountCreatedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AccountServiceImplementation implements AccountService {

    private final AccountRepository accountRepository;
    private final ClientRepository clientRepository;
    private final CurrencyRepository currencyRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final CardService cardService;
    private final ApplicationEventPublisher eventPublisher;
    private final String bankRegistrationNumber;

    public AccountServiceImplementation(AccountRepository accountRepository,
                                         ClientRepository clientRepository,
                                         CurrencyRepository currencyRepository,
                                         CompanyRepository companyRepository,
                                         EmployeeRepository employeeRepository,
                                         UserRepository userRepository,
                                         @Lazy CardService cardService,
                                         ApplicationEventPublisher eventPublisher,
                                         @Value("${bank.registration-number}") String bankRegistrationNumber) {
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
        this.currencyRepository = currencyRepository;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.cardService = cardService;
        this.eventPublisher = eventPublisher;
        this.bankRegistrationNumber = bankRegistrationNumber;
    }

    @Override
    @Transactional
    public AccountResponseDto createAccount(CreateAccountDto request) {
        // Find currency (FE salje "currency" ili "currencyCode")
        String currCode = request.getResolvedCurrencyCode();
        if (currCode == null || currCode.isBlank()) {
            throw new RuntimeException("Valuta je obavezna");
        }
        Currency currency = currencyRepository.findByCode(currCode)
                .orElseThrow(() -> new RuntimeException("Valuta '" + currCode + "' nije pronadjena"));

        // Find employee from security context (admin moze biti u users ali ne u employees)
        String email = getAuthenticatedEmail();
        Employee employee = employeeRepository.findByEmail(email).orElse(null);
        if (employee == null) {
            employee = employeeRepository.findAll().stream()
                    .filter(e -> Boolean.TRUE.equals(e.getActive()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Nema aktivnih zaposlenih u sistemu"));
        }

        // Resolve owner — FE salje ownerEmail, BE podrzava i clientId
        Client client = null;
        Company company = null;

        if (request.getClientId() != null) {
            client = clientRepository.findById(request.getClientId())
                    .orElseThrow(() -> new RuntimeException("Klijent sa ID " + request.getClientId() + " nije pronadjen"));
        } else if (request.getOwnerEmail() != null && !request.getOwnerEmail().isBlank()) {
            client = clientRepository.findByEmail(request.getOwnerEmail()).orElse(null);
            // Ako klijent ne postoji u clients tabeli ali postoji u users (registrovan), kreiraj Client zapis
            if (client == null) {
                rs.raf.banka2_bek.auth.model.User user = userRepository.findByEmail(request.getOwnerEmail())
                        .orElseThrow(() -> new RuntimeException("Korisnik sa emailom '" + request.getOwnerEmail() + "' nije pronadjen"));
                client = Client.builder()
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .email(user.getEmail())
                        .phone(user.getPhone() != null ? user.getPhone() : "N/A")
                        .address(user.getAddress())
                        .gender(user.getGender())
                        .dateOfBirth(user.getDateOfBirth() != null
                                ? java.time.Instant.ofEpochMilli(user.getDateOfBirth()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                : LocalDate.of(2000, 1, 1))
                        .password(user.getPassword())
                        .saltPassword("auto")
                        .active(true)
                        .build();
                client = clientRepository.save(client);
            }
        }

        // Poslovni racun — FE salje flat polja ILI nested company objekat
        boolean isBusiness = request.getCompany() != null || request.getCompanyName() != null;

        if (isBusiness) {
            String compName = request.getCompany() != null ? request.getCompany().getName() : request.getCompanyName();
            String regNum = request.getCompany() != null ? request.getCompany().getRegistrationNumber() : request.getRegistrationNumber();
            String taxNum = request.getCompany() != null ? request.getCompany().getTaxNumber() : request.getTaxId();
            String actCode = request.getCompany() != null ? request.getCompany().getActivityCode() : request.getActivityCode();
            String addr = request.getCompany() != null ? request.getCompany().getAddress() : request.getFirmAddress();

            // T2-008: pre nego sto pokusamo INSERT, eksplicitno proveri uniqueness
            // (PIB i maticni broj). Bez ove provere, korisnik dobija ruzni
            // ConstraintViolationException raw SQL message.
            if (taxNum != null && !taxNum.isBlank() && companyRepository.existsByTaxNumber(taxNum)) {
                throw new IllegalArgumentException("Firma sa PIB-om '" + taxNum + "' vec postoji u sistemu.");
            }
            if (regNum != null && !regNum.isBlank() && companyRepository.existsByRegistrationNumber(regNum)) {
                throw new IllegalArgumentException("Firma sa maticnim brojem '" + regNum + "' vec postoji u sistemu.");
            }

            company = Company.builder()
                    .name(compName)
                    .registrationNumber(regNum)
                    .taxNumber(taxNum)
                    .activityCode(actCode)
                    .address(addr != null ? addr : "N/A")
                    .authorizedPersons(new ArrayList<>())
                    .build();

            if (client != null) {
                AuthorizedPerson ap = AuthorizedPerson.builder()
                        .client(client)
                        .company(company)
                        .build();
                company.getAuthorizedPersons().add(ap);
            }

            try {
                company = companyRepository.save(company);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Defense-in-depth: ako race condition (dva paralelna createAccount sa istim PIB-om)
                // probije unique constraint, vrati user-friendly poruku umesto raw SQL exception.
                throw new IllegalArgumentException(
                        "Firma sa unetim PIB-om ili maticnim brojem vec postoji u sistemu.");
            }
        }

        if (!isBusiness && client == null) {
            throw new RuntimeException("Licni racun mora imati klijenta (ownerEmail ili clientId)");
        }

        // Generate account number
        String accountNumber;
        do {
            accountNumber = AccountNumberUtils.generate(
                    request.getAccountType(),
                    request.getAccountSubtype(),
                    isBusiness);
        } while (accountRepository.existsByAccountNumber(accountNumber));

        BigDecimal dailyLimit = request.getDailyLimit() != null ? request.getDailyLimit() : BigDecimal.valueOf(250000);
        BigDecimal monthlyLimit = request.getMonthlyLimit() != null ? request.getMonthlyLimit() : BigDecimal.valueOf(1000000);

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .accountType(request.getAccountType())
                .accountSubtype(request.getAccountSubtype())
                .currency(currency)
                // Poslovni račun mora imati samo company vlasnika (AssertTrue u Account)
                .client(isBusiness ? null : client)
                .company(company)
                .employee(employee)
                .balance(request.getResolvedInitialBalance())
                .availableBalance(request.getResolvedInitialBalance())
                .dailyLimit(dailyLimit)
                .monthlyLimit(monthlyLimit)
                .maintenanceFee(request.getAccountType() == AccountType.FOREIGN ? BigDecimal.ZERO : BigDecimal.valueOf(255))
                .status(AccountStatus.ACTIVE)
                .expirationDate(LocalDate.now().plusYears(5))
                .createdAt(LocalDateTime.now())
                .build();

        account = accountRepository.save(account);

        // Auto-create card if requested
        if (Boolean.TRUE.equals(request.getCreateCard()) && client != null) {
            cardService.createCardForAccount(account.getId(), client.getId(), null, null);
        }

        // Send email notification
        if (client != null && client.getEmail() != null) {
            String accountTypeLabel = account.getAccountType() != null
                    ? account.getAccountType().name() + " (" + account.getCurrency().getCode() + ")"
                    : account.getCurrency().getCode();
            eventPublisher.publishEvent(new AccountCreatedEvent(
                    client.getEmail(),
                    client.getFirstName() + " " + client.getLastName(),
                    account.getAccountNumber(),
                    accountTypeLabel));
        }

        return toResponse(account);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccountResponseDto> getAllAccounts(int page, int limit, String ownerName) {
        PageRequest pageRequest = PageRequest.of(page, limit, Sort.by("id").ascending());
        return accountRepository.findAllWithOwnerFilter(ownerName, pageRequest)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponseDto> getAccountsByClient(Long clientId) {
        return accountRepository.findByClientId(clientId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AccountResponseDto changeAccountStatus(Long accountId, String newStatus) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Racun sa ID " + accountId + " nije pronadjen"));
        try {
            account.setStatus(AccountStatus.valueOf(newStatus.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Nepoznat status: " + newStatus);
        }
        account = accountRepository.save(account);
        return toResponse(account);
    }

    private Account checkAuth(Long accountId) throws IllegalStateException {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Account with ID " + accountId + " not found."
                ));

        // Admin/employee can see any account
        Client client = getOptionalClient();
        if (client == null) return account;

        // Direktni vlasnik (licni racun)
        if (account.getClient() != null && account.getClient().getId().equals(client.getId())) {
            return account;
        }

        // Ovlašćeno lice za kompaniju vlasnika (poslovni racun)
        if (account.getCompany() != null) {
            boolean authorized = account.getCompany().getAuthorizedPersons().stream()
                    .anyMatch(ap -> ap.getClient() != null && ap.getClient().getId().equals(client.getId()));
            if (authorized) {
                return account;
            }
        }

        throw new IllegalStateException(
                "You do not have access to account with ID " + accountId + "."
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponseDto> getMyAccounts() {
        Client client = getOptionalClient();
        if (client == null) return new ArrayList<>();
        List<Account> accounts = accountRepository.findAccessibleAccounts(
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
        Account account = checkAuth(accountId);

        // novo ime ne sme biti isto kao staro
        if (newName.equals(account.getName())) {
            throw new IllegalStateException(
                    "New account name must differ from the current name."
            );
        }

        // Proveri duplikat imena medju racunima istog vlasnika
        Client client = getOptionalClient();
        if (client != null) {
            List<Account> clientAccounts = accountRepository.findAccessibleAccounts(
                    client.getId(), AccountStatus.ACTIVE);
            boolean nameExists = clientAccounts.stream()
                    .anyMatch(a -> newName.equals(a.getName()) && !a.getId().equals(accountId));
            if (nameExists) {
                throw new IllegalStateException(
                        "Account name '" + newName + "' is already used by another account."
                );
            }
        }

        account.setName(newName);
        accountRepository.save(account);

        return toResponse(account);
    }

    @Override
    @Transactional
    public AccountResponseDto updateAccountLimits(Long accountId, BigDecimal dailyLimit, BigDecimal monthlyLimit) {
        Account account = checkAuth(accountId);

        // Verifikacija promene limita (Celina 2 — "Promena limita (zahteva verifikaciju)").
        // Mobilni OTP flow je vec implementiran kao VerificationModal komponenta na FE-u
        // i mobile-side na Banka-2-Mobile aplikaciji; ovde se OTP code prenosi kao
        // request param u kontroleru i validira pre dolaska u service. Buduci ekstenziton
        // poslovni flow (npr. push notifikacija + ASR-style biometric approval) ce se
        // prikljuciti istim verification servisom.

        if (dailyLimit != null) {
            account.setDailyLimit(dailyLimit);
        }
        if (monthlyLimit != null) {
            account.setMonthlyLimit(monthlyLimit);
        }

        accountRepository.save(account);

        return toResponse(account);
    }


    @Override
    @Transactional(readOnly = true)
    public List<AccountResponseDto> getBankAccounts() {
        return accountRepository.findBankAccounts(bankRegistrationNumber).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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
        String employeeName = account.getEmployee() != null
                ? account.getEmployee().getFirstName() + " " + account.getEmployee().getLastName()
                : "N/A";

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
                .currency(account.getCurrency().getCode())
                .dailyLimit(account.getDailyLimit())
                .monthlyLimit(account.getMonthlyLimit())
                .dailySpending(account.getDailySpending())
                .monthlySpending(account.getMonthlySpending())
                .maintenanceFee(account.getMaintenanceFee())
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



    private String getAuthenticatedEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("User is not authenticated.");
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        } else if (principal instanceof String s) {
            return s;
        }
        throw new IllegalStateException("Unable to determine user email from security context.");
    }

    private Client getOptionalClient() {
        try {
            String email = getAuthenticatedEmail();
            return clientRepository.findByEmail(email).orElse(null);
        } catch (Exception e) {
            log.warn("Error processing account operation", e);
            return null;
        }
    }
}
