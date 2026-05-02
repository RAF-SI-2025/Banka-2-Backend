package rs.raf.banka2_bek.account.model;

/**
 * Kategorija racuna — razlikuje klijentske, bankine (trading) i margin racune.
 *
 * - CLIENT: obican racun fizickog/pravnog lica
 * - BANK_TRADING: sistemski racun banke koji se koristi za trgovanje od strane agenata
 * - MARGIN: margin racun za kupovinu sa polugom
 */
public enum AccountCategory {
    CLIENT,
    BANK_TRADING,
    MARGIN,
    FUND
}
