package rs.raf.banka2_bek.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.employee.model.Employee;
import rs.raf.banka2_bek.employee.repository.EmployeeRepository;
import rs.raf.banka2_bek.notification.service.MailSenderService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Account lockout posle X neuspesnih login pokusaja — stanje se cuva u bazi
 * na entitetima {@link User} i {@link Employee}.
 */
@Slf4j
@Service
public class AccountLockoutService {

    @Value("${auth.lockout.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${auth.lockout.lock-duration-minutes:10}")
    private int lockDurationMinutes;

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final MailSenderService mailSenderService;

    public AccountLockoutService(UserRepository userRepository,
                                 EmployeeRepository employeeRepository,
                                 MailSenderService mailSenderService) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.mailSenderService = mailSenderService;
    }

    /**
     * Baca {@link AccountLockedException} ako je email trenutno lock-ovan.
     * Treba pozvati pre nego sto se proveri lozinka.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assertNotLocked(String email) {
        if (email == null) return;
        LockableAccount account = findAccount(email).orElse(null);
        if (account == null) return;

        if (isLockActive(account.getLockedUntil())) {
            long secondsRemaining = secondsUntil(account.getLockedUntil());
            throw new AccountLockedException(formatLockoutMessage(secondsRemaining), secondsRemaining);
        }

        if (account.getLockedUntil() != null) {
            account.clearLockExpiry();
            account.save();
        }
    }

    /**
     * Belezi neuspesan pokusaj i lockuje racun ako je dosegnut prag.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = AccountLockedException.class)
    public void recordFailure(String email) {
        if (email == null) return;
        LockableAccount account = findAccount(email).orElse(null);
        if (account == null) return;

        if (isLockActive(account.getLockedUntil())) {
            long secondsRemaining = secondsUntil(account.getLockedUntil());
            throw new AccountLockedException(formatLockoutMessage(secondsRemaining), secondsRemaining);
        }

        int attempts = account.getFailedAttempts() + 1;
        account.setFailedAttempts(attempts);

        if (attempts >= maxFailedAttempts) {
            LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(lockDurationMinutes);
            account.setLockedUntil(lockUntil);
            account.save();
            log.warn("Account locked: {} ({} failed attempts)", normalize(email), attempts);
            try {
                mailSenderService.sendAccountLockedMail(account.getEmail(), lockDurationMinutes);
            } catch (Exception e) {
                log.error("Failed to send account locked email to {}", account.getEmail(), e);
            }
            long secondsRemaining = secondsUntil(lockUntil);
            throw new AccountLockedException(formatLockoutMessage(secondsRemaining), secondsRemaining);
        }

        account.save();
    }

    /**
     * Resetuje brojac i otkljucava nalog (po uspesnom login-u ili resetu lozinke).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String email) {
        if (email == null) return;
        LockableAccount account = findAccount(email).orElse(null);
        if (account == null) return;

        account.setFailedAttempts(0);
        account.setLockedUntil(null);
        account.save();
    }

    /** Vraca trenutni broj neuspesnih pokusaja; 0 ako nalog ne postoji. */
    @Transactional(readOnly = true)
    public int getFailureCount(String email) {
        if (email == null) return 0;
        return findAccount(email)
                .map(LockableAccount::getFailedAttempts)
                .orElse(0);
    }

    /** Vraca true ako je email trenutno lock-ovan. */
    @Transactional(readOnly = true)
    public boolean isLocked(String email) {
        if (email == null) return false;
        return findAccount(email)
                .map(a -> isLockActive(a.getLockedUntil()))
                .orElse(false);
    }

    private Optional<LockableAccount> findAccount(String email) {
        String normalized = normalize(email);
        Optional<Employee> employee = employeeRepository.findByEmail(normalized);
        if (employee.isEmpty()) {
            employee = employeeRepository.findByEmail(email.trim());
        }
        if (employee.isPresent()) {
            return Optional.of(new EmployeeLockable(employee.get(), employeeRepository));
        }

        Optional<User> user = userRepository.findByEmail(normalized);
        if (user.isEmpty()) {
            user = userRepository.findByEmail(email.trim());
        }
        return user.map(u -> new UserLockable(u, userRepository));
    }

    private boolean isLockActive(LocalDateTime lockedUntil) {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    private long secondsUntil(LocalDateTime lockedUntil) {
        return Duration.between(LocalDateTime.now(), lockedUntil).getSeconds();
    }

    private String formatLockoutMessage(long secondsRemaining) {
        long minutes = Math.max(1, secondsRemaining / 60 + (secondsRemaining % 60 > 0 ? 1 : 0));
        return "Nalog je privremeno zakljucan zbog previse neuspesnih pokusaja. "
                + "Pokusajte ponovo za " + minutes + " min.";
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private interface LockableAccount {
        String getEmail();
        int getFailedAttempts();
        void setFailedAttempts(int attempts);
        LocalDateTime getLockedUntil();
        void setLockedUntil(LocalDateTime until);
        void clearLockExpiry();
        void save();
    }

    private static final class UserLockable implements LockableAccount {
        private final User user;
        private final UserRepository repository;

        UserLockable(User user, UserRepository repository) {
            this.user = user;
            this.repository = repository;
        }

        @Override
        public String getEmail() {
            return user.getEmail();
        }

        @Override
        public int getFailedAttempts() {
            return user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() : 0;
        }

        @Override
        public void setFailedAttempts(int attempts) {
            user.setFailedLoginAttempts(attempts);
        }

        @Override
        public LocalDateTime getLockedUntil() {
            return user.getAccountLockedUntil();
        }

        @Override
        public void setLockedUntil(LocalDateTime until) {
            user.setAccountLockedUntil(until);
        }

        @Override
        public void clearLockExpiry() {
            user.setAccountLockedUntil(null);
        }

        @Override
        public void save() {
            repository.save(user);
        }
    }

    private static final class EmployeeLockable implements LockableAccount {
        private final Employee employee;
        private final EmployeeRepository repository;

        EmployeeLockable(Employee employee, EmployeeRepository repository) {
            this.employee = employee;
            this.repository = repository;
        }

        @Override
        public String getEmail() {
            return employee.getEmail();
        }

        @Override
        public int getFailedAttempts() {
            return employee.getFailedLoginAttempts() != null ? employee.getFailedLoginAttempts() : 0;
        }

        @Override
        public void setFailedAttempts(int attempts) {
            employee.setFailedLoginAttempts(attempts);
        }

        @Override
        public LocalDateTime getLockedUntil() {
            return employee.getAccountLockedUntil();
        }

        @Override
        public void setLockedUntil(LocalDateTime until) {
            employee.setAccountLockedUntil(until);
        }

        @Override
        public void clearLockExpiry() {
            employee.setAccountLockedUntil(null);
        }

        @Override
        public void save() {
            repository.save(employee);
        }
    }

    /**
     * Throw-ovan kad korisnik pokusa login na lock-ovan racun.
     */
    public static class AccountLockedException extends RuntimeException {
        private final long secondsRemaining;

        public AccountLockedException(String message, long secondsRemaining) {
            super(message);
            this.secondsRemaining = secondsRemaining;
        }

        public long getSecondsRemaining() {
            return secondsRemaining;
        }
    }
}
