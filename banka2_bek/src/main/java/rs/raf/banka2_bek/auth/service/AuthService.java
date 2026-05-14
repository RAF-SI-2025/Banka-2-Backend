package rs.raf.banka2_bek.auth.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountCategory;
import rs.raf.banka2_bek.account.model.AccountStatus;
import rs.raf.banka2_bek.account.model.AccountSubtype;
import rs.raf.banka2_bek.account.model.AccountType;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.account.util.AccountNumberUtils;
import rs.raf.banka2_bek.auth.dto.*;
import rs.raf.banka2_bek.auth.exception.AuthenticationFailedException;
import rs.raf.banka2_bek.auth.model.PasswordResetRequestedEvent;
import rs.raf.banka2_bek.auth.model.PasswordResetToken;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.PasswordResetTokenRepository;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.client.model.Client;
import rs.raf.banka2_bek.client.repository.ClientRepository;
import rs.raf.banka2_bek.currency.model.Currency;
import rs.raf.banka2_bek.currency.repository.CurrencyRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final CurrencyRepository currencyRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;
    private final AccountLockoutService accountLockoutService;

    public AuthService(UserRepository userRepository,
                       EmployeeRepository employeeRepository,
                       ClientRepository clientRepository,
                       AccountRepository accountRepository,
                       CurrencyRepository currencyRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       ApplicationEventPublisher eventPublisher,
                       AccountLockoutService accountLockoutService) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.currencyRepository = currencyRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.eventPublisher = eventPublisher;
        this.accountLockoutService = accountLockoutService;
    }

    /**
     * Po Celini 1 spec-u, self-service register kreira korisnicki nalog za login.
     * Da bi novi klijent odmah mogao da koristi feature-e koji preko
     * {@code UserResolver.resolveCurrent} traze {@code clients} red (BUY orderi,
     * OTC accept, fond invest), ovde se atomski kreira i Client zapis i jedan
     * podrazumevani RSD CHECKING/PERSONAL racun. Bez toga, UserResolver baca
     * {@code IllegalStateException} unutar {@code @Transactional} servisa,
     * pa Spring posle commit-a vraca {@code TransactionSystemException
     * ("Could not commit JPA transaction")} — bag prijavljen 10.05.2026.
     */
    @Transactional
    public String register(RegisterRequestDto request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("User with this email already exists");
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(hashedPassword);
        user.setUsername(request.getUsername());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setGender(request.getGender());
        user.setActive(true);
        user.setRole("CLIENT");

        userRepository.save(user);

        bootstrapClientArtifacts(user, hashedPassword);

        return "User registered successfully";
    }

    /**
     * Kreira odgovarajuci {@link Client} red i podrazumevani RSD CHECKING/PERSONAL
     * racun za novoregistrovanog korisnika. Idempotent — ako Client/racun vec
     * postoje, preskace.
     */
    private void bootstrapClientArtifacts(User user, String hashedPassword) {
        Optional<Client> existingByEmail = clientRepository.findByEmail(user.getEmail());
        Client client = existingByEmail.orElseGet(() -> {
            Client c = Client.builder()
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .email(user.getEmail())
                    .phone(user.getPhone() != null ? user.getPhone() : "N/A")
                    .address(user.getAddress())
                    .gender(user.getGender())
                    .dateOfBirth(user.getDateOfBirth() != null
                            ? Instant.ofEpochMilli(user.getDateOfBirth()).atZone(ZoneId.systemDefault()).toLocalDate()
                            : LocalDate.of(2000, 1, 1))
                    .password(hashedPassword)
                    .saltPassword("auto")
                    .active(true)
                    .build();
            return clientRepository.save(c);
        });

        if (client.getAccounts() != null && !client.getAccounts().isEmpty()) {
            return;
        }
        if (client.getId() != null
                && !accountRepository.findByClientId(client.getId()).isEmpty()) {
            return;
        }

        Currency rsd = currencyRepository.findByCode("RSD")
                .orElseThrow(() -> new IllegalStateException(
                        "RSD valuta nije seedovana — ne moze se otvoriti default racun za novog klijenta."));

        Employee creator = pickSystemEmployee();

        String accountNumber;
        do {
            accountNumber = AccountNumberUtils.generate(AccountType.CHECKING, AccountSubtype.PERSONAL, false);
        } while (accountRepository.existsByAccountNumber(accountNumber));

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .accountType(AccountType.CHECKING)
                .accountSubtype(AccountSubtype.PERSONAL)
                .currency(rsd)
                .client(client)
                .employee(creator)
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .reservedAmount(BigDecimal.ZERO)
                .accountCategory(AccountCategory.CLIENT)
                .dailyLimit(new BigDecimal("250000"))
                .monthlyLimit(new BigDecimal("1000000"))
                .maintenanceFee(new BigDecimal("255"))
                .status(AccountStatus.ACTIVE)
                .expirationDate(LocalDate.now().plusYears(5))
                .createdAt(LocalDateTime.now())
                .build();
        accountRepository.save(account);
    }

    private Employee pickSystemEmployee() {
        return employeeRepository.findAll().stream()
                .filter(e -> Boolean.TRUE.equals(e.getActive()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Nema aktivnih zaposlenih u sistemu — ne moze se kreirati racun za novog klijenta."));
    }

    public AuthResponseDto login(LoginRequestDto request) {
        // Opciono.2 — proveri lockout pre nego sto se proveri lozinka.
        // Bacaja AccountLockedException ako je email lock-ovan; mapira se u 401.
        accountLockoutService.assertNotLocked(request.getEmail());

        // First, try to find an employee with this email
        Optional<Employee> employeeOpt = employeeRepository.findByEmail(request.getEmail());
        if (employeeOpt.isPresent()) {
            Employee employee = employeeOpt.get();

            // Employee passwords are stored with salt concatenated
            String salt = employee.getSaltPassword();
            if (passwordEncoder.matches(request.getPassword() + salt, employee.getPassword())) {
                if (!Boolean.TRUE.equals(employee.getActive())) {
                    // Spec Sc 14 — login deaktiviranog naloga vraca 401, ne 400
                    // (Bug T1-012 prijavljen 12.05.2026).
                    throw new AuthenticationFailedException("Nalog je deaktiviran.");
                }

                accountLockoutService.recordSuccess(request.getEmail());
                String accessToken = jwtService.generateAccessToken(employee);
                String refreshToken = jwtService.generateRefreshToken(employee);
                return new AuthResponseDto(accessToken, refreshToken);
            }
        }

        // If not found as employee or password didn't match, try regular user
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isPresent()) {
            User user = userOpt.get();

            if (passwordEncoder.matches(request.getPassword(), user.getPassword())) {
                if (!user.isActive()) {
                    // Isto kao employee — generic poruka ne otkriva da li email
                    // postoji ili je deaktiviran. Vraca 401.
                    throw new AuthenticationFailedException("Nalog je deaktiviran.");
                }

                accountLockoutService.recordSuccess(request.getEmail());
                String accessToken = jwtService.generateAccessToken(user);
                String refreshToken = jwtService.generateRefreshToken(user);
                return new AuthResponseDto(accessToken, refreshToken);
            }
        }

        // Neither employee nor user found, or password didn't match — broji failure.
        // Spec Sc 5: posle 4 neuspeha, 5. pokusaj zakljucava nalog. AccountLockoutService
        // sad baca AccountLockedException u istom pozivu kad pretreknemo prag — tako
        // korisnik dobija lockout poruku na 5. pokusaju (Bug T1-015), ne tek na 6.
        accountLockoutService.recordFailure(request.getEmail());
        // Spec Sc 2/3: 401 sa generic porukom "Neispravni unos" (poruka na SR jer
        // ostatak UI je na SR — Bug T1-017 mesan SR/EN).
        throw new AuthenticationFailedException("Neispravan email ili lozinka.");
    }

    public String requestPasswordReset(PasswordResetRequestDto request) {
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        Employee employee = null;

        if (user == null) {
            employee = employeeRepository.findByEmail(request.getEmail()).orElse(null);
        }

        if (user == null && employee == null) {
            throw new RuntimeException("User with this email does not exist");
        }

        String tokenValue = UUID.randomUUID().toString();

        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.setToken(tokenValue);
        if (user != null) {
            passwordResetToken.setUser(user);
        } else {
            passwordResetToken.setEmployee(employee);
        }
        passwordResetToken.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        passwordResetToken.setUsed(false);

        passwordResetTokenRepository.save(passwordResetToken);

        String targetEmail = user != null ? user.getEmail() : employee.getEmail();
        eventPublisher.publishEvent(
            new PasswordResetRequestedEvent(targetEmail, tokenValue)
        );

        return "Password reset token generated and email event emitted";
    }

    public String resetPassword(PasswordResetDto reset){

        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByToken(reset.getToken())
                .orElseThrow(() -> new RuntimeException("Reset token does not exist"));


        if(passwordResetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Reset token has expired");
        }

        if(passwordResetToken.getUsed()) {
            throw new RuntimeException("Reset token has been already used");
        }

        User user = passwordResetToken.getUser();
        Employee employee = passwordResetToken.getEmployee();
        if (user == null && employee == null) {
            throw new RuntimeException("Reset token is not linked to a user");
        }

        if (user != null) {
            String hashedNewPassword = passwordEncoder.encode(reset.getNewPassword());
            user.setPassword(hashedNewPassword);
            userRepository.save(user);
        } else {
            String salt = employee.getSaltPassword();
            employee.setPassword(passwordEncoder.encode(reset.getNewPassword() + salt));
            employeeRepository.save(employee);
        }
        passwordResetToken.setUsed(true);
        passwordResetTokenRepository.save(passwordResetToken);
        return "Password reset successfully!";

    }

    public RefreshTokenResponseDto  refreshToken(RefreshTokenRequestDto  request){
        String refreshToken = request.getRefreshToken();

        if (!jwtService.isRefreshToken(refreshToken))
            throw new RuntimeException("Invalid refresh token");

        String email = jwtService.extractEmail(refreshToken);

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
            String newAccessToken = jwtService.generateAccessToken(employee);
            return new RefreshTokenResponseDto(newAccessToken, refreshToken);
        }

        String newAccessToken = jwtService.generateAccessToken(user);

        return new RefreshTokenResponseDto(newAccessToken, refreshToken);
    }
}