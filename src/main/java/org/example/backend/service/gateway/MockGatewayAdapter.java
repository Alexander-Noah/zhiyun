package org.example.backend.service.gateway;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MockGatewayAdapter implements GatewayAdapter {
    @Override
    public String protocol() {
        return "mock";
    }

    @Override
    public GatewayCommandResult sendCommand(GatewayCommandRequest request) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("gatewayMode", "mock_gateway");
        response.put("iotDeviceId", request.iotDeviceId());
        response.put("iotCode", request.iotCode());
        response.put("labId", request.labId());
        response.put("deviceType", request.deviceType());
        response.put("commandType", request.commandType());
        response.put("action", request.action());
        response.put("payload", request.payload());
        response.put("mockedAt", LocalDateTime.now().toString());
        return GatewayCommandResult.mockSuccess(request, response);
    }

    @Override
    public GatewayStatusResult queryStatus(GatewayCommandRequest request) {
        return new GatewayStatusResult(
                true,
                "mock_online",
                "mock_gateway",
                "模拟网关演示：状态来自 MockGatewayAdapter，非真实硬件回执",
                Map.of(
                        "iotCode", request.iotCode() == null ? "" : request.iotCode(),
                        "deviceType", request.deviceType() == null ? "" : request.deviceType()
                ),
                LocalDateTime.now()
        );
    }
}
