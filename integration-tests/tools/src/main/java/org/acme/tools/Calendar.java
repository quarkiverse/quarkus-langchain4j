package org.acme.tools;

import java.time.*;
import java.time.format.DateTimeFormatter;

import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.agent.tool.Tool;
import io.smallrye.common.annotation.Blocking;

@ApplicationScoped
public class Calendar {

    @Tool
    @Blocking
    public String instant(Instant variable) {
        return DateTimeFormatter.ISO_INSTANT.format(variable);
    }

    @Tool
    @Blocking
    public String date(LocalDate variable) {
        return variable.format(DateTimeFormatter.ISO_DATE);
    }

    @Tool
    @Blocking
    public String dateTime(LocalDateTime variable) {
        return variable.format(DateTimeFormatter.ISO_DATE_TIME);
    }

    @Tool
    @Blocking
    public String time(LocalTime variable) {
        return variable.format(DateTimeFormatter.ISO_LOCAL_TIME);
    }

    @Tool
    @Blocking
    public String offsetDateTime(OffsetDateTime variable) {
        return variable.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    @Tool
    @Blocking
    public String offsetTime(OffsetTime variable) {
        return variable.format(DateTimeFormatter.ISO_OFFSET_TIME);
    }

    @Tool
    @Blocking
    public String year(Year variable) {
        return variable.toString();
    }

    @Tool
    @Blocking
    public String yearMonth(YearMonth variable) {
        return variable.toString();
    }

    @Tool
    @Blocking
    public String period(Period variable) {
        return variable.toString();
    }
}
