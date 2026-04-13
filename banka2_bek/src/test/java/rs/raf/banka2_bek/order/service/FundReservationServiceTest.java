package rs.raf.banka2_bek.order.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.order.exception.InsufficientFundsException;
import rs.raf.banka2_bek.order.exception.InsufficientHoldingsException;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundReservationServiceTest {

    @Mock
    AccountRepository accountRepository;

    @Mock
    PortfolioRepository portfolioRepository;

    @InjectMocks
    FundReservationService service;

    // ── BUY helpers ──────────────────────────────────────────────────────────
    private Account buyAccount(BigDecimal balance, BigDecimal available, BigDecimal reserved) {
        Account a = new Account();
        a.setId(1L);
        a.setAccountNumber("111");
        a.setBalance(balance);
        a.setAvailableBalance(available);
        a.setReservedAmount(reserved);
        return a;
    }

    private Order buyOrder(BigDecimal reservedAmount, Integer qty) {
        Order o = new Order();
        o.setId(100L);
        o.setReservedAmount(reservedAmount);
        o.setReservedAccountId(1L);
        o.setQuantity(qty);
        o.setRemainingPortions(qty);
        o.setReservationReleased(false);
        return o;
    }

    // ── reserveForBuy ────────────────────────────────────────────────────────
    @Test
    void reserveForBuy_reducesAvailableBalance_increasesReservedAmount() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO);
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        service.reserveForBuy(order, account);

        assertThat(account.getAvailableBalance()).isEqualByComparingTo("7500.00");
        assertThat(account.getReservedAmount()).isEqualByComparingTo("2500.00");
        assertThat(account.getBalance()).isEqualByComparingTo("10000.00");
        verify(accountRepository).save(account);
    }

    @Test
    void reserveForBuy_throwsInsufficientFundsException_whenAvailableBalanceTooLow() {
        Account account = buyAccount(new BigDecimal("1000.00"), new BigDecimal("1000.00"), BigDecimal.ZERO);
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.reserveForBuy(order, account))
                .isInstanceOf(InsufficientFundsException.class);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void reserveForBuy_throwsIllegalStateException_whenReservationAlreadyReleased() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO);
        Order order = buyOrder(new BigDecimal("2500.00"), 10);
        order.setReservationReleased(true);

        assertThatThrownBy(() -> service.reserveForBuy(order, account))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── releaseForBuy ────────────────────────────────────────────────────────
    @Test
    void releaseForBuy_restoresAvailableBalance_zerosReservation_setsReleasedFlag() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("7500.00"), new BigDecimal("2500.00"));
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        service.releaseForBuy(order);

        assertThat(account.getAvailableBalance()).isEqualByComparingTo("10000.00");
        assertThat(account.getReservedAmount()).isEqualByComparingTo("0.00");
        assertThat(order.isReservationReleased()).isTrue();
        verify(accountRepository).save(account);
    }

    @Test
    void releaseForBuy_isIdempotent_whenAlreadyReleased() {
        Order order = buyOrder(new BigDecimal("2500.00"), 10);
        order.setReservationReleased(true);

        service.releaseForBuy(order);

        verify(accountRepository, never()).findForUpdateById(any());
        verify(accountRepository, never()).save(any());
        assertThat(order.isReservationReleased()).isTrue();
    }

    // ── consumeForBuyFill ────────────────────────────────────────────────────
    @Test
    void consumeForBuyFill_reducesBalanceAndReservationProportionally() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("7500.00"), new BigDecimal("2500.00"));
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        service.consumeForBuyFill(order, 4, new BigDecimal("1000.00"));

        // balance = 10000 - 1000 (stvarna cena fill-a)
        assertThat(account.getBalance()).isEqualByComparingTo("9000.00");
        // reserved smanjen proporcionalno: 4/10 * 2500 = 1000 → reserved 2500 - 1000 = 1500
        assertThat(account.getReservedAmount()).isEqualByComparingTo("1500.00");
        verify(accountRepository).save(account);
    }

    @Test
    void consumeForBuyFill_releasesFullyWhenLastPortion() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("7500.00"), new BigDecimal("2500.00"));
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        service.consumeForBuyFill(order, 10, new BigDecimal("2400.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("7600.00");
        assertThat(account.getReservedAmount()).isEqualByComparingTo("0.00");
    }

    // ── reserveForSell ───────────────────────────────────────────────────────
    private Portfolio portfolio(int quantity, int reserved) {
        Portfolio p = new Portfolio();
        p.setId(1L);
        p.setUserId(42L);
        p.setListingId(7L);
        p.setQuantity(quantity);
        p.setReservedQuantity(reserved);
        return p;
    }

    @Test
    void reserveForSell_increasesReservedQuantity() {
        Portfolio p = portfolio(30, 0);
        Order order = new Order();
        order.setId(200L);
        order.setQuantity(5);
        order.setRemainingPortions(5);

        service.reserveForSell(order, p);

        assertThat(p.getReservedQuantity()).isEqualTo(5);
        assertThat(p.getAvailableQuantity()).isEqualTo(25);
        verify(portfolioRepository).save(p);
    }

    @Test
    void reserveForSell_throwsInsufficientHoldings_whenAvailableQuantityTooLow() {
        Portfolio p = portfolio(30, 27);
        Order order = new Order();
        order.setQuantity(5);

        assertThatThrownBy(() -> service.reserveForSell(order, p))
                .isInstanceOf(InsufficientHoldingsException.class);

        verify(portfolioRepository, never()).save(any());
    }

    // ── releaseForSell ───────────────────────────────────────────────────────
    @Test
    void releaseForSell_decreasesReservedQuantity_setsReleasedFlag() {
        Portfolio p = portfolio(30, 5);
        Order order = new Order();
        order.setId(201L);
        order.setQuantity(5);
        order.setRemainingPortions(5);
        order.setReservationReleased(false);

        service.releaseForSell(order, p);

        assertThat(p.getReservedQuantity()).isEqualTo(0);
        assertThat(order.isReservationReleased()).isTrue();
        verify(portfolioRepository).save(p);
    }

    @Test
    void releaseForSell_isIdempotent() {
        Portfolio p = portfolio(30, 5);
        Order order = new Order();
        order.setQuantity(5);
        order.setRemainingPortions(5);
        order.setReservationReleased(true);

        service.releaseForSell(order, p);

        assertThat(p.getReservedQuantity()).isEqualTo(5);
        verify(portfolioRepository, never()).save(any());
    }

    // ── consumeForSellFill ───────────────────────────────────────────────────
    @Test
    void consumeForSellFill_reducesQuantityAndReservedProportionally() {
        Portfolio p = portfolio(30, 5);
        Order order = new Order();
        order.setId(202L);
        order.setQuantity(5);
        order.setRemainingPortions(5);

        service.consumeForSellFill(order, p, 2);

        assertThat(p.getQuantity()).isEqualTo(28);
        assertThat(p.getReservedQuantity()).isEqualTo(3);
        verify(portfolioRepository).save(p);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Phase 2: edge cases to reach >95% coverage
    // ════════════════════════════════════════════════════════════════════════

    // ── reserveForBuy edge cases ─────────────────────────────────────────────
    @Test
    void reserveForBuy_throwsIllegalArgument_whenReservedAmountNull() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO);
        Order order = buyOrder(null, 10);

        assertThatThrownBy(() -> service.reserveForBuy(order, account))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pozitivan");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void reserveForBuy_throwsIllegalArgument_whenReservedAmountZero() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO);
        Order order = buyOrder(BigDecimal.ZERO, 10);

        assertThatThrownBy(() -> service.reserveForBuy(order, account))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reserveForBuy_throwsIllegalArgument_whenReservedAmountNegative() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO);
        Order order = buyOrder(new BigDecimal("-5.00"), 10);

        assertThatThrownBy(() -> service.reserveForBuy(order, account))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reserveForBuy_skipsLock_whenAccountIdNull() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO);
        account.setId(null);
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        service.reserveForBuy(order, account);

        assertThat(account.getAvailableBalance()).isEqualByComparingTo("7500.00");
        assertThat(account.getReservedAmount()).isEqualByComparingTo("2500.00");
        verify(accountRepository, never()).findForUpdateById(any());
        verify(accountRepository).save(account);
    }

    @Test
    void reserveForBuy_usesFallbackAccount_whenLockedNotFound() {
        // findForUpdateById vraca empty → .orElse(account) → radimo sa orig instancom
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO);
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.empty());

        service.reserveForBuy(order, account);

        assertThat(account.getAvailableBalance()).isEqualByComparingTo("7500.00");
        assertThat(account.getReservedAmount()).isEqualByComparingTo("2500.00");
        verify(accountRepository).save(account);
    }

    @Test
    void reserveForBuy_syncsOriginalAccount_whenLockedIsDifferentInstance() {
        // simuliramo da findForUpdateById vrati razliciti (svezi) objekat
        Account originalAccount = buyAccount(new BigDecimal("10000.00"), new BigDecimal("500.00"), BigDecimal.ZERO);
        Account freshLocked = buyAccount(new BigDecimal("10000.00"), new BigDecimal("10000.00"), BigDecimal.ZERO);
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(freshLocked));

        service.reserveForBuy(order, originalAccount);

        // original objekat (prosledjen pozivaocu) mora biti sinhronizovan
        assertThat(originalAccount.getAvailableBalance()).isEqualByComparingTo("7500.00");
        assertThat(originalAccount.getReservedAmount()).isEqualByComparingTo("2500.00");
        assertThat(freshLocked.getAvailableBalance()).isEqualByComparingTo("7500.00");
        verify(accountRepository).save(freshLocked);
    }

    // ── releaseForBuy edge cases ─────────────────────────────────────────────
    @Test
    void releaseForBuy_marksReleased_whenReservedAccountIdIsNull() {
        Order order = buyOrder(new BigDecimal("2500.00"), 10);
        order.setReservedAccountId(null);

        service.releaseForBuy(order);

        assertThat(order.isReservationReleased()).isTrue();
        verify(accountRepository, never()).findForUpdateById(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void releaseForBuy_marksReleased_whenReservedAmountIsNull() {
        Order order = buyOrder(null, 10);

        service.releaseForBuy(order);

        assertThat(order.isReservationReleased()).isTrue();
        verify(accountRepository, never()).findForUpdateById(any());
    }

    @Test
    void releaseForBuy_throwsEntityNotFound_whenAccountMissing() {
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.releaseForBuy(order))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("1");
    }

    @Test
    void releaseForBuy_withPartialFill_releasesOnlyProportionalRemainingReservation() {
        // Order kreiran za 10 kom, ali je 4 kom vec fill-ovano → remaining = 6
        // Rezervacija originalno 2500, proporcija 6/10 = 1500 treba da se oslobodi.
        Account account = buyAccount(new BigDecimal("9000.00"), new BigDecimal("7500.00"), new BigDecimal("1500.00"));
        Order order = buyOrder(new BigDecimal("2500.00"), 10);
        order.setRemainingPortions(6);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        service.releaseForBuy(order);

        assertThat(account.getAvailableBalance()).isEqualByComparingTo("9000.00");
        assertThat(account.getReservedAmount()).isEqualByComparingTo("0.0000");
        assertThat(order.isReservationReleased()).isTrue();
    }

    @Test
    void releaseForBuy_isIdempotent_whenCalledTwice() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("7500.00"), new BigDecimal("2500.00"));
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        service.releaseForBuy(order);
        // drugi poziv → zbog flag-a odmah izlazi
        service.releaseForBuy(order);

        // save se desio samo jednom u prvom pozivu
        verify(accountRepository).save(account);
        verify(accountRepository, times(1)).findForUpdateById(1L);
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("10000.00");
        assertThat(account.getReservedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void releaseForBuy_currentReservedOnOrder_cappedByAccountReservedAmount() {
        // Account reserved je nizi od proracunate proporcije → mora biti capped
        Account account = buyAccount(new BigDecimal("9000.00"), new BigDecimal("8900.00"), new BigDecimal("100.00"));
        Order order = buyOrder(new BigDecimal("2500.00"), 10);
        // Puna rezervacija grana: remaining == quantity → vraca total.min(reserved) = 100
        order.setRemainingPortions(10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        service.releaseForBuy(order);

        assertThat(account.getAvailableBalance()).isEqualByComparingTo("9000.00");
        assertThat(account.getReservedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void releaseForBuy_currentReservedOnOrder_whenQuantityNull_treatedAsFull() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("7500.00"), new BigDecimal("2500.00"));
        Order order = buyOrder(new BigDecimal("2500.00"), null);
        order.setRemainingPortions(null);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        service.releaseForBuy(order);

        assertThat(account.getAvailableBalance()).isEqualByComparingTo("10000.00");
        assertThat(account.getReservedAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void releaseForBuy_currentReservedOnOrder_whenQuantityZero_treatedAsFull() {
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("7500.00"), new BigDecimal("2500.00"));
        Order order = buyOrder(new BigDecimal("2500.00"), 0);
        order.setRemainingPortions(0);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        service.releaseForBuy(order);

        assertThat(account.getReservedAmount()).isEqualByComparingTo("0.00");
    }

    // ── consumeForBuyFill edge cases ─────────────────────────────────────────
    @Test
    void consumeForBuyFill_throwsIllegalState_whenAlreadyReleased() {
        Order order = buyOrder(new BigDecimal("2500.00"), 10);
        order.setReservationReleased(true);

        assertThatThrownBy(() -> service.consumeForBuyFill(order, 5, new BigDecimal("1000.00")))
                .isInstanceOf(IllegalStateException.class);

        verify(accountRepository, never()).findForUpdateById(any());
    }

    @Test
    void consumeForBuyFill_throwsIllegalArgument_whenQtyZero() {
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        assertThatThrownBy(() -> service.consumeForBuyFill(order, 0, new BigDecimal("1000.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consumeForBuyFill_throwsIllegalArgument_whenQtyNegative() {
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        assertThatThrownBy(() -> service.consumeForBuyFill(order, -3, new BigDecimal("1000.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consumeForBuyFill_throwsEntityNotFound_whenAccountMissing() {
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumeForBuyFill(order, 4, new BigDecimal("1000.00")))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void consumeForBuyFill_capsReservedPortion_atCurrentReserved_whenRoundingOvershoots() {
        // Scenario: accountReserved je vec manji nego izracunata proporcija →
        // rezervacija NE sme ici u minus.
        Account account = buyAccount(new BigDecimal("10000.00"), new BigDecimal("9700.00"), new BigDecimal("200.00"));
        Order order = buyOrder(new BigDecimal("2500.00"), 10);

        when(accountRepository.findForUpdateById(1L)).thenReturn(Optional.of(account));

        // 4/10 * 2500 = 1000, ali reserved je samo 200 → cap na 200
        service.consumeForBuyFill(order, 4, new BigDecimal("1000.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("9000.00");
        assertThat(account.getReservedAmount()).isEqualByComparingTo("0.00");
    }

    // ── reserveForSell edge cases ────────────────────────────────────────────
    @Test
    void reserveForSell_throwsIllegalState_whenAlreadyReleased() {
        Portfolio p = portfolio(30, 0);
        Order order = new Order();
        order.setId(300L);
        order.setQuantity(5);
        order.setReservationReleased(true);

        assertThatThrownBy(() -> service.reserveForSell(order, p))
                .isInstanceOf(IllegalStateException.class);

        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void reserveForSell_throwsIllegalArgument_whenQuantityZero() {
        Portfolio p = portfolio(30, 0);
        Order order = new Order();
        order.setQuantity(0);

        assertThatThrownBy(() -> service.reserveForSell(order, p))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reserveForSell_throwsIllegalArgument_whenQuantityNegative() {
        Portfolio p = portfolio(30, 0);
        Order order = new Order();
        order.setQuantity(-2);

        assertThatThrownBy(() -> service.reserveForSell(order, p))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── releaseForSell edge cases ────────────────────────────────────────────
    @Test
    void releaseForSell_usesRemainingPortions_whenNotNull() {
        Portfolio p = portfolio(30, 10);
        Order order = new Order();
        order.setQuantity(10);
        order.setRemainingPortions(3); // 7 vec fill-ovano → oslobodi samo 3
        order.setReservationReleased(false);

        service.releaseForSell(order, p);

        assertThat(p.getReservedQuantity()).isEqualTo(7);
        assertThat(order.isReservationReleased()).isTrue();
    }

    @Test
    void releaseForSell_fallsBackToQuantity_whenRemainingPortionsNull() {
        Portfolio p = portfolio(30, 5);
        Order order = new Order();
        order.setQuantity(5);
        order.setRemainingPortions(null);
        order.setReservationReleased(false);

        service.releaseForSell(order, p);

        assertThat(p.getReservedQuantity()).isEqualTo(0);
        assertThat(order.isReservationReleased()).isTrue();
        verify(portfolioRepository).save(p);
    }

    @Test
    void releaseForSell_clampsToZero_whenReservedLessThanToRelease() {
        // Guard: Math.max(0, ...) branch
        Portfolio p = portfolio(30, 2);
        Order order = new Order();
        order.setQuantity(5);
        order.setRemainingPortions(5);
        order.setReservationReleased(false);

        service.releaseForSell(order, p);

        assertThat(p.getReservedQuantity()).isEqualTo(0);
    }

    // ── consumeForSellFill edge cases ────────────────────────────────────────
    @Test
    void consumeForSellFill_throwsIllegalState_whenAlreadyReleased() {
        Portfolio p = portfolio(30, 5);
        Order order = new Order();
        order.setQuantity(5);
        order.setReservationReleased(true);

        assertThatThrownBy(() -> service.consumeForSellFill(order, p, 2))
                .isInstanceOf(IllegalStateException.class);

        verify(portfolioRepository, never()).save(any());
    }

    @Test
    void consumeForSellFill_throwsIllegalArgument_whenQtyZero() {
        Portfolio p = portfolio(30, 5);
        Order order = new Order();
        order.setQuantity(5);

        assertThatThrownBy(() -> service.consumeForSellFill(order, p, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consumeForSellFill_throwsIllegalArgument_whenQtyNegative() {
        Portfolio p = portfolio(30, 5);
        Order order = new Order();
        order.setQuantity(5);

        assertThatThrownBy(() -> service.consumeForSellFill(order, p, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consumeForSellFill_clampsReservedToZero_whenQtyBiggerThanReserved() {
        // Math.max(0, ...) branch
        Portfolio p = portfolio(30, 2);
        Order order = new Order();
        order.setQuantity(10);
        order.setRemainingPortions(10);

        service.consumeForSellFill(order, p, 5);

        assertThat(p.getQuantity()).isEqualTo(25);
        assertThat(p.getReservedQuantity()).isEqualTo(0);
        verify(portfolioRepository).save(p);
    }
}
