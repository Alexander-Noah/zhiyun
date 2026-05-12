package org.example.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "smart-lab.iot")
public class IotHardwareProperties {
    private boolean enabled = true;
    private Duration timeout = Duration.ofSeconds(5);
    private List<HardwareDevice> devices = new ArrayList<>();

    @Data
    public static class HardwareDevice {
        private String code;
        private String name;
        private String type;
        private Long labId;
        private String labCode;
        private String protocol = "http";
        private String baseUrl;
        private String username;
        private String password;
        private String token;
        private Map<String, String> headers = new LinkedHashMap<>();
        private Map<String, HardwareCommand> commands = new LinkedHashMap<>();
        private String statusPath;
        private String snapshotUrl;
        private String streamUrl;
        private String hlsUrl;
        private String flvUrl;
        private String webrtcUrl;
    }

    @Data
    public static class HardwareCommand {
        private String method = "POST";
        private String path;
        private String body;
        private Map<String, String> headers = new LinkedHashMap<>();
        private Integer successStatusMin = 200;
        private Integer successStatusMax = 299;
        private String successContains;
        private TcpCommand tcp = new TcpCommand();
    }

    @Data
    public static class TcpCommand {
        private String host;
        private Integer port;
        private String payload;
        private String charset = "UTF-8";
        private String successContains;
    }
}
