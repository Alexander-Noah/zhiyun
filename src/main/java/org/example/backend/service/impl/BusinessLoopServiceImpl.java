package org.example.backend.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.backend.entity.RepairEntity;
import org.example.backend.entity.ReservationsEntity;
import org.example.backend.entity.UsageRecordEntity;
import org.example.backend.mapper.DevicesMapper;
import org.example.backend.mapper.ModuleRecordMapper;
import org.example.backend.mapper.RepairMapper;
import org.example.backend.mapper.UsageRecordMapper;
import org.example.backend.service.BusinessLoopService;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class BusinessLoopServiceImpl implements BusinessLoopService {

    private static final DateTimeFormatter TICKET_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String EVENT_MODULE = "operation-events";

    private static final String STATUS_APPROVED = "已通过";
    private static final String STATUS_EXISTING_REPAIR = "已存在工单";
    private static final String STATUS_CREATED_REPAIR = "已生成工单";
    private static final String STATUS_DONE = "完成";
    private static final String STATUS_NORMAL = "正常";
    private static final String STATUS_MAINTENANCE = "维护中";
    private static final String STATUS_FAULT = "故障";
    private static final String STATUS_WAIT_DISPATCH = "待派单";

    private static final String HEALTH_GOOD = "良好";
    private static final String HEALTH_ATTENTION = "关注";
    private static final String HEALTH_RISK = "风险";

    private final UsageRecordMapper usageRecordMapper;
    private final RepairMapper repairMapper;
    private final DevicesMapper devicesMapper;
    private final ModuleRecordMapper moduleRecordMapper;
    private final ObjectMapper objectMapper;

    public BusinessLoopServiceImpl(
            UsageRecordMapper usageRecordMapper,
            RepairMapper repairMapper,
            DevicesMapper devicesMapper,
            ModuleRecordMapper moduleRecordMapper,
            ObjectMapper objectMapper
    ) {
        this.usageRecordMapper = usageRecordMapper;
        this.repairMapper = repairMapper;
        this.devicesMapper = devicesMapper;
        this.moduleRecordMapper = moduleRecordMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 预约审批通过后，自动生成使用记录
     */
    @Override
    public void syncUsageRecordAfterReservationApproved(ReservationsEntity reservation) {
        if (reservation == null || reservation.getId() == null) {
            return;
        }

        UsageRecordEntity usageRecord = new UsageRecordEntity();
        usageRecord.setPerson(firstNonBlank(reservation.getApplicantName(), reservation.getApplicant()));
        usageRecord.setResource(reservation.getLab());
        usageRecord.setScene(firstNonBlank(reservation.getScene(), "预约"));
        usageRecord.setUseTime(resolveReservationUseTime(reservation));
        usageRecord.setStatus(STATUS_NORMAL);
        usageRecord.setRemark("由预约审批通过自动生成，预约编号" + reservation.getId());

        int exists = usageRecordMapper.countUsageRecordBySignature(
                usageRecord.getPerson(),
                usageRecord.getResource(),
                usageRecord.getScene(),
                usageRecord.getUseTime()
        );

        if (exists == 0) {
            usageRecordMapper.insertUsageRecord(usageRecord);
        }

        recordEvent("reservation", "approve", usageRecord.getResource(), STATUS_APPROVED, Map.of(
                "reservationId", reservation.getId(),
                "person", usageRecord.getPerson(),
                "scene", usageRecord.getScene()
        ));
    }

    /**
     * 使用记录异常后，自动生成维修工单
     */
    @Override
    public RepairEntity createRepairAfterUsageAbnormal(UsageRecordEntity usageRecord) {
        if (usageRecord == null || usageRecord.getId() == null) {
            return null;
        }

        String marker = "使用记录#" + usageRecord.getId();

        if (repairMapper.countOpenRepairByDescriptionMarker(marker) > 0) {
            recordEvent("usage-record", "abnormal", usageRecord.getResource(), STATUS_EXISTING_REPAIR, Map.of(
                    "usageRecordId", usageRecord.getId()
            ));
            return null;
        }

        RepairEntity repair = new RepairEntity();
        repair.setTicket(createTicketNo());
        repair.setReporter(firstNonBlank(usageRecord.getPerson(), "系统巡检"));
        repair.setContact("");
        repair.setLab(usageRecord.getResource());
        repair.setDevice("");
        repair.setFaultType("使用异常");
        repair.setPriority("中");
        repair.setStatus(STATUS_WAIT_DISPATCH);
        repair.setAssignee("待分配");
        repair.setProgress(10);
        repair.setDescription(marker + "：" + firstNonBlank(usageRecord.getRemark(), "使用记录被标记为异常"));
        repair.setResult("由使用记录异常自动生成，待运维接单。");

        repairMapper.insertRepair(repair);

        RepairEntity savedRepair = repairMapper.getRepair(repair.getTicket());

        recordEvent("usage-record", "abnormal", usageRecord.getResource(), STATUS_CREATED_REPAIR, Map.of(
                "usageRecordId", usageRecord.getId(),
                "repairTicket", repair.getTicket()
        ));

        return savedRepair;
    }

    /**
     * 报修状态变化后，同步设备状态
     */
    @Override
    public void syncDeviceAfterRepairChanged(RepairEntity repair) {
        if (repair == null || repair.getDevice() == null || repair.getDevice().isBlank()) {
            return;
        }

        String status = firstNonBlank(repair.getStatus(), "");

        if (status.contains("完成")) {
            updateDeviceState(
                    repair,
                    STATUS_NORMAL,
                    "维修完成：" + firstNonBlank(repair.getResult(), repair.getTicket())
            );
            return;
        }

        if (status.contains("验收") || status.contains("处理")) {
            updateDeviceState(
                    repair,
                    STATUS_MAINTENANCE,
                    "维修处理中：" + firstNonBlank(repair.getResult(), repair.getTicket())
            );
            return;
        }

        updateDeviceState(
                repair,
                STATUS_FAULT,
                "故障报修：" + firstNonBlank(repair.getDescription(), repair.getTicket())
        );
    }

    /**
     * 记录业务事件日志
     */
    @Override
    public void recordEvent(String category, String action, String subject, String status, Map<String, ?> details) {
        Map<String, Object> event = new LinkedHashMap<>();

        event.put("id", "evt-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8));
        event.put("category", firstNonBlank(category, "business"));
        event.put("action", firstNonBlank(action, "operate"));
        event.put("subject", firstNonBlank(subject, "-"));
        event.put("status", firstNonBlank(status, STATUS_DONE));
        event.put("operator", "system");
        event.put("occurredAt", LocalDateTime.now().format(EVENT_TIME));

        if (details != null && !details.isEmpty()) {
            event.put("details", details);
        }

        try {
            moduleRecordMapper.insertRecord(
                    EVENT_MODULE,
                    String.valueOf(event.get("id")),
                    objectMapper.writeValueAsString(event)
            );
        } catch (Exception exception) {
            log.warn("Failed to write business loop event", exception);
        }
    }

    /**
     * 根据报修状态更新设备状态
     */
    private void updateDeviceState(
            RepairEntity repair,
            String status,
            String maintenance
    ) {
        int updated = devicesMapper.updateDeviceAssetMaintenanceStateByNameOrCode(
                repair.getDevice(),
                status,
                maintenance
        );

        if (updated > 0) {
            recordEvent("repair", "device-sync", repair.getDevice(), status, Map.of(
                    "ticket", firstNonBlank(repair.getTicket(), repair.getId())
            ));
        }
    }

    /**
     * 解析预约使用时间
     */
    private LocalDateTime resolveReservationUseTime(ReservationsEntity reservation) {
        if (reservation.getReservationDate() == null) {
            return LocalDateTime.now();
        }

        String timeRange = firstNonBlank(reservation.getTimeRange(), "00:00");
        String startTime = timeRange.length() >= 5 ? timeRange.substring(0, 5) : "00:00";

        try {
            return LocalDateTime.of(reservation.getReservationDate(), LocalTime.parse(startTime));
        } catch (Exception exception) {
            return reservation.getReservationDate().atStartOfDay();
        }
    }

    /**
     * 生成维修工单号
     */
    private String createTicketNo() {
        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 4)
                .toUpperCase();

        return "R" + LocalDateTime.now().format(TICKET_DATE) + suffix;
    }

    /**
     * 返回第一个非空字符串
     */
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
