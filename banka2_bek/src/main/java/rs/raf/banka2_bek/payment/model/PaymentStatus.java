package rs.raf.banka2_bek.payment.model;

public enum PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    REJECTED,
    CANCELLED,
    /** T2-012: placanje otkazano zbog 3 neuspela OTP unosa (audit trail). */
    ABORTED
}
