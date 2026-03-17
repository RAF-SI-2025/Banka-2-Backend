package rs.raf.banka2_bek.account.service;

import rs.raf.banka2_bek.account.dto.AccountResponseDto;

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
}
