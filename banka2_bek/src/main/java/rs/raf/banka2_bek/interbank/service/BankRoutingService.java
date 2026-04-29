package rs.raf.banka2_bek.interbank.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;

import java.util.Optional;

/*
================================================================================
 TODO — ROUTING NUMBER RESOLUTION (PROTOKOL §2.1)
 Zaduzen: BE tim
 Spec ref: protokol §2.1 Bank identification — "Account brojevi pocinju sa
           tri cifre koje identifikuju banku. Te tri cifre se zovu routing
           numbers."
--------------------------------------------------------------------------------
 SVRHA:
 Za dato accountNumber, otkrij da li je nas (myRoutingNumber prefix) ili
 koja partnerska banka. Koristi se u:
  - PaymentController.createPayment: ako je receiver iz druge banke,
    redirektuj na TransactionExecutorService umesto obicnog transfer flow-a
  - TransactionExecutorService: parsira sve TxAccount.Account.num iz Postings
    da identifikuje sve ucesnice i zato ko se promovira u koordinatora

 METODE:
   int myRoutingNumber()
       Vraca routing number nase banke iz properties.

   int parseRoutingNumber(String accountNumber)
       Iz accountNumber izvuce prve 3 cifre kao int. IllegalArgumentException
       ako accountNumber nije parse-abilan.

   boolean isLocalAccount(String accountNumber)
       True ako parseRoutingNumber == myRoutingNumber.

   Optional<PartnerBank> resolvePartner(String accountNumber)
       Trazi partnera ciji se routingNumber poklapa.

   Optional<PartnerBank> resolvePartnerByRouting(int routingNumber)
       Direct lookup po vec parsiranom routing number-u (koristi se za
       Message receivers gde routingNumber dolazi iz Transaction.transactionId).

 EDGE CASES:
  - accountNumber null/krati od 3 cifre -> IllegalArgumentException
  - accountNumber sa nepoznatim prefixom -> Optional.empty()
    (caller: vrati 404 / NoVoteReason.NO_SUCH_ACCOUNT)
================================================================================
*/
@Service
@RequiredArgsConstructor
public class BankRoutingService {

    private final InterbankProperties properties;

    public int myRoutingNumber() {
        // TODO: validacija da properties.myRoutingNumber nije null

        if (properties.getMyRoutingNumber() != null)
            return properties.getMyRoutingNumber();

        throw new InterbankExceptions.InterbankProtocolException("My routing number is not defined!");

    }

    public int parseRoutingNumber(String accountNumber) {
        // if argument is null or empty
        if (accountNumber == null || accountNumber.isBlank()) // arg validation
            throw new InterbankExceptions.InterbankProtocolException("Empty accountNumber!");

        if (accountNumber.length() < 3)
            throw new InterbankExceptions.InterbankProtocolException("AccountNumber not valid!");

        // extract first 3 characters from string
        String first3digits = accountNumber.substring(0, 3);

        try {
            return Integer.parseInt(first3digits); // parsing digits to a number
        } catch (NumberFormatException e) { // if parse failed protocol exception is thrown
            throw new InterbankExceptions.InterbankProtocolException("First 3 characters of accountNumber not parsable to integer!");
        }
    }

    public boolean isLocalAccount(String accountNumber) {

        int routingNumber = parseRoutingNumber(accountNumber);
        int ourBankRoutingNumber = myRoutingNumber();

        return routingNumber == ourBankRoutingNumber;
    }

    public Optional<InterbankProperties.PartnerBank> resolvePartner(String accountNumber) {
        // TODO: parseRoutingNumber + delegate na resolvePartnerByRouting
        int accountOwnerBankRouting = parseRoutingNumber(accountNumber);
        return resolvePartnerByRouting(accountOwnerBankRouting);
    }

    public Optional<InterbankProperties.PartnerBank> resolvePartnerByRouting(int routingNumber) {

        return properties
                .getPartners()
                .stream()
                .filter(partnerBank -> partnerBank.getRoutingNumber() == routingNumber)
                .findFirst();
    }
}
