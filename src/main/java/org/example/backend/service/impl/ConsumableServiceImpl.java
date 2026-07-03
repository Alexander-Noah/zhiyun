package org.example.backend.service.impl;

import org.example.backend.entity.ConsumableEntity;
import org.example.backend.entity.ConsumableStockRecordEntity;
import org.example.backend.entity.NoticeEntity;
import org.example.backend.mapper.ConsumableMapper;
import org.example.backend.mapper.NoticeMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.ConsumableService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConsumableServiceImpl implements ConsumableService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String MOVEMENT_IN = "入库";
    private static final String MOVEMENT_OUT = "出库";

    private final ConsumableMapper consumableMapper;
    private final NoticeMapper noticeMapper;
    private final BusinessLoopService businessLoopService;

    public ConsumableServiceImpl(
            ConsumableMapper consumableMapper,
            NoticeMapper noticeMapper,
            BusinessLoopService businessLoopService
    ) {
        this.consumableMapper = consumableMapper;
        this.noticeMapper = noticeMapper;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public List<ConsumableEntity> listConsumables() {
        return consumableMapper.listConsumables();
    }

    @Override
    @Transactional
    public ConsumableEntity createConsumable(ConsumableEntity consumable) {
        ConsumableEntity normalizedConsumable = normalizeConsumable(consumable);
        consumableMapper.insertConsumable(normalizedConsumable);
        ConsumableEntity savedConsumable = consumableMapper.getConsumable(normalizedConsumable.getId());
        closeLoopAfterStockChanged(savedConsumable, "create");
        return savedConsumable;
    }

    @Override
    @Transactional
    public ConsumableEntity updateConsumable(Long id, ConsumableEntity consumable) {
        consumableMapper.updateConsumable(id, normalizeConsumable(consumable));
        ConsumableEntity savedConsumable = consumableMapper.getConsumable(id);
        closeLoopAfterStockChanged(savedConsumable, "update");
        return savedConsumable;
    }

    @Override
    @Transactional
    public List<ConsumableEntity> deleteConsumable(Long id) {
        consumableMapper.deleteConsumable(id);
        businessLoopService.recordEvent("consumable", "delete", String.valueOf(id), "已删除", Map.of("id", id));
        return consumableMapper.listConsumables();
    }

    @Override
    @Transactional
    public List<ConsumableEntity> replaceConsumables(List<ConsumableEntity> consumables) {
        consumableMapper.deleteAllConsumables();
        for (ConsumableEntity consumable : consumables == null ? Collections.<ConsumableEntity>emptyList() : consumables) {
            ConsumableEntity normalizedConsumable = normalizeConsumable(consumable);
            consumableMapper.insertConsumable(normalizedConsumable);
            closeLoopAfterStockChanged(consumableMapper.getConsumable(normalizedConsumable.getId()), "batch-save");
        }
        return consumableMapper.listConsumables();
    }

    @Override
    @Transactional
    public List<ConsumableEntity> resetConsumables() {
        consumableMapper.deleteAllConsumables();
        for (ConsumableEntity consumable : getDefaultConsumables()) {
            consumableMapper.insertConsumable(normalizeConsumable(consumable));
        }
        businessLoopService.recordEvent("consumable", "reset", "耗材库存", "已恢复初始库存", Map.of("count", getDefaultConsumables().size()));
        return consumableMapper.listConsumables();
    }

    @Override
    public List<ConsumableStockRecordEntity> listStockRecords(Long consumableId) {
        consumableMapper.createStockRecordTableIfNotExists();
        List<ConsumableStockRecordEntity> records = consumableMapper.listStockRecords(consumableId);
        return records == null ? List.of() : records;
    }

    @Override
    @Transactional
    public ConsumableMovementResult recordConsumableMovement(Long id, ConsumableStockRecordEntity record) {
        consumableMapper.createStockRecordTableIfNotExists();
        ConsumableEntity consumable = requireConsumable(id);
        ConsumableStockRecordEntity movement = record == null ? new ConsumableStockRecordEntity() : record;

        int quantity = movement.getQuantity() == null ? 0 : movement.getQuantity();
        if (quantity <= 0) {
            throw new IllegalArgumentException("Movement quantity must be greater than 0");
        }

        String type = normalizeMovementType(movement.getType());
        int beforeStock = consumable.getStock() == null ? 0 : Math.max(consumable.getStock(), 0);
        int afterStock = MOVEMENT_IN.equals(type) ? beforeStock + quantity : beforeStock - quantity;
        if (afterStock < 0) {
            throw new IllegalArgumentException("Outbound quantity cannot exceed current stock");
        }

        consumable.setStock(afterStock);
        applyStockStatus(consumable);
        int updated = consumableMapper.updateConsumableStock(id, afterStock, consumable.getStatus(), consumable.getTagType());
        if (updated == 0) {
            throw new IllegalArgumentException("Consumable not found");
        }

        movement.setConsumableId(consumable.getId());
        movement.setConsumableName(consumable.getName());
        movement.setCategory(consumable.getCategory());
        movement.setType(type);
        movement.setQuantity(quantity);
        movement.setUnit(textOrDefault(movement.getUnit(), consumable.getUnit()));
        movement.setBeforeStock(beforeStock);
        movement.setAfterStock(afterStock);
        movement.setOperator(textOrDefault(movement.getOperator(), "system"));
        movement.setSource(textOrDefault(movement.getSource(), "-"));
        movement.setReason(textOrDefault(movement.getReason(), "-"));
        movement.setRemark(textOrDefault(movement.getRemark(), ""));
        movement.setTime(LocalDateTime.now().format(DATE_TIME_FORMATTER));
        consumableMapper.insertStockRecord(movement);

        ConsumableEntity savedConsumable = consumableMapper.getConsumable(id);
        closeLoopAfterStockChanged(savedConsumable, MOVEMENT_IN.equals(type) ? "stock-in" : "stock-out");
        List<ConsumableStockRecordEntity> records = consumableMapper.listStockRecords(null);
        return new ConsumableMovementResult(savedConsumable, movement, records == null ? List.of() : records);
    }

    private ConsumableEntity normalizeConsumable(ConsumableEntity consumable) {
        ConsumableEntity normalizedConsumable = consumable == null ? new ConsumableEntity() : consumable;
        if (isBlank(normalizedConsumable.getName())) {
            normalizedConsumable.setName("新耗材");
        }
        if (isBlank(normalizedConsumable.getCategory())) {
            normalizedConsumable.setCategory("教学耗材");
        }
        if (normalizedConsumable.getStock() == null) {
            normalizedConsumable.setStock(0);
        }
        if (normalizedConsumable.getWarnThreshold() == null) {
            normalizedConsumable.setWarnThreshold(10);
        }
        if (isBlank(normalizedConsumable.getUnit())) {
            normalizedConsumable.setUnit("件");
        }
        applyStockStatus(normalizedConsumable);
        return normalizedConsumable;
    }

    private void applyStockStatus(ConsumableEntity consumable) {
        if (consumable.getStock() <= 0) {
            consumable.setStatus("待补货");
            consumable.setTagType("danger");
            return;
        }
        if (consumable.getStock() <= consumable.getWarnThreshold()) {
            consumable.setStatus("低库存");
            consumable.setTagType("warning");
            return;
        }
        consumable.setStatus("库存充足");
        consumable.setTagType("success");
    }

    private void closeLoopAfterStockChanged(ConsumableEntity consumable, String action) {
        if (consumable == null) {
            return;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("id", consumable.getId());
        details.put("stock", consumable.getStock());
        details.put("threshold", consumable.getWarnThreshold());
        businessLoopService.recordEvent("consumable", action, consumable.getName(), consumable.getStatus(), details);

        if ("danger".equals(consumable.getTagType()) || "warning".equals(consumable.getTagType())) {
            NoticeEntity notice = new NoticeEntity();
            notice.setTitle(consumable.getName() + "库存预警");
            notice.setType("库存预警");
            notice.setNoticeType("库存预警");
            notice.setTarget("labAdmin");
            notice.setTargetRole("labAdmin");
            notice.setContent("当前库存 " + consumable.getStock() + consumable.getUnit()
                    + "，低于预警阈值 " + consumable.getWarnThreshold() + consumable.getUnit() + "，请安排补货。");
            notice.setStatus("已发布");
            notice.setPublishStatus("已发布");
            notice.setPublishTime(LocalDateTime.now());
            noticeMapper.insertNotice(notice);
            businessLoopService.recordEvent("notice", "auto-publish", notice.getTitle(), "已发布", Map.of("source", "consumable"));
        }
    }

    private List<ConsumableEntity> getDefaultConsumables() {
        return List.of(
                buildConsumable("网线", "网络耗材", 86, "根", "A 区库房", 20),
                buildConsumable("鼠标", "外设", 12, "个", "B 区库房", 15),
                buildConsumable("固态硬盘", "维修备件", 3, "块", "维修柜", 5)
        );
    }

    private ConsumableEntity buildConsumable(String name, String category, Integer stock, String unit, String location, Integer warnThreshold) {
        ConsumableEntity consumable = new ConsumableEntity();
        consumable.setName(name);
        consumable.setCategory(category);
        consumable.setStock(stock);
        consumable.setUnit(unit);
        consumable.setLocation(location);
        consumable.setWarnThreshold(warnThreshold);
        return consumable;
    }

    private ConsumableEntity requireConsumable(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Consumable id is required");
        }
        ConsumableEntity consumable = consumableMapper.getConsumable(id);
        if (consumable == null) {
            throw new IllegalArgumentException("Consumable not found");
        }
        return consumable;
    }

    private String normalizeMovementType(String type) {
        String rawValue = type == null ? "" : type.trim();
        String value = rawValue.toLowerCase();
        if ("in".equals(value) || "stock-in".equals(value) || MOVEMENT_IN.equals(rawValue)) {
            return MOVEMENT_IN;
        }
        if ("out".equals(value) || "stock-out".equals(value) || MOVEMENT_OUT.equals(rawValue)) {
            return MOVEMENT_OUT;
        }
        throw new IllegalArgumentException("Movement type must be in or out");
    }

    private String textOrDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
