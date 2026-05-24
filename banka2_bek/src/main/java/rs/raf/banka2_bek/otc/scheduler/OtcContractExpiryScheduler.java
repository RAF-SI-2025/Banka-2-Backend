package rs.raf.banka2_bek.otc.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rs.raf.banka2_bek.notification.model.NotificationType;
import rs.raf.banka2_bek.notification.service.NotificationService;
import rs.raf.banka2_bek.otc.model.OtcContract;
import rs.raf.banka2_bek.otc.repository.OtcContractRepository;
import rs.raf.banka2_bek.otc.service.OtcService;

import java.time.LocalDate;
import java.util.List;

/**
 * Dnevno markiranje OTC ugovora kojima je prosao settlementDate kao EXPIRED.
 * Time se prodavcima oslobadja publicQuantity za nove ponude.
 *
 * Takodje salje notifikacije 3 dana pre isteka (B4 zahtev).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OtcContractExpiryScheduler {

    private final OtcService otcService;
    private final OtcContractRepository otcContractRepository;
    private final NotificationService notificationService;

    private static final int EXPIRY_NOTIFICATION_DAYS = 3;

    @Scheduled(cron = "0 5 1 * * *")
    public void expireContracts() {
        int count = otcService.expireSettledContracts();
        if (count > 0) {
            log.info("OTC: {} ugovora istekao i markiran EXPIRED", count);
        }
    }

    @Scheduled(cron = "0 10 1 * * *")
    public void notifyExpiringContracts() {
        LocalDate targetDate = LocalDate.now().plusDays(EXPIRY_NOTIFICATION_DAYS);
        List<OtcContract> expiring = otcContractRepository.findActiveExpiringOn(targetDate);

        for (OtcContract contract : expiring) {
            try {
                notificationService.notify(
                        contract.getBuyerId(),
                        contract.getBuyerRole(),
                        NotificationType.OTC_CONTRACT_EXPIRING,
                        "Opcioni ugovor uskoro ističe",
                        "Vaš opcioni ugovor za " + contract.getListing().getTicker()
                                + " ističe za " + EXPIRY_NOTIFICATION_DAYS + " dana ("
                                + contract.getSettlementDate() + ").",
                        "OTC_CONTRACT",
                        contract.getId()
                );
            } catch (Exception e) {
                log.warn("Failed to send OTC expiry notification to buyer for contract #{}: {}",
                        contract.getId(), e.getMessage());
            }

            try {
                notificationService.notify(
                        contract.getSellerId(),
                        contract.getSellerRole(),
                        NotificationType.OTC_CONTRACT_EXPIRING,
                        "Opcioni ugovor uskoro ističe",
                        "Opcioni ugovor za " + contract.getListing().getTicker()
                                + " ističe za " + EXPIRY_NOTIFICATION_DAYS + " dana ("
                                + contract.getSettlementDate() + ").",
                        "OTC_CONTRACT",
                        contract.getId()
                );
            } catch (Exception e) {
                log.warn("Failed to send OTC expiry notification to seller for contract #{}: {}",
                        contract.getId(), e.getMessage());
            }
        }

        if (!expiring.isEmpty()) {
            log.info("OTC: Poslate notifikacije o isteku za {} ugovora koji isticu za {} dana",
                    expiring.size(), EXPIRY_NOTIFICATION_DAYS);
        }
    }
}
