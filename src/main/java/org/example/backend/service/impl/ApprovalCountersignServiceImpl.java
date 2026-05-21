package org.example.backend.service.impl;

import org.example.backend.entity.ApprovalCountersignEntity;
import org.example.backend.entity.ReservationsEntity;
import org.example.backend.mapper.ApprovalCountersignMapper;
import org.example.backend.mapper.ReservationsMapper;
import org.example.backend.service.ApprovalCountersignService;
import org.example.backend.service.BusinessLoopService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ApprovalCountersignServiceImpl implements ApprovalCountersignService {
    private static final String STATUS_PENDING = "待加签";
    private static final String STATUS_APPROVED = "已同意";
    private static final String STATUS_RETURNED = "已退回";
    private static final String STATUS_CANCELLED = "已取消";
    private static final String BUSINESS_RESERVATION = "reservation";

    private final ApprovalCountersignMapper countersignMapper;
    private final ReservationsMapper reservationsMapper;
    private final BusinessLoopService businessLoopService;

    public ApprovalCountersignServiceImpl(
            ApprovalCountersignMapper countersignMapper,
            ReservationsMapper reservationsMapper,
            BusinessLoopService businessLoopService
    ) {
        this.countersignMapper = countersignMapper;
        this.reservationsMapper = reservationsMapper;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public List<ApprovalCountersignEntity> listCountersigns(
            String businessType,
            String businessId,
            String assigneeName,
            String status
    ) {
        return countersignMapper.listCountersigns(trimToNull(businessType), trimToNull(businessId), trimToNull(assigneeName), trimToNull(status));
    }

    @Override
    @Transactional
    public ApprovalCountersignEntity createCountersign(ApprovalCountersignEntity countersign) {
        normalizeCreatePayload(countersign);

        if (countersignMapper.countActiveByBusiness(countersign.getBusinessType(), countersign.getBusinessId()) > 0) {
            throw new IllegalArgumentException("当前业务已存在待处理加签，请先完成后再发起");
        }

        enrichBusinessInfo(countersign);
        countersignMapper.insertCountersign(countersign);
        syncBusinessWhenCreated(countersign);
        businessLoopService.recordEvent("approval-countersign", "create", countersign.getBusinessTitle(), STATUS_PENDING, Map.of(
                "businessType", countersign.getBusinessType(),
                "businessId", countersign.getBusinessId(),
                "assigneeName", countersign.getAssigneeName()
        ));
        return countersignMapper.getCountersign(countersign.getId());
    }

    @Override
    @Transactional
    public ApprovalCountersignEntity completeCountersign(Long id, ApprovalCountersignEntity payload) {
        ApprovalCountersignEntity current = getExistingCountersign(id);
        if (!STATUS_PENDING.equals(current.getStatus())) {
            throw new IllegalArgumentException("当前加签已处理，不能重复提交");
        }

        String result = normalizeResult(payload == null ? null : payload.getResult());
        String remark = trimToNull(payload == null ? null : payload.getResultRemark());
        if (remark == null) {
            remark = STATUS_APPROVED.equals(result) ? "加签同意，返回原审批流程" : "加签退回，业务审批驳回";
        }

        int updatedCount = countersignMapper.completeCountersign(id, result, result, remark);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("加签记录不存在");
        }

        ApprovalCountersignEntity updated = countersignMapper.getCountersign(id);
        syncBusinessWhenCompleted(updated);
        businessLoopService.recordEvent("approval-countersign", "complete", updated.getBusinessTitle(), result, Map.of(
                "businessType", updated.getBusinessType(),
                "businessId", updated.getBusinessId(),
                "result", result
        ));
        return updated;
    }

    @Override
    @Transactional
    public ApprovalCountersignEntity cancelCountersign(Long id, ApprovalCountersignEntity payload) {
        ApprovalCountersignEntity current = getExistingCountersign(id);
        if (!STATUS_PENDING.equals(current.getStatus())) {
            throw new IllegalArgumentException("当前加签已处理，不能撤销");
        }

        String remark = trimToNull(payload == null ? null : payload.getResultRemark());
        int updatedCount = countersignMapper.cancelCountersign(id, remark == null ? "加签已撤销" : remark);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("加签记录不存在");
        }

        ApprovalCountersignEntity updated = countersignMapper.getCountersign(id);
        syncBusinessReviewStatus(updated, "待审核", "加签已撤销", updated.getResultRemark());
        businessLoopService.recordEvent("approval-countersign", "cancel", updated.getBusinessTitle(), STATUS_CANCELLED, Map.of(
                "businessType", updated.getBusinessType(),
                "businessId", updated.getBusinessId()
        ));
        return updated;
    }

    private ApprovalCountersignEntity getExistingCountersign(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("加签记录ID不能为空");
        }
        ApprovalCountersignEntity countersign = countersignMapper.getCountersign(id);
        if (countersign == null) {
            throw new IllegalArgumentException("加签记录不存在");
        }
        return countersign;
    }

    private void normalizeCreatePayload(ApprovalCountersignEntity countersign) {
        if (countersign == null) {
            throw new IllegalArgumentException("请填写加签信息");
        }
        countersign.setBusinessType(trimToNull(countersign.getBusinessType()));
        countersign.setBusinessId(trimToNull(countersign.getBusinessId()));
        countersign.setBusinessTitle(trimToNull(countersign.getBusinessTitle()));
        countersign.setBusinessStatus(trimToNull(countersign.getBusinessStatus()));
        countersign.setAssignerId(trimToNull(countersign.getAssignerId()));
        countersign.setAssignerName(firstNonBlank(trimToNull(countersign.getAssignerName()), "张明"));
        countersign.setAssigneeId(trimToNull(countersign.getAssigneeId()));
        countersign.setAssigneeName(trimToNull(countersign.getAssigneeName()));
        countersign.setReason(trimToNull(countersign.getReason()));
        countersign.setStatus(STATUS_PENDING);

        if (countersign.getBusinessType() == null) {
            throw new IllegalArgumentException("业务类型不能为空");
        }
        if (countersign.getBusinessId() == null) {
            throw new IllegalArgumentException("业务记录ID不能为空");
        }
        validateMeaningfulText("加签处理人", countersign.getAssigneeName(), 2, 80);
        validateMeaningfulText("加签原因", countersign.getReason(), 4, 500);
    }

    private void enrichBusinessInfo(ApprovalCountersignEntity countersign) {
        if (!BUSINESS_RESERVATION.equals(countersign.getBusinessType())) {
            if (countersign.getBusinessTitle() == null) {
                countersign.setBusinessTitle(countersign.getBusinessType() + "#" + countersign.getBusinessId());
            }
            return;
        }

        Integer reservationId = parseInteger(countersign.getBusinessId());
        if (reservationId == null) {
            throw new IllegalArgumentException("预约加签记录ID不正确");
        }

        ReservationsEntity reservation = reservationsMapper.getReservation(reservationId);
        if (reservation == null) {
            throw new IllegalArgumentException("预约记录不存在");
        }
        countersign.setBusinessStatus(firstNonBlank(countersign.getBusinessStatus(), reservation.getStatus()));
        countersign.setBusinessTitle(firstNonBlank(
                countersign.getBusinessTitle(),
                reservation.getApplicant() + " · " + reservation.getLab() + " · " + reservation.getDate()
        ));
    }

    private void syncBusinessWhenCreated(ApprovalCountersignEntity countersign) {
        syncBusinessReviewStatus(
                countersign,
                "加签中",
                countersign.getAssignerName(),
                "加签给" + countersign.getAssigneeName() + "：" + countersign.getReason()
        );
    }

    private void syncBusinessWhenCompleted(ApprovalCountersignEntity countersign) {
        if (STATUS_APPROVED.equals(countersign.getResult())) {
            syncBusinessReviewStatus(countersign, "待审核", countersign.getAssigneeName(), countersign.getResultRemark());
            return;
        }
        if (STATUS_RETURNED.equals(countersign.getResult())) {
            syncBusinessReviewStatus(countersign, "已驳回", countersign.getAssigneeName(), countersign.getResultRemark());
        }
    }

    private void syncBusinessReviewStatus(
            ApprovalCountersignEntity countersign,
            String status,
            String reviewerName,
            String reviewRemark
    ) {
        if (!BUSINESS_RESERVATION.equals(countersign.getBusinessType())) {
            return;
        }

        Integer reservationId = parseInteger(countersign.getBusinessId());
        if (reservationId == null) {
            return;
        }
        reservationsMapper.updateReviewStatus(reservationId, status, reviewerName, reviewRemark);
    }

    private String normalizeResult(String result) {
        String value = trimToNull(result);
        if (value == null || "同意".equals(value) || STATUS_APPROVED.equals(value)) {
            return STATUS_APPROVED;
        }
        if ("退回".equals(value) || "驳回".equals(value) || STATUS_RETURNED.equals(value)) {
            return STATUS_RETURNED;
        }
        throw new IllegalArgumentException("加签结果只能是已同意或已退回");
    }

    private void validateMeaningfulText(String label, String value, int minLength, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (value.length() < minLength) {
            throw new IllegalArgumentException(label + "至少填写 " + minLength + " 个字符");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
        }
        if (value.matches("(?i).*(<\\s*script|javascript:|on\\w+\\s*=|<[^>]+>).*")) {
            throw new IllegalArgumentException(label + "不能包含脚本或 HTML 标签");
        }
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
