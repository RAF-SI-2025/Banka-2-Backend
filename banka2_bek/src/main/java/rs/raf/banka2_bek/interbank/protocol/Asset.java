package rs.raf.banka2_bek.interbank.protocol;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Spec ref: protokol §2.7 Assets
 *
 * Sealed interface sa 3 variante: Monas (valutna sredstva), Stock (akcije),
 * OptionAsset (opcioni ugovori). Pri serijalizaciji u JSON se pojavljuje
 * polje `type` ('MONAS' | 'STOCK' | 'OPTION') + odgovarajuci `asset` payload.
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
        {
        @JsonSubTypes.Type(value = Asset.Monas.class, name = "MONAS"),
        @JsonSubTypes.Type(value = Asset.Stock.class, name = "STOCK"),
        @JsonSubTypes.Type(value = Asset.OptionAsset.class, name = "OPTION")
    }
)
public sealed interface Asset permits Asset.Monas, Asset.Stock, Asset.OptionAsset {

    record Monas(MonetaryAsset asset) implements Asset {}

    record Stock(StockDescription asset) implements Asset {}

    record OptionAsset(OptionDescription asset) implements Asset {}
}
