package org.example.backend.service.impl;

import org.example.backend.config.IotHardwareProperties;
import org.example.backend.entity.DevicesEntity;
import org.example.backend.mapper.DevicesMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.IotHardwareService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class IotHardwareServiceImpl implements IotHardwareService {
    private final IotHardwareProperties properties;
    private final BusinessLoopService businessLoopService;
    private final DevicesMapper devicesMapper;
    private final HttpClient httpClient;

    public IotHardwareServiceImpl(IotHardwareProperties properties, BusinessLoopService businessLoopService, DevicesMapper devicesMapper) {
        this.properties = properties;
        this.businessLoopService = businessLoopService;
        this.devicesMapper = devicesMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .build();
    }

    @Override
    public Map<String, Object> getOverview() {
        List<Map<String, Object>> devices = listHardwareDevices();
        long accessCount = devices.stream().filter(device -> "access-control".equals(device.get("type"))).count();
        long cameraCount = devices.stream().filter(device -> "camera".equals(device.get("type"))).count();

        return Map.of(
                "enabled", properties.isEnabled(),
                "configuredCount", devices.size(),
                "accessCount", accessCount,
                "cameraCount", cameraCount,
                "devices", devices
        );
    }

    @Override
    public List<Map<String, Object>> listHardwareDevices() {
        return properties.getDevices().stream()
                .map(this::sanitizeDevice)
                .toList();
    }

    @Override
    public Map<String, Object> getLabDevices(Long labId) {
        List<DevicesEntity> ledgerDevices = Optional.ofNullable(devicesMapper.getDevices())
                .orElse(List.of())
                .stream()
                .filter(device -> Objects.equals(device.getLabId(), labId))
                .toList();
        Map<String, IotHardwareProperties.HardwareDevice> hardwareByCode = new LinkedHashMap<>();
        properties.getDevices().stream()
                .filter(device -> Objects.equals(device.getLabId(), labId))
                .forEach(device -> hardwareByCode.put(valueOrEmpty(device.getCode()), device));

        List<Map<String, Object>> boundDevices = new ArrayList<>();
        for (DevicesEntity ledgerDevice : ledgerDevices) {
            String code = valueOrEmpty(ledgerDevice.getDeviceCode());
            IotHardwareProperties.HardwareDevice hardwareDevice = hardwareByCode.remove(code);
            boundDevices.add(createBoundDevice(ledgerDevice, hardwareDevice, labId));
        }

        for (IotHardwareProperties.HardwareDevice hardwareDevice : hardwareByCode.values()) {
            boundDevices.add(createBoundDevice(null, hardwareDevice, labId));
        }

        Set<String> categories = boundDevices.stream()
                .map(device -> String.valueOf(device.get("category")))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        long onlineCount = boundDevices.stream().filter(device -> Boolean.TRUE.equals(device.get("online"))).count();
        long configuredCount = boundDevices.stream().filter(device -> Boolean.TRUE.equals(device.get("configured"))).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("labId", labId);
        result.put("total", boundDevices.size());
        result.put("onlineCount", onlineCount);
        result.put("configuredCount", configuredCount);
        result.put("categories", categories);
        result.put("devices", boundDevices);
        return result;
    }

    @Override
    public Map<String, Object> getLabStatus(Long labId) {
        ensureEnabled();
        List<IotHardwareProperties.HardwareDevice> labDevices = properties.getDevices().stream()
                .filter(device -> Objects.equals(device.getLabId(), labId))
                .toList();

        if (labDevices.isEmpty()) {
            throw new IllegalArgumentException("该实验室未配置物联网硬件接入");
        }

        List<Map<String, Object>> statusList = new ArrayList<>();
        for (IotHardwareProperties.HardwareDevice device : labDevices) {
            statusList.add(checkDeviceStatus(device));
        }

        return Map.of(
                "labId", labId,
                "devices", statusList,
                "online", statusList.stream().anyMatch(status -> Boolean.TRUE.equals(status.get("online")))
        );
    }

    @Override
    public Map<String, Object> executeAccessCommand(Long labId, String action, Map<String, Object> payload) {
        ensureEnabled();
        IotHardwareProperties.HardwareDevice device = findLabDevice(labId, "access-control")
                .orElseThrow(() -> new IllegalArgumentException("该实验室未配置门禁控制器"));
        String normalizedAction = normalizeAction(action);
        Map<String, Object> result = executeConfiguredCommand(device, normalizedAction, payload);

        businessLoopService.recordEvent("iot", "access-" + normalizedAction, device.getName(), String.valueOf(result.get("status")), Map.of(
                "labId", labId,
                "deviceCode", valueOrEmpty(device.getCode()),
                "online", Boolean.TRUE.equals(result.get("success"))
        ));

        return result;
    }

    @Override
    public Map<String, Object> getLabCamera(Long labId) {
        ensureEnabled();
        IotHardwareProperties.HardwareDevice device = findLabDevice(labId, "camera")
                .orElseThrow(() -> new IllegalArgumentException("该实验室未配置摄像头"));
        Map<String, Object> result = sanitizeDevice(device);

        if (StringUtils.hasText(device.getSnapshotUrl())) {
            result.put("snapshotProxyUrl", "/iot/cameras/" + device.getCode() + "/snapshot");
        }

        return result;
    }

    @Override
    public Map<String, Object> getDeviceStatus(String code) {
        ensureEnabled();
        IotHardwareProperties.HardwareDevice device = findDevice(code)
                .orElseThrow(() -> new IllegalArgumentException("未找到硬件设备配置"));
        return checkDeviceStatus(device);
    }

    @Override
    public Map<String, Object> executeDeviceCommand(String code, String action, Map<String, Object> payload) {
        ensureEnabled();
        IotHardwareProperties.HardwareDevice device = findDevice(code)
                .orElseThrow(() -> new IllegalArgumentException("未找到硬件设备配置"));
        Map<String, Object> result = executeConfiguredCommand(device, normalizeAction(action), payload);

        businessLoopService.recordEvent("iot", "device-" + action, device.getName(), String.valueOf(result.get("status")), Map.of(
                "deviceCode", valueOrEmpty(device.getCode()),
                "labId", device.getLabId() == null ? 0 : device.getLabId(),
                "success", Boolean.TRUE.equals(result.get("success"))
        ));

        return result;
    }

    @Override
    public CameraSnapshot getCameraSnapshot(String code) {
        ensureEnabled();
        IotHardwareProperties.HardwareDevice device = findDevice(code)
                .filter(this::isCamera)
                .orElseThrow(() -> new IllegalArgumentException("未找到摄像头配置"));

        if (!StringUtils.hasText(device.getSnapshotUrl())) {
            throw new IllegalArgumentException("摄像头未配置抓拍地址");
        }

        HttpResult result = executeHttp(device, createAdHocCommand("GET", device.getSnapshotUrl()), Map.of());
        if (!result.success()) {
            throw new IllegalArgumentException("摄像头抓拍失败，硬件返回状态 " + result.statusCode());
        }

        String contentType = result.headers().entrySet().stream()
                .filter(entry -> "content-type".equalsIgnoreCase(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst()
                .orElse("image/jpeg");

        return new CameraSnapshot(result.bodyBytes(), contentType);
    }

    private Map<String, Object> createBoundDevice(DevicesEntity ledgerDevice, IotHardwareProperties.HardwareDevice hardwareDevice, Long labId) {
        String code = firstNonBlank(
                ledgerDevice == null ? "" : ledgerDevice.getDeviceCode(),
                hardwareDevice == null ? "" : hardwareDevice.getCode()
        );
        String name = firstNonBlank(
                ledgerDevice == null ? "" : ledgerDevice.getDeviceName(),
                hardwareDevice == null ? "" : hardwareDevice.getName(),
                code
        );
        String category = firstNonBlank(
                ledgerDevice == null ? "" : ledgerDevice.getCategory(),
                hardwareDevice == null ? "" : hardwareDevice.getType(),
                "未分类设备"
        );
        String type = normalizeType(firstNonBlank(
                hardwareDevice == null ? "" : hardwareDevice.getType(),
                category
        ));
        boolean configured = hardwareDevice != null;
        boolean online = ledgerDevice == null || ledgerDevice.getOnline() == null || Boolean.TRUE.equals(ledgerDevice.getOnline());
        String status = firstNonBlank(
                ledgerDevice == null ? "" : ledgerDevice.getStatus(),
                configured ? "已配置" : "未配置",
                online ? "正常" : "离线"
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", ledgerDevice == null ? null : ledgerDevice.getId());
        result.put("code", code);
        result.put("name", name);
        result.put("type", type);
        result.put("category", category);
        result.put("labId", labId);
        result.put("labCode", hardwareDevice == null ? "" : valueOrEmpty(hardwareDevice.getLabCode()));
        result.put("labName", ledgerDevice == null ? "" : valueOrEmpty(ledgerDevice.getLabName()));
        result.put("location", ledgerDevice == null ? "" : valueOrEmpty(ledgerDevice.getLocation()));
        result.put("owner", ledgerDevice == null ? "" : valueOrEmpty(ledgerDevice.getOwnerUsername()));
        result.put("status", status);
        result.put("health", ledgerDevice == null ? "" : valueOrEmpty(ledgerDevice.getHealth()));
        result.put("online", online);
        result.put("configured", configured);
        result.put("protocol", hardwareDevice == null ? "" : firstNonBlank(hardwareDevice.getProtocol(), "http"));
        result.put("streamUrl", hardwareDevice == null ? "" : valueOrEmpty(firstNonBlank(hardwareDevice.getHlsUrl(), hardwareDevice.getWebrtcUrl(), hardwareDevice.getFlvUrl(), hardwareDevice.getStreamUrl())));
        result.put("snapshotProxyUrl", hardwareDevice != null && StringUtils.hasText(hardwareDevice.getSnapshotUrl()) ? "/iot/cameras/" + hardwareDevice.getCode() + "/snapshot" : "");
        result.put("capabilities", buildCapabilities(type, hardwareDevice));
        return result;
    }

    private List<Map<String, Object>> buildCapabilities(String type, IotHardwareProperties.HardwareDevice device) {
        Set<String> commands = device == null || device.getCommands() == null ? Set.of() : device.getCommands().keySet();
        boolean configured = device != null;
        List<Map<String, Object>> capabilities = new ArrayList<>();

        switch (type) {
            case "access-control" -> {
                addCapability(capabilities, "open", "远程开门", "action", "open", configured && (commands.isEmpty() || commands.contains("open")), null);
                addCapability(capabilities, "lock", "锁定", "action", "lock", configured && (commands.isEmpty() || commands.contains("lock")), null);
                addCapability(capabilities, "card-enroll", "卡录入", "form", "card-enroll", configured && commands.contains("card-enroll"), null);
                addCapability(capabilities, "student-enroll", "学号录入", "form", "student-enroll", configured && commands.contains("student-enroll"), null);
                addCapability(capabilities, "face-enroll", "人脸录入", "form", "face-enroll", configured && commands.contains("face-enroll"), null);
                addCapability(capabilities, "fingerprint-enroll", "指纹录入", "form", "fingerprint-enroll", configured && commands.contains("fingerprint-enroll"), null);
                addCapability(capabilities, "status", "状态同步", "action", "status", configured, null);
            }
            case "camera" -> {
                addCapability(capabilities, "view-camera", "查看监控", "media", "view-camera", configured, null);
                addCapability(capabilities, "snapshot", "抓拍", "media", "snapshot", configured, null);
                addCapability(capabilities, "status", "状态同步", "action", "status", configured, null);
            }
            case "light" -> {
                addCapability(capabilities, "power", "电源", "switch", "set", configured && (commands.contains("set") || commands.contains("off")), Map.of("offCommand", "off"));
                addCapability(capabilities, "brightness", "亮度", "slider", "set", configured && commands.contains("set"), Map.of("min", 0, "max", 100, "unit", "%"));
                addCapability(capabilities, "status", "状态同步", "action", "status", configured, null);
            }
            case "air-conditioner" -> {
                addCapability(capabilities, "power", "电源", "switch", "set", configured && (commands.contains("set") || commands.contains("off")), Map.of("offCommand", "off"));
                addCapability(capabilities, "temperature", "温度", "slider", "set", configured && commands.contains("set"), Map.of("min", 16, "max", 30, "unit", "℃"));
                addCapability(capabilities, "mode", "模式", "select", "set", configured && commands.contains("set"), Map.of("options", List.of("制冷", "制热", "自动", "送风", "除湿")));
                addCapability(capabilities, "status", "状态同步", "action", "status", configured, null);
            }
            case "sensor", "safety" -> addCapability(capabilities, "status", "状态同步", "action", "status", configured, null);
            default -> {
                if (commands.contains("set") || commands.contains("off")) {
                    addCapability(capabilities, "power", "电源", "switch", "set", configured, Map.of("offCommand", "off"));
                }
                if (commands.contains("status") || configured) {
                    addCapability(capabilities, "status", "状态同步", "action", "status", configured, null);
                }
            }
        }

        return capabilities;
    }

    private void addCapability(
            List<Map<String, Object>> capabilities,
            String key,
            String label,
            String type,
            String command,
            boolean enabled,
            Map<String, Object> options
    ) {
        Map<String, Object> capability = new LinkedHashMap<>();
        capability.put("key", key);
        capability.put("label", label);
        capability.put("type", type);
        capability.put("command", command);
        capability.put("enabled", enabled);
        if (options != null) {
            capability.putAll(options);
        }
        capabilities.add(capability);
    }

    private Map<String, Object> checkDeviceStatus(IotHardwareProperties.HardwareDevice device) {
        if (device.getCommands().containsKey("status")) {
            return executeConfiguredCommand(device, "status", Map.of());
        }

        if (StringUtils.hasText(device.getStatusPath())) {
            HttpResult result = executeHttp(device, createAdHocCommand("GET", device.getStatusPath()), Map.of());
            return Map.of(
                    "code", valueOrEmpty(device.getCode()),
                    "name", valueOrEmpty(device.getName()),
                    "type", normalizeType(device.getType()),
                    "online", result.success(),
                    "status", result.success() ? "在线" : "离线",
                    "httpStatus", result.statusCode(),
                    "response", result.bodyText()
            );
        }

        return Map.of(
                "code", valueOrEmpty(device.getCode()),
                "name", valueOrEmpty(device.getName()),
                "type", normalizeType(device.getType()),
                "online", true,
                "status", "已配置",
                "response", ""
        );
    }

    private Map<String, Object> executeConfiguredCommand(IotHardwareProperties.HardwareDevice device, String action, Map<String, Object> payload) {
        IotHardwareProperties.HardwareCommand command = device.getCommands().get(action);
        if (command == null) {
            if ("status".equals(action)) {
                return checkDeviceStatus(device);
            }
            throw new IllegalArgumentException(device.getName() + " 未配置 " + action + " 指令");
        }

        if ("tcp".equalsIgnoreCase(device.getProtocol())) {
            return executeTcp(device, command, payload);
        }

        HttpResult result = executeHttp(device, command, payload == null ? Map.of() : payload);
        boolean businessSuccess = result.success() && matchesSuccessBody(command, result.bodyText());
        return Map.of(
                "success", businessSuccess,
                "deviceCode", valueOrEmpty(device.getCode()),
                "deviceName", valueOrEmpty(device.getName()),
                "action", action,
                "status", businessSuccess ? "执行成功" : "执行失败",
                "httpStatus", result.statusCode(),
                "response", result.bodyText()
        );
    }

    private Map<String, Object> executeTcp(IotHardwareProperties.HardwareDevice device, IotHardwareProperties.HardwareCommand command, Map<String, Object> payload) {
        IotHardwareProperties.TcpCommand tcp = command.getTcp();
        String host = firstNonBlank(tcp.getHost(), device.getBaseUrl());
        Integer port = tcp.getPort();
        if (!StringUtils.hasText(host) || port == null) {
            throw new IllegalArgumentException(device.getName() + " TCP 指令缺少 host 或 port");
        }

        String renderedPayload = renderTemplate(tcp.getPayload(), device, payload);
        byte[] outbound = toCommandBytes(renderedPayload, tcp.getCharset());
        Duration timeout = properties.getTimeout();

        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new InetSocketAddress(host, port), Math.toIntExact(timeout.toMillis()));
            socket.setSoTimeout(Math.toIntExact(timeout.toMillis()));
            socket.getOutputStream().write(outbound);
            socket.getOutputStream().flush();

            ByteArrayOutputStream response = new ByteArrayOutputStream();
            try {
                byte[] buffer = new byte[512];
                int read = socket.getInputStream().read(buffer);
                while (read > 0) {
                    response.write(buffer, 0, read);
                    if (socket.getInputStream().available() == 0) {
                        break;
                    }
                    read = socket.getInputStream().read(buffer);
                }
            } catch (java.net.SocketTimeoutException ignored) {
                // Some relay controllers only acknowledge by accepting the TCP write.
            }

            String responseText = response.toString(Charset.forName(firstNonBlank(tcp.getCharset(), StandardCharsets.UTF_8.name())));
            boolean success = !StringUtils.hasText(tcp.getSuccessContains()) || responseText.contains(tcp.getSuccessContains());
            return Map.of(
                    "success", success,
                    "deviceCode", valueOrEmpty(device.getCode()),
                    "deviceName", valueOrEmpty(device.getName()),
                    "status", success ? "执行成功" : "执行失败",
                    "response", responseText
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException("硬件 TCP 连接失败：" + exception.getMessage());
        }
    }

    private HttpResult executeHttp(IotHardwareProperties.HardwareDevice device, IotHardwareProperties.HardwareCommand command, Map<String, Object> payload) {
        URI uri = URI.create(resolveUrl(device, command.getPath()));
        String method = firstNonBlank(command.getMethod(), "POST").toUpperCase(Locale.ROOT);
        String body = renderTemplate(command.getBody(), device, payload);

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(properties.getTimeout());
        applyAuthAndHeaders(builder, device, command);

        if ("GET".equals(method) || "DELETE".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.method(method, HttpRequest.BodyPublishers.ofString(firstNonBlank(body, "{}"), StandardCharsets.UTF_8));
        }

        try {
            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            int min = command.getSuccessStatusMin() == null ? 200 : command.getSuccessStatusMin();
            int max = command.getSuccessStatusMax() == null ? 299 : command.getSuccessStatusMax();
            boolean success = response.statusCode() >= min && response.statusCode() <= max;
            String responseBody = new String(response.body(), StandardCharsets.UTF_8);
            return new HttpResult(response.statusCode(), response.headers().map(), response.body(), responseBody, success);
        } catch (IOException exception) {
            throw new IllegalArgumentException("硬件 HTTP 连接失败：" + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("硬件 HTTP 请求被中断");
        }
    }

    private void applyAuthAndHeaders(HttpRequest.Builder builder, IotHardwareProperties.HardwareDevice device, IotHardwareProperties.HardwareCommand command) {
        builder.header("Accept", "*/*");
        if (StringUtils.hasText(command.getBody())) {
            builder.header("Content-Type", "application/json;charset=UTF-8");
        }

        device.getHeaders().forEach(builder::header);
        command.getHeaders().forEach(builder::header);

        if (StringUtils.hasText(device.getToken())) {
            builder.header("Authorization", "Bearer " + device.getToken());
            return;
        }

        if (StringUtils.hasText(device.getUsername()) && StringUtils.hasText(device.getPassword())) {
            String raw = device.getUsername() + ":" + device.getPassword();
            builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private IotHardwareProperties.HardwareCommand createAdHocCommand(String method, String path) {
        IotHardwareProperties.HardwareCommand command = new IotHardwareProperties.HardwareCommand();
        command.setMethod(method);
        command.setPath(path);
        return command;
    }

    private String resolveUrl(IotHardwareProperties.HardwareDevice device, String path) {
        String renderedPath = renderTemplate(path, device, Map.of());
        if (!StringUtils.hasText(renderedPath)) {
            throw new IllegalArgumentException(device.getName() + " 未配置硬件请求地址");
        }
        if (renderedPath.startsWith("http://") || renderedPath.startsWith("https://")) {
            return renderedPath;
        }
        if (!StringUtils.hasText(device.getBaseUrl())) {
            throw new IllegalArgumentException(device.getName() + " 未配置 baseUrl");
        }
        return device.getBaseUrl().replaceAll("/+$", "") + "/" + renderedPath.replaceAll("^/+", "");
    }

    private String renderTemplate(String template, IotHardwareProperties.HardwareDevice device, Map<String, Object> payload) {
        if (template == null) {
            return "";
        }

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("deviceCode", valueOrEmpty(device.getCode()));
        values.put("deviceName", valueOrEmpty(device.getName()));
        values.put("labId", device.getLabId() == null ? "" : device.getLabId());
        values.put("labCode", valueOrEmpty(device.getLabCode()));
        if (payload != null) {
            values.putAll(payload);
        }

        String rendered = template;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String value = String.valueOf(entry.getValue());
            rendered = rendered.replace("${" + entry.getKey() + "}", value);
            rendered = rendered.replace("{" + entry.getKey() + "}", value);
        }
        return rendered;
    }

    private byte[] toCommandBytes(String payload, String charsetName) {
        if (!StringUtils.hasText(payload)) {
            return new byte[0];
        }

        if (payload.startsWith("hex:")) {
            String hex = payload.substring(4).replaceAll("[^0-9A-Fa-f]", "");
            byte[] bytes = new byte[hex.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                int index = i * 2;
                bytes[i] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
            }
            return bytes;
        }

        return payload.getBytes(Charset.forName(firstNonBlank(charsetName, StandardCharsets.UTF_8.name())));
    }

    private boolean matchesSuccessBody(IotHardwareProperties.HardwareCommand command, String body) {
        return !StringUtils.hasText(command.getSuccessContains()) || body.contains(command.getSuccessContains());
    }

    private Optional<IotHardwareProperties.HardwareDevice> findLabDevice(Long labId, String type) {
        return properties.getDevices().stream()
                .filter(device -> Objects.equals(device.getLabId(), labId))
                .filter(device -> type.equals(normalizeType(device.getType())))
                .findFirst();
    }

    private Optional<IotHardwareProperties.HardwareDevice> findDevice(String code) {
        return properties.getDevices().stream()
                .filter(device -> Objects.equals(device.getCode(), code))
                .findFirst();
    }

    private boolean isCamera(IotHardwareProperties.HardwareDevice device) {
        return "camera".equals(normalizeType(device.getType()));
    }

    private Map<String, Object> sanitizeDevice(IotHardwareProperties.HardwareDevice device) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", valueOrEmpty(device.getCode()));
        result.put("name", valueOrEmpty(device.getName()));
        result.put("type", normalizeType(device.getType()));
        result.put("labId", device.getLabId());
        result.put("labCode", valueOrEmpty(device.getLabCode()));
        result.put("protocol", firstNonBlank(device.getProtocol(), "http"));
        result.put("configured", true);
        result.put("streamUrl", valueOrEmpty(firstNonBlank(device.getHlsUrl(), device.getWebrtcUrl(), device.getFlvUrl(), device.getStreamUrl())));
        result.put("hlsUrl", valueOrEmpty(device.getHlsUrl()));
        result.put("flvUrl", valueOrEmpty(device.getFlvUrl()));
        result.put("webrtcUrl", valueOrEmpty(device.getWebrtcUrl()));
        result.put("rtspUrl", valueOrEmpty(device.getStreamUrl()));
        result.put("snapshotConfigured", StringUtils.hasText(device.getSnapshotUrl()));
        return result;
    }

    private String normalizeType(String type) {
        String value = valueOrEmpty(type).toLowerCase(Locale.ROOT).trim();
        if (List.of("door", "access", "access-control", "lock", "electronic-lock", "门禁", "电子锁").contains(value)
                || value.contains("门禁")
                || value.contains("门锁")) {
            return "access-control";
        }
        if (List.of("camera", "video", "摄像头", "监控").contains(value)
                || value.contains("摄像")
                || value.contains("监控")) {
            return "camera";
        }
        if (List.of("light", "lighting", "lamp", "relay", "照明", "灯光", "继电器").contains(value)
                || value.contains("照明")
                || value.contains("灯")
                || value.contains("relay")) {
            return "light";
        }
        if (List.of("air", "ac", "hvac", "air-conditioner", "conditioner", "空调", "温控", "新风").contains(value)
                || value.contains("空调")
                || value.contains("温控")
                || value.contains("hvac")) {
            return "air-conditioner";
        }
        if (List.of("sensor", "environment", "smoke", "flame", "传感器", "烟感", "温湿度").contains(value)
                || value.contains("传感")
                || value.contains("烟感")
                || value.contains("温湿度")) {
            return "sensor";
        }
        if (List.of("safety", "fire", "security", "安防", "消防").contains(value)
                || value.contains("安防")
                || value.contains("消防")) {
            return "safety";
        }
        return value;
    }

    private String normalizeAction(String action) {
        String value = valueOrEmpty(action).toLowerCase(Locale.ROOT).trim();
        return switch (value) {
            case "远程开门", "开门", "open", "unlock" -> "open";
            case "锁定", "关门", "close", "lock" -> "lock";
            case "卡录入", "录卡", "card", "card-enroll", "card_enroll", "enroll-card" -> "card-enroll";
            case "学号录入", "录入学号", "student", "student-enroll", "student_enroll", "enroll-student" -> "student-enroll";
            case "人脸录入", "录入人脸", "face", "face-enroll", "face_enroll", "enroll-face" -> "face-enroll";
            case "指纹录入", "录入指纹", "fingerprint", "fingerprint-enroll", "fingerprint_enroll", "enroll-fingerprint" -> "fingerprint-enroll";
            case "电子锁", "锁状态", "门锁状态", "status", "lock-status", "lock_status" -> "status";
            case "read-environment", "read_environment", "environment", "温湿度", "刷新温湿度" -> "read-environment";
            default -> value;
        };
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("物联网硬件接入未启用");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private record HttpResult(int statusCode, Map<String, List<String>> headers, byte[] bodyBytes, String bodyText, boolean success) {
    }
}
