package rs.raf.banka2_bek.investmentfund.model;

public enum ClientFundTransactionStatus {
    PENDING,
    COMPLETED,
    FAILED,

    // B11 — dividenda je primljena na račun fonda, ali još nije obrađena
    // kroz reinvestiranje ili raspodelu klijentima.
    DIVIDEND_INFLOW,

    // B11 — primljena dividenda je iskorišćena za kreiranje BUY ordera
    // u ime investicionog fonda.
    DIVIDEND_REINVESTED,

    // B11 — primljena dividenda je raspodeljena klijentima proporcionalno
    // njihovom udelu u fondu.
    DIVIDEND_DISTRIBUTED
}