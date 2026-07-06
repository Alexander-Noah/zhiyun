package org.example.backend.service.gateway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayAdapterBehaviorTest {
    @Test
    void mockGatewayReturnsStandardizedCommandResultWithoutClaimingRealHardware() throws IOException {
        String request = readGatewaySource("GatewayCommandRequest.java");
        String result = readGatewaySource("GatewayCommandResult.java");
        String adapter = readGatewaySource("MockGatewayAdapter.java");

        assertAll(
                () -> assertTrue(request.contains("public static GatewayCommandRequest mock(")),
                () -> assertTrue(result.contains("mock_success")),
                () -> assertTrue(result.contains("mock_gateway")),
                () -> assertTrue(result.contains("模拟网关演示")),
                () -> assertTrue(result.contains("非真实硬件回执")),
                () -> assertTrue(adapter.contains("response.put(\"action\", request.action())")),
                () -> assertTrue(adapter.contains("response.put(\"iotCode\", request.iotCode())"))
        );
    }

    @Test
    void realGatewayPlaceholdersReturnPendingConfigurationState() throws IOException {
        String http = readGatewaySource("HttpGatewayAdapter.java");
        String mqtt = readGatewaySource("MqttGatewayAdapter.java");
        String result = readGatewaySource("GatewayCommandResult.java");

        assertAll(
                () -> assertTrue(http.contains("GatewayCommandResult.realGatewayNotConfigured")),
                () -> assertTrue(mqtt.contains("GatewayCommandResult.realGatewayNotConfigured")),
                () -> assertTrue(result.contains("\"failed\"")),
                () -> assertTrue(result.contains("待配置真实网关"))
        );
    }

    private String readGatewaySource(String fileName) throws IOException {
        Path path = Path.of("src", "main", "java", "org", "example", "backend", "service", "gateway", fileName);
        assertEquals(true, Files.exists(path), path + " should exist");
        return Files.readString(path);
    }
}
