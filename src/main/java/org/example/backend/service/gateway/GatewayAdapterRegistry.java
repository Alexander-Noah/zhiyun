package org.example.backend.service.gateway;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class GatewayAdapterRegistry {
    private final Map<String, GatewayAdapter> adapters;
    private final GatewayAdapter mockGatewayAdapter;

    public GatewayAdapterRegistry(List<GatewayAdapter> adapters, MockGatewayAdapter mockGatewayAdapter) {
        this.adapters = adapters.stream()
                .collect(Collectors.toMap(adapter -> normalize(adapter.protocol()), Function.identity(), (left, right) -> left));
        this.mockGatewayAdapter = mockGatewayAdapter;
    }

    public GatewayAdapter resolve(String protocolType, boolean configured) {
        String protocol = normalize(protocolType);
        if (!configured || !StringUtils.hasText(protocol) || "mock".equals(protocol)) {
            return mockGatewayAdapter;
        }
        return adapters.getOrDefault(protocol, mockGatewayAdapter);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replace("-", "_").toLowerCase(Locale.ROOT);
    }
}
