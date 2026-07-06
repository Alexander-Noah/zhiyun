package org.example.backend.service.gateway;

import java.time.LocalDateTime;
import java.util.Map;

public record GatewayStatusResult(
        boolean online,
        String runtimeStatus,
        String gatewayMode,
        String responseSummary,
        Map<String, Object> responseResult,
        LocalDateTime reportedAt
) {
    public GatewayStatusResult {
        responseResult = responseResult == null ? Map.of() : Map.copyOf(responseResult);
        reportedAt = reportedAt == null ? LocalDateTime.now() : reportedAt;
    }
}
