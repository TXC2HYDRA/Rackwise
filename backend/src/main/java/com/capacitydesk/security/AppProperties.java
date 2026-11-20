package com.capacitydesk.security;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("capacitydesk")
public record AppProperties(Jwt jwt, Cors cors) {

    public record Jwt(String secret, String issuer, Duration ttl) {}

    public record Cors(List<String> allowedOrigins) {}
}
