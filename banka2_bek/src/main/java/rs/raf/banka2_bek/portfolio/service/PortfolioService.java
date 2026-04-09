package rs.raf.banka2_bek.portfolio.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.raf.banka2_bek.auth.model.User;
import rs.raf.banka2_bek.auth.repository.UserRepository;
import rs.raf.banka2_bek.portfolio.dto.PortfolioItemDto;
import rs.raf.banka2_bek.portfolio.dto.PortfolioSummaryDto;
import rs.raf.banka2_bek.portfolio.model.Portfolio;
import rs.raf.banka2_bek.portfolio.repository.PortfolioRepository;
import rs.raf.banka2_bek.stock.model.Listing;
import rs.raf.banka2_bek.stock.repository.ListingRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;

    /**
     * Vraca ID trenutno ulogovanog korisnika na osnovu email-a iz JWT-a.
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Korisnik nije pronadjen: " + email));
        return user.getId();
    }

    /**
     * Vraca listu portfolio stavki za trenutnog korisnika sa izracunatim profitom.
     */
    public List<PortfolioItemDto> getMyPortfolio() {
        Long userId = getCurrentUserId();
        List<Portfolio> portfolios = portfolioRepository.findByUserId(userId);

        return portfolios.stream().map(p -> {
            BigDecimal currentPrice = getCurrentPrice(p.getListingId());
            BigDecimal avgPrice = p.getAverageBuyPrice();
            BigDecimal qty = BigDecimal.valueOf(p.getQuantity());

            // profit = (currentPrice - avgPrice) * quantity
            BigDecimal profit = currentPrice.subtract(avgPrice).multiply(qty);

            // profitPercent = ((currentPrice - avgPrice) / avgPrice) * 100
            BigDecimal profitPercent = BigDecimal.ZERO;
            if (avgPrice.compareTo(BigDecimal.ZERO) != 0) {
                profitPercent = currentPrice.subtract(avgPrice)
                        .divide(avgPrice, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
            }

            PortfolioItemDto dto = new PortfolioItemDto();
            dto.setId(p.getId());
            dto.setListingId(p.getListingId());
            dto.setListingTicker(p.getListingTicker());
            dto.setListingName(p.getListingName());
            dto.setListingType(p.getListingType());
            dto.setQuantity(p.getQuantity());
            dto.setAverageBuyPrice(avgPrice);
            dto.setCurrentPrice(currentPrice);
            dto.setProfit(profit);
            dto.setProfitPercent(profitPercent);
            dto.setPublicQuantity(p.getPublicQuantity());
            dto.setLastModified(p.getLastModified());
            return dto;
        }).toList();
    }

    /**
     * Vraca sumarni pregled portfolija (ukupna vrednost, profit, porez).
     */
    public PortfolioSummaryDto getSummary() {
        List<PortfolioItemDto> items = getMyPortfolio();

        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;

        for (PortfolioItemDto item : items) {
            // totalValue = SUM(currentPrice * quantity)
            BigDecimal itemValue = item.getCurrentPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));
            totalValue = totalValue.add(itemValue);
            totalProfit = totalProfit.add(item.getProfit());
        }

        // Porez na kapitalnu dobit: 15% na pozitivan profit
        BigDecimal taxRate = new BigDecimal("0.15");
        BigDecimal unpaidTax = totalProfit.compareTo(BigDecimal.ZERO) > 0
                ? totalProfit.multiply(taxRate).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new PortfolioSummaryDto(
                totalValue.setScale(2, RoundingMode.HALF_UP),
                totalProfit.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO, // paidTaxThisYear — nema istorije transakcija
                unpaidTax
        );
    }

    /**
     * Azurira javnu kolicinu za datu portfolio stavku.
     * Vraca azuriranu stavku.
     */
    @Transactional
    public PortfolioItemDto setPublicQuantity(Long portfolioId, int quantity) {
        Long userId = getCurrentUserId();

        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new RuntimeException("Portfolio stavka nije pronadjena: " + portfolioId));

        if (!portfolio.getUserId().equals(userId)) {
            throw new RuntimeException("Nemate pristup ovoj portfolio stavci.");
        }

        if (quantity < 0 || quantity > portfolio.getQuantity()) {
            throw new IllegalArgumentException(
                    "Javna kolicina mora biti izmedju 0 i " + portfolio.getQuantity());
        }

        portfolio.setPublicQuantity(quantity);
        portfolioRepository.save(portfolio);

        // Vrati azuriranu stavku
        BigDecimal currentPrice = getCurrentPrice(portfolio.getListingId());
        BigDecimal avgPrice = portfolio.getAverageBuyPrice();
        BigDecimal qty = BigDecimal.valueOf(portfolio.getQuantity());

        BigDecimal profit = currentPrice.subtract(avgPrice).multiply(qty);
        BigDecimal profitPercent = BigDecimal.ZERO;
        if (avgPrice.compareTo(BigDecimal.ZERO) != 0) {
            profitPercent = currentPrice.subtract(avgPrice)
                    .divide(avgPrice, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        PortfolioItemDto dto = new PortfolioItemDto();
        dto.setId(portfolio.getId());
        dto.setListingId(portfolio.getListingId());
        dto.setListingTicker(portfolio.getListingTicker());
        dto.setListingName(portfolio.getListingName());
        dto.setListingType(portfolio.getListingType());
        dto.setQuantity(portfolio.getQuantity());
        dto.setAverageBuyPrice(avgPrice);
        dto.setCurrentPrice(currentPrice);
        dto.setProfit(profit);
        dto.setProfitPercent(profitPercent);
        dto.setPublicQuantity(portfolio.getPublicQuantity());
        dto.setLastModified(portfolio.getLastModified());
        return dto;
    }

    /**
     * Vraca trenutnu cenu listinga iz baze. Fallback na 0 ako listing ne postoji.
     */
    private BigDecimal getCurrentPrice(Long listingId) {
        Optional<Listing> listing = listingRepository.findById(listingId);
        return listing.map(l -> l.getPrice() != null ? l.getPrice() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
    }
}
