package org.example.backend.service.gateway;

public interface GatewayAdapter {
    String protocol();

    GatewayCommandResult sendCommand(GatewayCommandRequest request);

    GatewayStatusResult queryStatus(GatewayCommandRequest request);
}
