package org.example.backend.service.gateway;

import java.util.Map;

public record GatewayCommandRequest(
        Long gatewayId,
        String gatewayCode,
        String protocolType,
        String gatewayAddress,
        String authType,
        Long pointId,
        String pointCode,
        String requestPath,
        String requestTemplate,
        Long iotDeviceId,
        String iotCode,
        Long labId,
        String deviceType,
        String commandType,
        String action,
        Map<String, Object> payload
) {
    public GatewayCommandRequest {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static GatewayCommandRequest mock(
            Long iotDeviceId,
            String iotCode,
            Long labId,
            String deviceType,
            String action,
            Map<String, Object> payload
    ) {
        return new GatewayCommandRequest(
                null,
                "mock-gateway",
                "mock",
                "",
                "none",
                null,
                "",
                "",
                "",
                iotDeviceId,
                iotCode,
                labId,
                deviceType,
                action,
                action,
                payload
        );
    }
}
