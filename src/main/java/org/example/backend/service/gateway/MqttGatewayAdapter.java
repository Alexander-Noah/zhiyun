package org.example.backend.service.gateway;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
public class MqttGatewayAdapter implements GatewayAdapter {
    @Override
    public String protocol() {
        return "mqtt";
    }

    @Override
    public GatewayCommandResult sendCommand(GatewayCommandRequest request) {
        return GatewayCommandResult.realGatewayNotConfigured("real_gateway_unconfigured", request);
    }

    @Override
    public GatewayStatusResult queryStatus(GatewayCommandRequest request) {
        return new GatewayStatusResult(
                false,
                "unconfigured",
                "real_gateway_unconfigured",
                "待配置真实网关",
                Map.of("protocol", protocol()),
                LocalDateTime.now()
        );
    }
}
