package rs.raf.banka2_bek.interbank.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class InterbankReservationApplier {

    private final AccountRepository accountRepository;
    private final PortfolioRepository portfolioRepository;

    public void reserveMonas(String accountNumber, BigDecimal amount){
        Account acct = accountRepository.findForUpdateByAccountNumber(accountNumber)
                .orElseThrow(
                        () -> new InterbankExceptions.InterbankProtocolException("NO_SUCH_ACCOUNT: " + accountNumber)
                );

        if (acct.getAvailableBalance().compareTo(amount) < 0) {
            throw new InterbankExceptions.InterbankProtocolException("INSUFFICIENT_ASSET on " + accountNumber);
        }

        acct.setAvailableBalance(acct.getAvailableBalance().subtract(amount));
        acct.setReservedAmount(acct.getReservedAmount().add(amount));
        accountRepository.save(acct);

    }

    public void releaseMonas(String accountNumber, BigDecimal amount){
        Account acct = accountRepository.findForUpdateByAccountNumber(accountNumber)
                .orElseThrow(
                        () -> new InterbankExceptions.InterbankProtocolException("NO_SUCH_ACCOUNT: " + accountNumber)
                );

        acct.setAvailableBalance(acct.getAvailableBalance().add(amount));
        acct.setReservedAmount(acct.getReservedAmount().subtract(amount));
        accountRepository.save(acct);

    }

    public void commitMonas(String accountNumber, BigDecimal amount, boolean isDebit){
        Account acct = accountRepository.findForUpdateByAccountNumber(accountNumber)
                .orElseThrow(
                        () -> new InterbankExceptions.InterbankProtocolException("NO_SUCH_ACCOUNT: " + accountNumber)
                );

        if (isDebit) {
            acct.setBalance(acct.getBalance().add(amount));
            acct.setAvailableBalance(acct.getAvailableBalance().add(amount));
        }
        else {
            acct.setBalance(acct.getBalance().subtract(amount));
            acct.setReservedAmount(acct.getReservedAmount().subtract(amount));
        }
        accountRepository.save(acct);
    }

    public void reserveStock(Long userId, String role, Long listingId, int quantity){
        Portfolio portfolio = portfolioRepository
                .findByUserIdAndUserRoleAndListingIdForUpdate(userId, role, listingId)
                .orElseThrow(
                        () -> new InterbankExceptions.InterbankProtocolException("NO_SUCH_ASSET: no portfolio exists for listing " + listingId)
                );
        if (portfolio.getAvailableQuantity() < quantity)
            throw new InterbankExceptions.InterbankProtocolException("INSUFFICIENT_QUANTITY on listing" + listingId + ". Only " + portfolio.getAvailableQuantity() + " quantity available.");

        portfolio.setReservedQuantity(portfolio.getReservedQuantity() + quantity);
        portfolioRepository.save(portfolio);
    }

    public void releaseStock(Long userId, String role, Long listingId, int quantity){
        Portfolio portfolio = portfolioRepository
                .findByUserIdAndUserRoleAndListingIdForUpdate(userId, role, listingId)
                .orElseThrow(
                        () -> new InterbankExceptions.InterbankProtocolException("NO_SUCH_ASSET: no portfolio exists for listing " + listingId)
                );
        portfolio.setReservedQuantity(Math.max(0, portfolio.getReservedQuantity() - quantity));
        portfolioRepository.save(portfolio);
    }

    public void commitStock(Long userId, String role, Long listingId, int quantity, boolean isDebit){
        if (isDebit) {
            Optional<Portfolio> current = portfolioRepository.findByUserIdAndUserRoleAndListingIdForUpdate(userId, role, listingId);
            if (current.isPresent()) {
                Portfolio portfolio = current.get();
                portfolio.setQuantity(portfolio.getQuantity() + quantity);
                portfolioRepository.save(portfolio);
            } else {
                Portfolio portfolio = Portfolio.builder()
                        .userId(userId)
                        .userRole(role)
                        .listingId(listingId)
                        .quantity(quantity)
                        .reservedQuantity(0)
                        .publicQuantity(0)
                        .build();
                portfolioRepository.save(portfolio);
            }
        }
        else {
            Portfolio portfolio = portfolioRepository.findByUserIdAndUserRoleAndListingIdForUpdate(userId, role, listingId)
                  .orElseThrow(
                          () -> new InterbankExceptions.InterbankProtocolException("NO_SUCH_ASSET: no portfolio exists for listing " + listingId)
                  );
            portfolio.setQuantity(portfolio.getQuantity() - quantity);
            portfolio.setReservedQuantity(Math.max(0, portfolio.getReservedQuantity() - quantity));
            portfolioRepository.save(portfolio);

        }
    }
}
