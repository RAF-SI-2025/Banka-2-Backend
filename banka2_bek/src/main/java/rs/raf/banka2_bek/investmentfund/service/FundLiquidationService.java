package rs.raf.banka2_bek.investmentfund.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.account.model.Account;
import rs.raf.banka2_bek.account.model.AccountCategory;
import rs.raf.banka2_bek.account.repository.AccountRepository;
import rs.raf.banka2_bek.investmentfund.model.ClientFundTransaction;
import rs.raf.banka2_bek.investmentfund.model.ClientFundTransactionStatus;
import rs.raf.banka2_bek.investmentfund.repository.ClientFundTransactionRepository;
import rs.raf.banka2_bek.order.model.Order;
import rs.raf.banka2_bek.order.model.OrderDirection;
import rs.raf.banka2_bek.order.model.OrderStatus;
import rs.raf.banka2_bek.order.model.OrderType;
import rs.raf.banka2_bek.order.repository.OrderRepository;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FundLiquidationService {

    private static final Logger log = LoggerFactory.getLogger(FundLiquidationService.class);

    private final OrderRepository orderRepository;
    private final PortfolioRepository portfolioRepository;
    private final ListingRepository listingRepository;
    private final ClientFundTransactionRepository transactionRepository;
    private final AccountRepository accountRepository;


    @Transactional
    public void liquidateFor(Long fundId, BigDecimal amountRsd) {
        sendPushNotification(fundId, "Vaša isplata je pokrenuta. Zbog nedovoljno sredstava, vrši se automatska prodaja hartija od vrednosti. Isplata će biti završena ubrzo.");

        // 1. Dobijamo listu i odmah pravimo ArrayList (da bi bila Mutable - promenljiva)
        List<Portfolio> fundHoldings = new ArrayList<>(portfolioRepository.findByUserIdAndUserRole(fundId, "FUND"));

        // 2. Sortiranje: Najvredniji holding (Quantity * Price) ide prvi
        // Eksplicitno definisemo Comparator da izbegnemo "crvenilo" i probleme sa tipovima
        fundHoldings.sort((p1, p2) -> {
            BigDecimal val1 = p1.getQuantity() != null && p1.getAverageBuyPrice() != null
                    ? BigDecimal.valueOf(p1.getQuantity()).multiply(p1.getAverageBuyPrice())
                    : BigDecimal.ZERO;
            BigDecimal val2 = p2.getQuantity() != null && p2.getAverageBuyPrice() != null
                    ? BigDecimal.valueOf(p2.getQuantity()).multiply(p2.getAverageBuyPrice())
                    : BigDecimal.ZERO;
            return val2.compareTo(val1); // Reversed (od najveceg ka najmanjem)
        });

        BigDecimal preostalo = amountRsd;

        for (Portfolio holding : fundHoldings) {
            if (preostalo.compareTo(BigDecimal.ZERO) <= 0) break;

            // Koristimo tiker ili ID zavisno od tvog ListingRepository-a
            Listing listing = listingRepository.findByTicker(holding.getListingTicker())
                    .orElseThrow(() -> new RuntimeException("Listing nije pronadjen za tiker: " + holding.getListingTicker()));

            // Uzimamo Bid cenu (ono sto mozemo odmah dobiti prodajom)
            BigDecimal priceInRsd = convertToRsd(listing.getBid(), "USD");

            // Buffer od 1% da osiguramo da cemo pokriti troskove/promene cene
            BigDecimal bufferPrice = priceInRsd.multiply(new BigDecimal("0.99"));

            if (bufferPrice.compareTo(BigDecimal.ZERO) <= 0) continue;

            // Izracunaj koliko nam komada treba da prodamo
            int quantityToSell = preostalo.divide(bufferPrice, 0, RoundingMode.CEILING).intValue();

            // Ne mozemo prodati vise nego sto imamo
            quantityToSell = Math.min(quantityToSell, holding.getQuantity());

            if (quantityToSell > 0) {
                createInternalFundOrder(fundId, holding, quantityToSell);

                // Azuriramo preostali dug
                BigDecimal ostvarenaVrednost = bufferPrice.multiply(BigDecimal.valueOf(quantityToSell));
                preostalo = preostalo.subtract(ostvarenaVrednost);
            }
        }

        // Ako i nakon prodaje svega nismo skupili dovoljno
        if (preostalo.compareTo(BigDecimal.ZERO) > 0) {
            sendPushNotification(999L, "ALARM: Fond #" + fundId + " nema dovoljno hartija za likvidaciju duga. Fali jos: " + preostalo + " RSD!");
        }
    }

    @Transactional
    public void onFillCompleted(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order nije pronadjen"));

        if (!"FUND".equals(order.getUserRole())) return;

        log.info("Hook: Order #{} (FUND) uspesno fill-ovan. Pokusaj resolve-a PENDING transakcija.", orderId);

        List<ClientFundTransaction> allPending = transactionRepository.findByStatus(ClientFundTransactionStatus.PENDING);

        List<ClientFundTransaction> fundPendingIsplate = allPending.stream()
                .filter(tx -> tx.getFundId().equals(order.getUserId()))
                .filter(tx -> !tx.isInflow())
                .sorted(Comparator.comparing(ClientFundTransaction::getCreatedAt))
                .collect(Collectors.toList());

        Long fundAccountId = order.getReservedAccountId();
        if (fundAccountId == null) return;

        Account fundAccount = accountRepository.findById(fundAccountId)
                .orElseThrow(() -> new RuntimeException("Racun fonda nije pronadjen"));

        for (ClientFundTransaction tx : fundPendingIsplate) {
            if (fundAccount.getBalance().compareTo(tx.getAmountRsd()) >= 0) {
                executeTransactionPayout(tx, fundAccount);
                log.info("Transakcija #{} COMPLETED.", tx.getId());
            } else {
                break;
            }
        }
    }

    private void createInternalFundOrder(Long fundId, Portfolio holding, int quantity) {
        Order fundOrder = new Order();
        fundOrder.setUserId(fundId);
        fundOrder.setUserRole("FUND");
        fundOrder.setListing(listingRepository.findById(holding.getListingId()).get());
        fundOrder.setQuantity(quantity);
        fundOrder.setRemainingPortions(quantity);
        fundOrder.setDirection(OrderDirection.SELL);
        fundOrder.setOrderType(OrderType.MARKET);
        fundOrder.setStatus(OrderStatus.APPROVED);
        fundOrder.setCreatedAt(LocalDateTime.now());
        fundOrder.setDone(false);
        fundOrder.setReservedAccountId(getFundAccount(fundId).getId());

        orderRepository.save(fundOrder);
    }
    private void sendPushNotification(Long userId, String message) {
        // U realnom sistemu ovde bi išao poziv ka Firebase-u ili Apple Push servisu
        log.info("[PUSH NOTIFICATION] Za korisnika #{}: {}", userId, message);
    }
    private void executeTransactionPayout(ClientFundTransaction tx, Account fundAccount) {
        fundAccount.setBalance(fundAccount.getBalance().subtract(tx.getAmountRsd()));
        fundAccount.setAvailableBalance(fundAccount.getAvailableBalance().subtract(tx.getAmountRsd()));

        Account clientAccount = accountRepository.findById(tx.getSourceAccountId())
                .orElseThrow(() -> new RuntimeException("Klijentski racun nije pronadjen"));

        clientAccount.setBalance(clientAccount.getBalance().add(tx.getAmountRsd()));
        clientAccount.setAvailableBalance(clientAccount.getAvailableBalance().add(tx.getAmountRsd()));

        tx.setStatus(ClientFundTransactionStatus.COMPLETED);
        tx.setCompletedAt(LocalDateTime.now());
        sendPushNotification(tx.getUserId(), "Vaša isplata u iznosu od " + tx.getAmountRsd() + " RSD je uspešno procesuirana.");
        accountRepository.save(fundAccount);
        accountRepository.save(clientAccount);
        transactionRepository.save(tx);
    }

    private Account getFundAccount(Long fundId) {
        // Popravljeno: Koristi BANK_TRADING i ispravnu metodu iz repozitorijuma
        return accountRepository.findFirstByAccountCategoryAndCurrency_Code(
                        AccountCategory.BANK_TRADING, "RSD")
                .orElseThrow(() -> new RuntimeException("Glavni RSD racun banke (trading) nije pronadjen"));
    }

    private BigDecimal calculateValueInRsd(Portfolio p) {
        return p.getAverageBuyPrice().multiply(BigDecimal.valueOf(p.getQuantity()));
    }

    private BigDecimal convertToRsd(BigDecimal amount, String fromCurrency) {
        if ("RSD".equals(fromCurrency)) return amount;
        return amount.multiply(new BigDecimal("117"));
    }
}