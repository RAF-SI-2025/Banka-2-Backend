package rs.raf.banka2_bek.account.service;

import rs.raf.banka2_bek.account.dto.AccountResponseDto;

import java.math.BigDecimal;
import java.util.List;

public interface AccountService {
    /**
     * Returns a list of active accounts for the currently authenticated client,
     * sorted by available balance in descending order.
     *
     * @return list of account response DTOs
     * @throws IllegalStateException if the authenticated user is not a client
     */
    List<AccountResponseDto> getMyAccounts();

    /**
     * Returns detailed information about a single account.
     * Only the account owner (client) can access it.
     *
     * @param accountId account ID
     * @return account response DTO with full details
     * @throws IllegalArgumentException if account not found
     * @throws IllegalStateException    if the authenticated user is not the account owner
     */
    AccountResponseDto getAccountById(Long accountId);

    /**
     * updates the name of the acc
     * only owner can change it
     * cannot be the same as the old one
     * @param accountId
     * @param newName
     * @return
     */
    AccountResponseDto updateAccountName(Long accountId, String newName);

    /**
     * updating limit for acc
     * @param accountId
     * @param dailyLimit
     * @param monthlyLimit
     * @return
     */

    AccountResponseDto updateAccountLimits(Long accountId, BigDecimal dailyLimit, BigDecimal monthlyLimit);
}
