package io.quarkiverse.langchain4j.cost;

import java.math.BigDecimal;

public record Cost(BigDecimal number, String currencyCode) {

    public static Cost of(BigDecimal number, String currencyCode) {
        return new Cost(number == null ? BigDecimal.ZERO : number, currencyCode == null ? "UNKNOWN" : currencyCode);
    }

    @Override
    public String toString() {
        return number.toPlainString() + currencyCode;
    }
}
