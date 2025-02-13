package io.quarkiverse.langchain4j.sample;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RentalReport(
        LocalDate agreementDate,
        LocalDate rentalStartDate,
        LocalDate rentalEndDate,
        String landlordName,
        String tenantName,
        BigDecimal rentalPriceUSD
) {
}
