package rs.raf.banka2_bek.timeseries;

import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Upisuje OHLCV (Open / High / Low / Close / Volume) tickove u InfluxDB.
 * <p>
 * Pozove se kad god se cena hartije osvezi iz Alpha Vantage API-ja ili
 * Fixer-a (forex). Svaki Point je jedan trenutak u vremenu — agregati
 * (dnevni, satni) se rade Flux query-jem pri citanju.
 *
 * <pre>
 * measurement:  listing_price
 * tags:         ticker=AAPL, exchange=NASDAQ, asset_type=STOCK
 * fields:       open, high, low, close, volume, ask, bid
 * timestamp:    Unix epoch ms
 * </pre>
 */
@Component
@ConditionalOnProperty(name = "banka2.influx.enabled", havingValue = "true")
public class ListingPriceRecorder {

    private static final Logger log = LoggerFactory.getLogger(ListingPriceRecorder.class);

    private final WriteApiBlocking writeApi;
    private final String bucket;
    private final String org;

    public ListingPriceRecorder(
            WriteApiBlocking writeApi,
            @Value("${banka2.influx.bucket}") String bucket,
            @Value("${banka2.influx.org}") String org) {
        this.writeApi = writeApi;
        this.bucket = bucket;
        this.org = org;
    }

    /**
     * Zapisuje jedan OHLCV tick.
     * <p>
     * {@code timestamp} ide u milisekundama (WritePrecision.MS) — InfluxDB
     * podrzava preciznost do nanosekunde, ali nas ne treba (Alpha Vantage
     * snapshot-i su 15-min granularnosti).
     */
    public void recordTick(
            String ticker,
            String exchange,
            String assetType,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            Long volume,
            BigDecimal ask,
            BigDecimal bid,
            Instant timestamp) {

        try {
            Point point = Point.measurement("listing_price")
                    .addTag("ticker", ticker)
                    .addTag("exchange", exchange != null ? exchange : "unknown")
                    .addTag("asset_type", assetType != null ? assetType : "STOCK")
                    .time(timestamp.toEpochMilli(), WritePrecision.MS);

            if (open != null)   point.addField("open",   open.doubleValue());
            if (high != null)   point.addField("high",   high.doubleValue());
            if (low != null)    point.addField("low",    low.doubleValue());
            if (close != null)  point.addField("close",  close.doubleValue());
            if (volume != null) point.addField("volume", volume);
            if (ask != null)    point.addField("ask",    ask.doubleValue());
            if (bid != null)    point.addField("bid",    bid.doubleValue());

            writeApi.writePoint(bucket, org, point);
        } catch (Exception e) {
            // InfluxDB write nikad ne sme da pukne business operaciju —
            // listing refresh i order execution rade i bez time-series persistence-a.
            log.warn("Greska pri pisanju u InfluxDB za ticker={}: {}", ticker, e.getMessage());
        }
    }
}
