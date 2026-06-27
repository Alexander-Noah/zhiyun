package org.example.backend.service.impl;

import org.example.backend.entity.DeviceInventoryRecordEntity;
import org.example.backend.entity.DeviceTransferRecordEntity;
import org.example.backend.entity.DevicesEntity;
import org.example.backend.entity.LabEntity;
import org.example.backend.mapper.DeviceInventoryRecordMapper;
import org.example.backend.mapper.DeviceTransferRecordMapper;
import org.example.backend.mapper.DevicesMapper;
import org.example.backend.mapper.LabMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.DevicesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DevicesServiceImpl implements DevicesService {
    private static final String STATUS_NORMAL = "正常";
    private static final String STATUS_REPAIRING = "维修中";
    private static final String STATUS_SCRAPPED = "已报废";
    private static final String HEALTH_GOOD = "良好";
    private static final String DEFAULT_UNIT = "台";
    private static final String CATEGORY_COMPUTER = "计算终端";
    private static final String CATEGORY_NETWORK = "网络设备";
    private static final String CATEGORY_DISPLAY = "显示设备";
    private static final String CATEGORY_ENVIRONMENT = "环境设备";
    private static final String CATEGORY_OTHER = "其他设备";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern ROOM_NUMBER_PATTERN = Pattern.compile("(\\d+[A-Za-z0-9-]*)");
    private static final Pattern DEVICE_QUANTITY_PATTERN =
            Pattern.compile("(.+?)\\s*([0-9]+)\\s*(台 | 套 | 个 | 件 | 组 | 批 | 只 | 块 | 条 | 根)");
    private static final Pattern DEVICE_TYPE_BOUNDARY_PATTERN =
            Pattern.compile("\\s+((?:台式电脑|电脑|交换机|投影仪|投影机|空调|显示器|路由器)[：:])");
    private static final AtomicLong CODE_SEQUENCE = new AtomicLong(System.currentTimeMillis() % 100000);

    private final DevicesMapper devicesMapper;
    private final DeviceInventoryRecordMapper deviceInventoryRecordMapper;
    private final DeviceTransferRecordMapper deviceTransferRecordMapper;
    private final LabMapper labMapper;
    private final BusinessLoopService businessLoopService;

    public DevicesServiceImpl(
            DevicesMapper devicesMapper,
            DeviceInventoryRecordMapper deviceInventoryRecordMapper,
            DeviceTransferRecordMapper deviceTransferRecordMapper,
            LabMapper labMapper,
            BusinessLoopService businessLoopService
    ) {
        this.devicesMapper = devicesMapper;
        this.deviceInventoryRecordMapper = deviceInventoryRecordMapper;
        this.deviceTransferRecordMapper = deviceTransferRecordMapper;
        this.labMapper = labMapper;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public List<DevicesEntity> getDevices() {
        List<DevicesEntity> devices = devicesMapper.getDevices();
        return devices == null ? List.of() : devices;
    }

    @Override
    public DevicePageResult pageDevices(DevicePageQuery query) {
        DevicePageQuery safeQuery = normalizePageQuery(query);
        int offset = (safeQuery.getPageNum() - 1) * safeQuery.getPageSize();
        List<DevicesEntity> records = devicesMapper.pageDevices(
                offset,
                safeQuery.getPageSize(),
                emptyToNull(safeQuery.getKeyword()),
                safeQuery.getLabId(),
                emptyToNull(safeQuery.getLabName()),
                emptyToNull(safeQuery.getCategory()),
                emptyToNull(safeQuery.getStatus())
        );
        long total = devicesMapper.countDevices(
                emptyToNull(safeQuery.getKeyword()),
                safeQuery.getLabId(),
                emptyToNull(safeQuery.getLabName()),
                emptyToNull(safeQuery.getCategory()),
                emptyToNull(safeQuery.getStatus())
        );
        return new DevicePageResult(records == null ? List.of() : records, total);
    }

    @Override
    public Map<String, Object> getDeviceStats() {
        Map<String, Object> stats = devicesMapper.getDeviceStats();
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("total", toLong(stats == null ? null : stats.get("total")));
        normalized.put("normal", toLong(stats == null ? null : stats.get("normal")));
        normalized.put("repairing", toLong(stats == null ? null : stats.get("repairing")));
        normalized.put("scrapped", toLong(stats == null ? null : stats.get("scrapped")));
        return normalized;
    }

    @Override
    public DevicesEntity InserterDevices(DevicesEntity devicesEntity) {
        normalizeDevice(devicesEntity);
        devicesMapper.InserterDevices(devicesEntity);
        DevicesEntity savedDevice = devicesEntity.getId() == null
                ? devicesEntity
                : devicesMapper.getDevicesById(devicesEntity.getId());
        recordBusinessEvent("create", savedDevice, savedDevice == null ? null : savedDevice.getStatus());
        return savedDevice;
    }

    @Override
    public DevicesEntity getDevicesById(Long id) {
        return devicesMapper.getDevicesById(id);
    }

    @Override
    public DevicesEntity updateDevices(Long id, DevicesEntity devices) {
        normalizeDevice(devices);
        int updatedCount = devicesMapper.updateDevices(id, devices);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("Device not found or deleted");
        }

        DevicesEntity savedDevice = devicesMapper.getDevicesById(id);
        recordBusinessEvent("update", savedDevice, savedDevice == null ? null : savedDevice.getStatus());
        return savedDevice;
    }

    @Override
    public List<DevicesEntity> updateDevices(List<DevicesEntity> devices) {
        if (devices == null || devices.isEmpty()) {
            return getDevices();
        }

        for (DevicesEntity device : devices) {
            if (device.getId() == null) {
                InserterDevices(device);
            } else {
                updateDevices(device.getId(), device);
            }
        }

        return getDevices();
    }

    @Override
    public void deleteDevices(Long id) {
        DevicesEntity device = devicesMapper.getDevicesById(id);
        devicesMapper.deleteDevices(id);
        recordBusinessEvent("delete", device, STATUS_SCRAPPED);
    }

    @Override
    public List<DevicesEntity> getDevicesByLabId(Integer labId) {
        if (labId == null) {
            return List.of();
        }
        List<DevicesEntity> devices = devicesMapper.getDevicesByLabId(labId);
        return devices == null ? List.of() : devices;
    }

    @Override
    public List<DeviceInventoryRecordEntity> listInventoryRecords(Long deviceId) {
        deviceInventoryRecordMapper.createTableIfNotExists();
        List<DeviceInventoryRecordEntity> records = deviceInventoryRecordMapper.listRecords(deviceId);
        return records == null ? List.of() : records;
    }

    @Override
    public DeviceInventoryRecordResult recordDeviceInventory(Long deviceId, DeviceInventoryRecordEntity record) {
        DevicesEntity device = requireDevice(deviceId);
        deviceInventoryRecordMapper.createTableIfNotExists();

        record.setDeviceId(device.getId());
        record.setDeviceCode(defaultText(record.getDeviceCode(), device.getDeviceCode()));
        record.setDeviceName(defaultText(record.getDeviceName(), device.getDeviceName()));
        record.setInspectorName(defaultText(record.getInspectorName(), "lab admin"));
        record.setInspectedAt(defaultText(record.getInspectedAt(), LocalDateTime.now().format(DATE_TIME_FORMATTER)));
        record.setResultStatus(defaultText(record.getResultStatus(), "已盘点"));
        record.setNormal(Boolean.TRUE.equals(record.getNormal()));
        record.setMissing(Boolean.TRUE.equals(record.getMissing()));
        record.setDamaged(Boolean.TRUE.equals(record.getDamaged()));
        record.setRemark(textOrEmpty(record.getRemark()));
        deviceInventoryRecordMapper.insertRecord(record);

        String inventoryDate = record.getInspectedAt().length() >= 10
                ? record.getInspectedAt().substring(0, 10)
                : LocalDate.now().toString();
        devicesMapper.updateInventoryState(deviceId, record.getResultStatus(), device.getHealth(), device.getOnline(), inventoryDate);
        DevicesEntity savedDevice = devicesMapper.getDevicesById(deviceId);
        List<DeviceInventoryRecordEntity> records = deviceInventoryRecordMapper.listRecordsByDeviceId(deviceId);
        return new DeviceInventoryRecordResult(savedDevice, record, records == null ? List.of() : records);
    }

    @Override
    public List<DeviceTransferRecordEntity> listTransferRecords(Long deviceId) {
        deviceTransferRecordMapper.createTableIfNotExists();
        List<DeviceTransferRecordEntity> records = deviceTransferRecordMapper.listRecords(deviceId);
        return records == null ? List.of() : records;
    }

    @Override
    @Transactional
    public DeviceTransferRecordResult transferDevice(Long deviceId, DeviceTransferRecordEntity record) {
        DevicesEntity device = requireDevice(deviceId);
        deviceTransferRecordMapper.createTableIfNotExists();

        record.setDeviceId(device.getId());
        record.setDeviceCode(defaultText(record.getDeviceCode(), device.getDeviceCode()));
        record.setDeviceName(defaultText(record.getDeviceName(), device.getDeviceName()));
        record.setFromLabId(device.getLabId());
        record.setFromLabName(textOrEmpty(device.getLabName()));
        record.setFromOwnerUserId(device.getOwnerUserId());
        record.setFromOwnerName(textOrEmpty(device.getOwnerUsername()));
        record.setFromLocation(textOrEmpty(device.getLocation()));
        record.setToLocation(defaultText(record.getToLocation(), device.getLocation()));
        record.setTransferType(defaultText(record.getTransferType(), "设备调拨"));
        record.setOperatorName(defaultText(record.getOperatorName(), "lab admin"));
        record.setTransferAt(defaultText(record.getTransferAt(), LocalDateTime.now().format(DATE_TIME_FORMATTER)));
        record.setReason(textOrEmpty(record.getReason()));

        devicesMapper.updateTransferState(deviceId, record.getToLabId(), record.getToOwnerUserId(), record.getToLocation());
        deviceTransferRecordMapper.insertRecord(record);
        DevicesEntity savedDevice = devicesMapper.getDevicesById(deviceId);
        List<DeviceTransferRecordEntity> records = deviceTransferRecordMapper.listRecordsByDeviceId(deviceId);
        return new DeviceTransferRecordResult(savedDevice, record, records == null ? List.of() : records);
    }

    @Override
    @Transactional
    public DeviceImportResult importDevices(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传Excel或csv文件");
        }

        List<DevicesEntity> parsedDevices = parseDeviceImportFile(file);
        int importedCount = 0;
        for (DevicesEntity device : parsedDevices) {
            if (isBlank(device.getDeviceName())) {
                continue;
            }
            InserterDevices(device);
            importedCount++;
        }

        return new DeviceImportResult(true, "导入成功", importedCount);
    }

    @Override
    public byte[] exportDevices(DevicePageQuery query) {
        DevicePageQuery exportQuery = normalizePageQuery(query);
        exportQuery.setPageNum(1);
        exportQuery.setPageSize(100000);
        List<DevicesEntity> records = pageDevices(exportQuery).getRecords();
        StringBuilder csv = new StringBuilder("\ufeff");
        csv.append("assetNo,deviceName,category,labName,roomNo,quantity,unit,status,specification,standardRequirement,remark\r\n");
        for (DevicesEntity device : records) {
            csv.append(csvCell(device.getDeviceCode())).append(',')
                    .append(csvCell(device.getDeviceName())).append(',')
                    .append(csvCell(device.getCategory())).append(',')
                    .append(csvCell(device.getLabName())).append(',')
                    .append(csvCell(device.getLocation())).append(',')
                    .append(csvCell(device.getQuantity())).append(',')
                    .append(csvCell(device.getUnit())).append(',')
                    .append(csvCell(device.getStatus())).append(',')
                    .append(csvCell(device.getSpecs())).append(',')
                    .append(csvCell(device.getStandardRequirement())).append(',')
                    .append(csvCell(device.getRemark())).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<DevicesEntity> parseDeviceImportFile(MultipartFile file) {
        try {
            String filename = textOrEmpty(file.getOriginalFilename()).toLowerCase();
            List<Map<String, Object>> rows = filename.endsWith(".csv")
                    ? readCsvRows(file.getInputStream())
                    : readXlsxRows(file.getInputStream());

            List<DevicesEntity> devices = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                devices.addAll(mapImportRow(row));
            }
            return devices;
        } catch (IOException error) {
            throw new IllegalArgumentException("Failed to read import file: " + error.getMessage(), error);
        }
    }

    private List<Map<String, Object>> readCsvRows(InputStream inputStream) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
            rows = parseCsvContent(content.toString().replace("\ufeff", ""));
        }
        return rowsToObjects(rows);
    }

    private List<List<String>> parseCsvContent(String content) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean insideQuotes = false;

        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            char next = index + 1 < content.length() ? content.charAt(index + 1) : '\0';

            if (current == '"' && insideQuotes && next == '"') {
                value.append('"');
                index++;
                continue;
            }
            if (current == '"') {
                insideQuotes = !insideQuotes;
                continue;
            }
            if (current == ',' && !insideQuotes) {
                row.add(value.toString());
                value.setLength(0);
                continue;
            }
            if ((current == '\n' || current == '\r') && !insideQuotes) {
                if (current == '\r' && next == '\n') {
                    index++;
                }
                row.add(value.toString());
                value.setLength(0);
                if (row.stream().anyMatch(item -> !textOrEmpty(item).isBlank())) {
                    rows.add(row);
                }
                row = new ArrayList<>();
                continue;
            }
            value.append(current);
        }

        row.add(value.toString());
        if (row.stream().anyMatch(item -> !textOrEmpty(item).isBlank())) {
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> readXlsxRows(InputStream inputStream) throws IOException {
        Map<String, byte[]> entries = readZipEntries(inputStream);
        List<String> sharedStrings = parseSharedStrings(entries.get("xl/sharedStrings.xml"));
        byte[] worksheet = entries.get("xl/worksheets/sheet1.xml");
        if (worksheet == null) {
            throw new IllegalArgumentException("Excel file does not contain sheet1");
        }
        return rowsToObjects(parseWorksheetRows(worksheet, sharedStrings));
    }

    private Map<String, byte[]> readZipEntries(InputStream inputStream) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private List<String> parseSharedStrings(byte[] xmlBytes) {
        List<String> sharedStrings = new ArrayList<>();
        if (xmlBytes == null) {
            return sharedStrings;
        }

        Document document = parseXml(xmlBytes);
        NodeList items = document.getElementsByTagNameNS("*", "si");
        for (int index = 0; index < items.getLength(); index++) {
            sharedStrings.add(nodeText(items.item(index)));
        }
        return sharedStrings;
    }

    private List<List<String>> parseWorksheetRows(byte[] xmlBytes, List<String> sharedStrings) {
        Document document = parseXml(xmlBytes);
        Map<String, String> cells = new HashMap<>();
        int maxRow = 0;
        int maxColumn = 0;

        NodeList cellNodes = document.getElementsByTagNameNS("*", "c");
        for (int index = 0; index < cellNodes.getLength(); index++) {
            Element cell = (Element) cellNodes.item(index);
            String reference = cell.getAttribute("r");
            int row = cellRow(reference);
            int column = cellColumn(reference);
            if (row <= 0 || column <= 0) {
                continue;
            }
            cells.put(cellKey(row, column), readCellValue(cell, sharedStrings));
            maxRow = Math.max(maxRow, row);
            maxColumn = Math.max(maxColumn, column);
        }

        NodeList mergeNodes = document.getElementsByTagNameNS("*", "mergeCell");
        for (int index = 0; index < mergeNodes.getLength(); index++) {
            String reference = ((Element) mergeNodes.item(index)).getAttribute("ref");
            String[] range = reference.split(":");
            if (range.length != 2) {
                continue;
            }
            int startRow = cellRow(range[0]);
            int startColumn = cellColumn(range[0]);
            int endRow = cellRow(range[1]);
            int endColumn = cellColumn(range[1]);
            String value = cells.getOrDefault(cellKey(startRow, startColumn), "");
            for (int row = startRow; row <= endRow; row++) {
                for (int column = startColumn; column <= endColumn; column++) {
                    cells.putIfAbsent(cellKey(row, column), value);
                    maxRow = Math.max(maxRow, row);
                    maxColumn = Math.max(maxColumn, column);
                }
            }
        }

        List<List<String>> rows = new ArrayList<>();
        for (int row = 1; row <= maxRow; row++) {
            List<String> values = new ArrayList<>();
            for (int column = 1; column <= maxColumn; column++) {
                values.add(cells.getOrDefault(cellKey(row, column), ""));
            }
            if (values.stream().anyMatch(value -> !value.isBlank())) {
                rows.add(values);
            }
        }
        return rows;
    }

    private Document parseXml(byte[] xmlBytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(xmlBytes)));
        } catch (Exception error) {
            throw new IllegalArgumentException("Failed to parse Excel XML", error);
        }
    }

    private String readCellValue(Element cell, List<String> sharedStrings) {
        String type = cell.getAttribute("t");
        String rawValue = firstChildText(cell, "v");

        if ("inlineStr".equals(type)) {
            return nodeText(cell);
        }
        if ("s".equals(type) && !rawValue.isBlank()) {
            int sharedIndex = Integer.parseInt(rawValue);
            return sharedIndex >= 0 && sharedIndex < sharedStrings.size() ? sharedStrings.get(sharedIndex) : "";
        }
        if ("b".equals(type)) {
            return "1".equals(rawValue) ? "true" : "false";
        }
        return rawValue;
    }

    private String firstChildText(Element element, String localName) {
        NodeList nodes = element.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? "" : textOrEmpty(nodes.item(0).getTextContent());
    }

    private String nodeText(Node node) {
        return node == null ? "" : textOrEmpty(node.getTextContent());
    }

    private int cellRow(String reference) {
        String digits = reference == null ? "" : reference.replaceAll("[^0-9]", "");
        return digits.isBlank() ? 0 : Integer.parseInt(digits);
    }

    private int cellColumn(String reference) {
        String letters = reference == null ? "" : reference.replaceAll("[^A-Za-z]", "").toUpperCase();
        int column = 0;
        for (int index = 0; index < letters.length(); index++) {
            column = column * 26 + letters.charAt(index) - 'A' + 1;
        }
        return column;
    }

    private String cellKey(int row, int column) {
        return row + ":" + column;
    }

    private List<Map<String, Object>> rowsToObjects(List<List<String>> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<String> headers = rows.get(0).stream().map(this::textOrEmpty).toList();
        List<Map<String, Object>> records = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            Map<String, Object> record = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                String header = headers.get(column);
                if (!header.isBlank()) {
                    record.put(header, column < row.size() ? textOrEmpty(row.get(column)) : "");
                }
            }
            if (record.values().stream().anyMatch(value -> !textOrEmpty(value).isBlank())) {
                records.add(record);
            }
        }
        return records;
    }

    private List<DevicesEntity> mapImportRow(Map<String, Object> row) {
        String labName = textValue(row, "labName", "lab", "实验室名称", "所属实验室");
        String roomNo = textValue(row, "roomNo", "location", "地点", "安转位置");
        String mainDeviceText = textValue(row, "mainDeviceName", "主要设备名称");
        String structuredDeviceName = textValue(row, "deviceName", "name", "设备名称");
        String category = textValue(row, "category", "设备分类");
        String quantityText = textValue(row, "quantity", "数量");
        String unitText = textValue(row, "unit", "单位");
        String status = defaultText(textValue(row, "status", "状态"), STATUS_NORMAL);
        String specification = textValue(row, "specification", "specs", "主要功能和技术要求", "规格参数");
        String standard = textValue(row, "standardRequirement", "执行标准和数量要求", "执行标准");
        String remark = textValue(row, "remark", "备注");

        List<DeviceTextItem> items = splitDeviceText(mainDeviceText, false);
        boolean useMainDeviceText = !items.isEmpty();
        if (!useMainDeviceText) {
            items = splitDeviceText(structuredDeviceName, true);
        }

        List<DevicesEntity> devices = new ArrayList<>();
        for (DeviceTextItem item : items) {
            DevicesEntity device = new DevicesEntity();
            device.setDeviceCode(textValue(row, "assetNo", "deviceCode", "资产编号"));
            device.setDeviceName(item.deviceName());
            device.setCategory(resolveCategory(item, category));
            device.setLabName(labName);
            device.setLocation(roomNo);
            device.setQuantity(!useMainDeviceText && !isBlank(quantityText) ? parseQuantity(quantityText) : item.quantity());
            device.setUnit(!useMainDeviceText ? defaultText(unitText, item.unit()) : item.unit());
            device.setStatus(status);
            device.setSpecs(resolveSpecs(item, structuredDeviceName, specification));
            device.setStandardRequirement(standard);
            device.setRemark(remark);
            device.setSourceType("Excel");
            normalizeDevice(device);
            devices.add(device);
        }
        return devices;
    }

    private List<DeviceTextItem> splitDeviceText(String value, boolean allowPlainFallback) {
        String text = textOrEmpty(value).replace("\r\n", "\n").replace('\r', '\n');
        if (text.isBlank()) {
            return List.of();
        }

        text = DEVICE_TYPE_BOUNDARY_PATTERN.matcher(text).replaceAll("\n$1");
        List<DeviceTextItem> items = new ArrayList<>();
        for (String line : text.split("\\n+")) {
            DeviceLine deviceLine = splitDeviceLine(line);
            if (deviceLine.deviceName().isBlank()) {
                continue;
            }
            Matcher matcher = DEVICE_QUANTITY_PATTERN.matcher(deviceLine.deviceName());
            boolean matched = false;
            while (matcher.find()) {
                String deviceName = matcher.group(1).trim();
                if (!deviceName.isBlank()) {
                    items.add(new DeviceTextItem(deviceLine.deviceType(), deviceName, parseQuantity(matcher.group(2)), matcher.group(3)));
                    matched = true;
                }
            }
            if (!matched && allowPlainFallback) {
                items.add(new DeviceTextItem(deviceLine.deviceType(), deviceLine.deviceName(), 1, DEFAULT_UNIT));
            }
        }
        return items;
    }

    private DeviceLine splitDeviceLine(String value) {
        String text = textOrEmpty(value).trim();
        String deviceType = "";
        int colonIndex = text.indexOf('：');
        if (colonIndex < 0) {
            colonIndex = text.indexOf(':');
        }
        if (colonIndex >= 0) {
            deviceType = text.substring(0, colonIndex).trim();
            text = text.substring(colonIndex + 1).trim();
        }
        return new DeviceLine(deviceType, text);
    }

    private record DeviceTextItem(String deviceType, String deviceName, int quantity, String unit) {
    }

    private record DeviceLine(String deviceType, String deviceName) {
    }

    private void normalizeDevice(DevicesEntity device) {
        if (device == null) {
            throw new IllegalArgumentException("Device is required");
        }
        if (isBlank(device.getDeviceName())) {
            throw new IllegalArgumentException("Device name is required");
        }
        bindDeviceLab(device);
        if (isBlank(device.getDeviceCode())) {
            device.setDeviceCode(generateDeviceCode(device));
        }
        if (isBlank(device.getCategory())) {
            device.setCategory(resolveCategory(device.getDeviceName()));
        }

        if (device.getQuantity() == null || device.getQuantity() <= 0) {
            device.setQuantity(1);
        }
        if (isBlank(device.getUnit())) {
            device.setUnit(DEFAULT_UNIT);
        }
        if (isBlank(device.getStatus())) {
            device.setStatus(STATUS_NORMAL);
        }
        if (!STATUS_NORMAL.equals(device.getStatus())
                && !STATUS_REPAIRING.equals(device.getStatus())
                && !STATUS_SCRAPPED.equals(device.getStatus())) {
            device.setStatus(STATUS_NORMAL);
        }
        if (isBlank(device.getHealth())) {
            device.setHealth(HEALTH_GOOD);
        }
        if (device.getOnline() == null) {
            device.setOnline(true);
        }
        if (device.getUsageHours() == null) {
            device.setUsageHours(0);
        }
        if (isBlank(device.getSourceType())) {
            device.setSourceType("manual");
        }
        if (device.getDeleted() == null) {
            device.setDeleted(0);
        }
    }

    private void bindDeviceLab(DevicesEntity device) {
        LabEntity matchedLab = matchLab(device);
        if (matchedLab == null) {
            return;
        }

        device.setLabId(matchedLab.getId() == null ? null : matchedLab.getId().longValue());
        device.setLabName(matchedLab.getLabName());
        device.setLocation(matchedLab.getRoomNo());
    }

    private LabEntity matchLab(DevicesEntity device) {
        if (labMapper == null) {
            return null;
        }

        List<LabEntity> labs = labMapper.getLabs();
        if (labs == null || labs.isEmpty()) {
            return null;
        }

        if (device.getLabId() != null) {
            LabEntity matchedById = labs.stream()
                    .filter(lab -> lab.getId() != null && Objects.equals(lab.getId().longValue(), device.getLabId()))
                    .findFirst()
                    .orElse(null);
            if (matchedById != null) {
                return matchedById;
            }
        }

        String location = textOrEmpty(device.getLocation());
        if (!location.isBlank()) {
            LabEntity matchedByRoom = labs.stream()
                    .filter(lab -> sameText(lab.getRoomNo(), location))
                    .findFirst()
                    .orElse(null);
            if (matchedByRoom != null) {
                return matchedByRoom;
            }
        }

        String labName = textOrEmpty(device.getLabName());
        if (!labName.isBlank()) {
            LabEntity matchedByName = labs.stream()
                    .filter(lab -> sameText(lab.getLabName(), labName))
                    .findFirst()
                    .orElse(null);
            if (matchedByName != null) {
                return matchedByName;
            }

            LabEntity matchedByFuzzyName = labs.stream()
                    .filter(lab -> containsText(labName, lab.getLabName()) || containsText(lab.getLabName(), labName))
                    .findFirst()
                    .orElse(null);
            if (matchedByFuzzyName != null) {
                return matchedByFuzzyName;
            }
        }

        if (!location.isBlank()) {
            LabEntity matchedByLocation = labs.stream()
                    .filter(lab -> containsText(location, lab.getRoomNo()))
                    .findFirst()
                    .orElse(null);
            if (matchedByLocation != null) {
                return matchedByLocation;
            }
        }

        String searchable = String.join(" ",
                textOrEmpty(device.getDeviceName()),
                textOrEmpty(device.getSpecs()),
                textOrEmpty(device.getStandardRequirement()),
                textOrEmpty(device.getRemark()),
                labName,
                location
        );

        return labs.stream()
                .filter(lab -> containsText(searchable, lab.getLabName()) || containsText(searchable, lab.getRoomNo()))
                .findFirst()
                .orElse(null);
    }

    private DevicePageQuery normalizePageQuery(DevicePageQuery query) {
        DevicePageQuery safeQuery = query == null ? new DevicePageQuery() : query;
        safeQuery.setPageNum(Math.max(safeQuery.getPageNum(), 1));
        safeQuery.setPageSize(Math.min(Math.max(safeQuery.getPageSize(), 1), 100000));
        return safeQuery;
    }

    private DevicesEntity requireDevice(Long deviceId) {
        if (deviceId == null) {
            throw new IllegalArgumentException("Device id is required");
        }
        DevicesEntity device = devicesMapper.getDevicesById(deviceId);
        if (device == null) {
            throw new IllegalArgumentException("Device not found or deleted");
        }
        return device;
    }

    private String generateDeviceCode(DevicesEntity device) {
        String location = defaultText(device.getLocation(), "000");
        Matcher matcher = ROOM_NUMBER_PATTERN.matcher(location);
        String room = matcher.find() ? matcher.group(1).replaceAll("[^A-Za-z0-9]", "") : "000";
        return "DEV-" + room + "-" + CODE_SEQUENCE.incrementAndGet();
    }

    private String resolveCategory(String deviceName) {
        String name = textOrEmpty(deviceName);
        if (name.matches(".*(电脑|主机|工作站|计算机|服务器).*")) {
            return CATEGORY_COMPUTER;
        }
        if (name.matches(".*(交换机|路由器|网络).*")) {
            return CATEGORY_NETWORK;
        }
        if (name.matches(".*(投影仪|显示器|屏幕).*")) {
            return CATEGORY_DISPLAY;
        }
        if (name.matches(".*(空调|温控).*")) {
            return CATEGORY_ENVIRONMENT;
        }
        return CATEGORY_OTHER;
    }

    private String resolveCategory(DeviceTextItem item, String fallbackCategory) {
        String category = resolveCategory(item.deviceType() + " " + item.deviceName());
        if (CATEGORY_OTHER.equals(category) && !isBlank(fallbackCategory)) {
            return fallbackCategory;
        }
        return category;
    }

    private String resolveSpecs(DeviceTextItem item, String structuredDeviceName, String specs) {
        if (isBlank(specs)) {
            return "";
        }
        if (isComputerDevice(item) || normalizedText(item.deviceName()).equals(normalizedText(structuredDeviceName))) {
            return textOrEmpty(specs);
        }
        return "";
    }

    private boolean isComputerDevice(DeviceTextItem item) {
        String name = item.deviceType() + " " + item.deviceName();
        return name.matches(".*(台式电脑|电脑|主机|工作站).*");
    }

    private String normalizedText(String value) {
        return textOrEmpty(value).replaceAll("\\s+", "").toLowerCase();
    }

    private boolean sameText(String left, String right) {
        String normalizedLeft = normalizedText(left);
        String normalizedRight = normalizedText(right);
        return !normalizedLeft.isBlank() && normalizedLeft.equals(normalizedRight);
    }

    private boolean containsText(String text, String value) {
        String normalizedSearchText = normalizedText(text);
        String normalizedValue = normalizedText(value);
        return !normalizedSearchText.isBlank()
                && !normalizedValue.isBlank()
                && normalizedSearchText.contains(normalizedValue);
    }

    private String textValue(Map<String, Object> row, String... keys) {
        if (row == null) {
            return "";
        }
        Map<String, Object> normalized = new HashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            normalized.put(normalizeKey(entry.getKey()), entry.getValue());
        }
        for (String key : keys) {
            Object value = normalized.get(normalizeKey(key));
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private String normalizeKey(String key) {
        return textOrEmpty(key).replaceAll("\\s+", "").toLowerCase();
    }

    private int parseQuantity(String value) {
        String text = textOrEmpty(value);
        if (text.isBlank()) {
            return 1;
        }
        try {
            return Math.max(1, new BigDecimal(text.replaceAll("[^0-9.]", "")).intValue());
        } catch (NumberFormatException error) {
            return 1;
        }
    }

    private String csvCell(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains("\"") || text.contains(",") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private String textOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultText(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException error) {
            return 0L;
        }
    }

    private void recordBusinessEvent(String action, DevicesEntity device, String status) {
        if (device == null) {
            return;
        }
        try {
            businessLoopService.recordEvent("device", action, defaultText(device.getDeviceName(), String.valueOf(device.getId())), textOrEmpty(status), Map.of(
                    "deviceId", Objects.toString(device.getId(), ""),
                    "lab", textOrEmpty(device.getLabName())
            ));
        } catch (RuntimeException ignored) {
            // Business-loop recording is auxiliary and must not break asset maintenance.
        }
    }
}
