package rs.raf.banka2_bek.interbank.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Configuration
@ConfigurationProperties(prefix = "interbank")
@Data
public class InterbankProperties {

    /** Routing number nase banke (prve 3 cifre svakog naseg racuna). */
    private Integer myRoutingNumber;

    /** Display name nase banke koji se vraca u UserInformation.bankDisplayName (§3.7). */
    private String myBankDisplayName;

    /** Lista partnerskih banaka sa kojima smo u komunikaciji. */
    private List<PartnerBank> partners = new ArrayList<>();

    @Data
    public static class PartnerBank {
        /** Routing number partnerske banke. */
        private Integer routingNumber;

        /** Display name partnerske banke (za UI). */
        private String displayName;

        /** Base URL partnerskog API-ja, npr. "http://banka1-api:8080". */
        private String baseUrl;

        /** Token koji partner banka izdaje nama; saljemo ga u X-Api-Key headeru. */
        private String outboundToken;

        /** Token koji mi izdajemo partner banci; verifikujemo ga u X-Api-Key headeru. */
        private String inboundToken;
    }

    public Optional<PartnerBank> findByApiKey(String apiKey) {
        return partners.stream().filter(p -> p.getInboundToken().equals(apiKey)).findFirst();
    }
}
