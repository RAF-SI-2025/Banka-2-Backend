package rs.raf.banka2_bek.interbank.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rs.raf.banka2_bek.interbank.config.InterbankProperties;
import rs.raf.banka2_bek.interbank.exception.InterbankExceptions;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BankRoutingService {

    private final InterbankProperties properties;

    public int myRoutingNumber() {
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
