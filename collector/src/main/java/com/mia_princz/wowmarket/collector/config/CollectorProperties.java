package com.mia_princz.wowmarket.collector.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Everything the collector needs to know, validated at startup so a bad setting stops it before the first run. */
@Validated
@ConfigurationProperties("collector")
public record CollectorProperties(
        @NotBlank @Pattern(regexp = "eu|us|kr|tw") String region,
        @NotEmpty List<@NotNull @Positive Integer> connectedRealms,
        @Valid @NotNull Capture capture,
        @Valid @NotNull Archive archive,
        @Valid @NotNull Blizzard blizzard) {

    public CollectorProperties {
        if (connectedRealms != null) {
            connectedRealms = List.copyOf(connectedRealms);
            if (new HashSet<>(connectedRealms).size() != connectedRealms.size()) {
                throw new IllegalArgumentException(
                        "collector.connected-realms lists a realm twice: " + connectedRealms);
            }
        }
    }

    /** When snapshots are fetched and how many sources are fetched at the same time. */
    public record Capture(
            boolean enabled,
            @NotBlank String cron,
            @Min(1) @Max(16) int concurrency) {}

    /** Root folder of the raw zone, where every snapshot is kept as received. */
    public record Archive(@NotNull Path root) {}

    /** Battle.net API client and the limits we keep below the Blizzard quota. */
    public record Blizzard(
            @NotBlank String clientId,
            @NotBlank String clientSecret,
            @NotNull URI tokenUrl,
            @NotNull URI apiUrl,
            @Min(1) @Max(100) int requestsPerSecond,
            @NotNull Duration connectTimeout,
            @NotNull Duration requestTimeout,
            @Min(1) @Max(10) int maxAttempts) {

        /** Keeps the secret out of logs and error messages. */
        @Override
        public String toString() {
            return "Blizzard[clientId=" + clientId + ", clientSecret=***, tokenUrl=" + tokenUrl + ", apiUrl=" + apiUrl
                    + ", requestsPerSecond=" + requestsPerSecond + ", connectTimeout=" + connectTimeout
                    + ", requestTimeout=" + requestTimeout + ", maxAttempts=" + maxAttempts + "]";
        }
    }
}
