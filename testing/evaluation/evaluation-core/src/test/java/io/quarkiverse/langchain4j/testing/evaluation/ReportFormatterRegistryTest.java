package io.quarkiverse.langchain4j.testing.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ReportFormatterRegistryTest {

    @Test
    void shouldGetMarkdownFormatter() {
        ReportFormatter formatter = ReportFormatterRegistry.get("markdown");
        assertThat(formatter).isNotNull();
        assertThat(formatter).isInstanceOf(MarkdownReportFormatter.class);
        assertThat(formatter.format()).isEqualTo("markdown");
    }

    @Test
    void shouldGetJsonFormatter() {
        ReportFormatter formatter = ReportFormatterRegistry.get("json");
        assertThat(formatter).isNotNull();
        assertThat(formatter).isInstanceOf(JsonReportFormatter.class);
        assertThat(formatter.format()).isEqualTo("json");
    }

    @Test
    void shouldGetFormatterCaseInsensitive() {
        ReportFormatter markdown1 = ReportFormatterRegistry.get("markdown");
        ReportFormatter markdown2 = ReportFormatterRegistry.get("MARKDOWN");
        ReportFormatter markdown3 = ReportFormatterRegistry.get("MarkDown");

        assertThat(markdown1.format()).isEqualTo(markdown2.format());
        assertThat(markdown2.format()).isEqualTo(markdown3.format());
    }

    @Test
    void shouldThrowExceptionForUnknownFormat() {
        assertThatThrownBy(() -> ReportFormatterRegistry.get("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No ReportFormatter found for format: unknown")
                .hasMessageContaining("Available formats:");
    }

    @Test
    void shouldThrowExceptionForNullFormat() {
        assertThatThrownBy(() -> ReportFormatterRegistry.get(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Format must not be null or blank");
    }

    @Test
    void shouldThrowExceptionForBlankFormat() {
        assertThatThrownBy(() -> ReportFormatterRegistry.get(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Format must not be null or blank");

        assertThatThrownBy(() -> ReportFormatterRegistry.get("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Format must not be null or blank");
    }

    @Test
    void shouldReturnSupportedFormats() {
        Set<String> formats = ReportFormatterRegistry.supportedFormats();
        assertThat(formats).containsExactlyInAnyOrder("markdown", "json");
    }

    @Test
    void shouldDiscoverFormattersViaServiceLoader() {
        // When running in plain JUnit context (no CDI), should still find formatters via ServiceLoader
        Set<String> formats = ReportFormatterRegistry.supportedFormats();
        assertThat(formats)
                .isNotEmpty()
                .contains("markdown", "json");
    }

    @Test
    void shouldVerifyBuiltInFormatterPriority() {
        // Built-in formatters should have default priority
        ReportFormatter markdown = ReportFormatterRegistry.get("markdown");
        ReportFormatter json = ReportFormatterRegistry.get("json");

        assertThat(markdown.priority()).isEqualTo(0);
        assertThat(json.priority()).isEqualTo(0);
    }
}
