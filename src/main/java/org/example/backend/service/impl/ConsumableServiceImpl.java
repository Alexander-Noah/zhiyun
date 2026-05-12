package org.example.backend.service.impl;

import org.example.backend.entity.ConsumableEntity;
import org.example.backend.entity.NoticeEntity;
import org.example.backend.mapper.ConsumableMapper;
import org.example.backend.mapper.NoticeMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.ConsumableService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConsumableServiceImpl implements ConsumableService {
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
