package io.quarkiverse.langchain4j.bedrock.runtime.config;

import org.eclipse.microprofile.config.spi.Converter;

import software.amazon.awssdk.services.bedrockruntime.model.CacheTTL;

/**
 * Converts a prompt caching TTL, accepting both the AWS Bedrock values ({@code 5m}, {@code 1h}) and the SDK enum
 * constant names ({@code VALUE_5_M}, {@code VALUE_1_H}).
 */
public class CacheTtlConverter implements Converter<CacheTTL> {

    @Override
    public CacheTTL convert(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        var trimmed = value.trim();
        var ttl = CacheTTL.fromValue(trimmed);
        if (ttl != CacheTTL.UNKNOWN_TO_SDK_VERSION) {
            return ttl;
        }

        var name = trimmed.replace('-', '_');
        for (CacheTTL known : CacheTTL.knownValues()) {
            if (known.name().equalsIgnoreCase(name)) {
                return known;
            }
        }

        throw new IllegalArgumentException(
                String.format("Unknown prompt caching TTL: %s, must be one of: [5m, 1h]", trimmed));
    }
}
