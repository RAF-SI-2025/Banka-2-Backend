package rs.raf.banka2_bek.interbank.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
================================================================================
 TODO — KONFIGURACIJA PARTNERSKIH BANAKA (PROTOKOL §2.1, §2.10)
 Zaduzen: BE tim
 Spec ref: protokol §2.1 Bank identification (RoutingNumber = prve 3 cifre
           racuna), §2.10 Authentication (X-Api-Key header)
--------------------------------------------------------------------------------
 Citaj iz application.properties:
   interbank.my-routing-number=222
   interbank.my-bank-display-name=Banka 2
   interbank.partners[0].routing-number=111
   interbank.partners[0].display-name=Banka 1
   interbank.partners[0].base-url=http://banka1-api:8080
   interbank.partners[0].outbound-token=<token koji oni izdaju nama>
   interbank.partners[0].inbound-token=<token koji mi izdajemo njima>
   interbank.partners[1].routing-number=333
   ...

 KORISNICI:
  - BankRoutingService: po prva 3 cifre racuna mapira na PartnerBank.
  - InterbankClient: na osnovu routingNumber-a pronadje URL + outboundToken
    i salje HTTP zahtev sa X-Api-Key: <outboundToken> header-om (§2.10).
  - InterbankInboundController: proverava da X-Api-Key header u dolaznoj
    poruci odgovara `partners[*].inboundToken` (token koji smo MI izdali
    toj banci). Nevalidan token -> 401.

 NAPOMENA O DVA TOKEN-A:
  Svaka banka izdaje sopstveni API token za svaku drugu banku (§2.10).
  Tokeni su asimetricni: token koji A koristi pri slanju ka B nije isti
  kao token koji B koristi pri slanju ka A. Dva polja: outboundToken
  (sta saljemo mi) + inboundToken (sta verifikujemo mi).
================================================================================
*/
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
