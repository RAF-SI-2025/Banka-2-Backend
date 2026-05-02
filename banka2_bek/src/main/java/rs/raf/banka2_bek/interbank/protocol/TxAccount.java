package rs.raf.banka2_bek.interbank.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Spec ref: protokol §2.6 Accounts
 *
 * Sealed interface sa 3 variante:
 *  - Person  — osoba kod neke banke (ForeignBankId; koristi se kao "owner" u nekim transakcijama)
 *  - Account — valutni racun po brojevnom identifikatoru (CurrencyAccountNumber = string)
 *  - Option  — pseudo-racun za izvrsavanje OTC opcije; id je ID OTC pregovora
 *              (§3.6.1 — osigurava da je pseudo-racun kod prodavca)
 *
 * JSON: `{type: 'PERSON', id: ForeignBankId} | {type: 'ACCOUNT', num: string} | {type: 'OPTION', id: ForeignBankId}`
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
        {
        @JsonSubTypes.Type(value = TxAccount.Person.class, name = "PERSON"),
        @JsonSubTypes.Type(value = TxAccount.Account.class, name = "ACCOUNT"),
        @JsonSubTypes.Type(value = TxAccount.Option.class, name = "OPTION")
    }
)
public sealed interface TxAccount permits TxAccount.Person, TxAccount.Account, TxAccount.Option {

    record Person(ForeignBankId id) implements TxAccount {}

    record Account(String num) implements TxAccount {}

    record Option(ForeignBankId id) implements TxAccount {}
}
