package rs.raf.banka2_bek.savings.mapper;

import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.auth.util.UserResolver;
import rs.raf.banka2_bek.auth.util.UserRole;
import rs.raf.banka2_bek.savings.dto.*;
import rs.raf.banka2_bek.savings.entity.*;

@Component
public class SavingsMapper {

    private final AccountRepository accountRepository;
    private final UserResolver userResolver;

    public SavingsMapper(AccountRepository accountRepository, UserResolver userResolver) {
        this.accountRepository = accountRepository;
        this.userResolver = userResolver;
    }

    public SavingsDepositDto toDepositDto(SavingsDeposit d) {
        if (d == null) return null;
        String accountNumber = accountRepository.findById(d.getLinkedAccountId())
                .map(Account::getAccountNumber).orElse(null);
        String clientName = userResolver.resolveName(d.getClientId(), UserRole.CLIENT);
        return SavingsDepositDto.builder()
                .id(d.getId())
                .clientId(d.getClientId())
                .clientName(clientName)
                .linkedAccountId(d.getLinkedAccountId())
                .linkedAccountNumber(accountNumber)
                .principalAmount(d.getPrincipalAmount())
                .currencyCode(d.getCurrency().getCode())
                .termMonths(d.getTermMonths())
                .annualInterestRate(d.getAnnualInterestRate())
                .startDate(d.getStartDate())
                .maturityDate(d.getMaturityDate())
                .nextInterestPaymentDate(d.getNextInterestPaymentDate())
                .totalInterestPaid(d.getTotalInterestPaid())
                .autoRenew(d.getAutoRenew())
                .status(d.getStatus().name())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }

    public SavingsTransactionDto toTransactionDto(SavingsTransaction t) {
        if (t == null) return null;
        return SavingsTransactionDto.builder()
                .id(t.getId())
                .depositId(t.getDeposit().getId())
                .type(t.getType().name())
                .amount(t.getAmount())
                .currencyCode(t.getCurrency().getCode())
                .processedDate(t.getProcessedDate())
                .resultingTransactionId(t.getResultingTransactionId())
                .description(t.getDescription())
                .createdAt(t.getCreatedAt())
                .build();
    }

    public SavingsRateDto toRateDto(SavingsInterestRate r) {
        if (r == null) return null;
        return SavingsRateDto.builder()
                .id(r.getId())
                .currencyCode(r.getCurrency().getCode())
                .termMonths(r.getTermMonths())
                .annualRate(r.getAnnualRate())
                .active(r.getActive())
                .effectiveFrom(r.getEffectiveFrom())
                .build();
    }
}
