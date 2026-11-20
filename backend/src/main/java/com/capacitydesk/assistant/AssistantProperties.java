package com.capacitydesk.assistant;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("capacitydesk.assistant")
public record AssistantProperties(@DefaultValue Llm llm) {

    public record Llm(@DefaultValue("false") boolean enabled,
                      String apiKey,
                      @DefaultValue("https://generativelanguage.googleapis.com") String baseUrl,
                      @DefaultValue("gemini-2.0-flash") String model,
                      @DefaultValue("4s") Duration timeout,
                      @DefaultValue("1") int retries) {}
}
