package org.example.backend.service.impl;

import org.example.backend.entity.LabEntity;
import org.example.backend.entity.ReservationsEntity;
import org.example.backend.mapper.ClassTimetableMapper;
import org.example.backend.mapper.LabMapper;
import org.example.backend.mapper.ReservationsMapper;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.ReservationsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReservationsServiceImpl implements ReservationsService {
    private static final String CRAWLER_CONFIG_KEY = "lab-admin-class-timetable";
    private static final String DEFAULT_SEMESTER_START_DATE = "2026-03-02";
    private static final String STATUS_PENDING = "待审核";
    private static final String STATUS_CONFLICT = "冲突";
    private static final Set<String> ALLOWED_SCENES = Set.of("课程", "自主", "考试", "竞赛");

    private final ReservationsMapper reservationsMapper;
    private final LabMapper labMapper;
    private final ClassTimetableMapper classTimetableMapper;
    private final BusinessLoopService businessLoopService;

    public ReservationsServiceImpl(
            ReservationsMapper reservationsMapper,
            LabMapper labMapper,
            ClassTimetableMapper classTimetableMapper,
            BusinessLoopService businessLoopService
    ) {
        this.reservationsMapper = reservationsMapper;
        this.labMapper = labMapper;
        this.classTimetableMapper = classTimetableMapper;
        this.businessLoopService = businessLoopService;
    }

    @Override
    public Object getReservations() {
        return reservationsMapper.getReservations();
    }

    @Override
    @Transactional
    public void insertReservation(ReservationsEntity reservation) {
        normalizeReservation(null, reservation);
        reservationsMapper.insertReservation(reservation);
        businessLoopService.recordEvent("reservation", "create", reservation.getLab(), reservation.getStatus(), Map.of(
                "reservationId", reservation.getId(),
                "applicant", reservation.getApplicant()
        ));
    }

    @Override
    public Object getReservation(Integer id) {
        return reservationsMapper.getReservation(id);
    }

    @Override
    @Transactional
    public void updateReservation(Integer id, ReservationsEntity reservation) {
        ReservationsEntity previousReservation = reservationsMapper.getReservation(id);
        if (previousReservation == null) {
            throw new IllegalArgumentException("预约记录不存在");
        }

        normalizeReservation(id, reservation);
        int updatedCount = reservationsMapper.updateReservation(id, reservation);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("预约记录不存在");
        }

        ReservationsEntity updatedReservation = reservationsMapper.getReservation(id);
        refreshLabStatus(previousReservation.getLabId());
        refreshLabStatus(updatedReservation.getLabId());
        businessLoopService.recordEvent("reservation", "update", updatedReservation.getLab(), updatedReservation.getStatus(), Map.of(
                "reservationId", id
        ));
    }

    @Override
    @Transactional
    public void deleteReservation(Integer id) {
        ReservationsEntity reservation = reservationsMapper.getReservation(id);
        reservationsMapper.deleteReservation(id);
        if (reservation != null) {
            refreshLabStatus(reservation.getLabId());
        }
        businessLoopService.recordEvent("reservation", "delete", String.valueOf(id), "已删除", Map.of("reservationId", id));
    }

    @Override
    @Transactional
    public List<ReservationsEntity> replaceReservations(List<ReservationsEntity> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return reservationsMapper.getReservations();
        }

        reservationsMapper.deleteAllReservations();
        for (ReservationsEntity reservation : reservations) {
            normalizeReservation(null, reservation);
            reservationsMapper.insertReservation(reservation);
        }
        businessLoopService.recordEvent("reservation", "batch-save", "预约台账", "已同步", Map.of("count", reservations.size()));

        return reservationsMapper.getReservations();
    }

    @Override
    @Transactional
    public ReservationsEntity getApproved(Integer id, ReservationsEntity reservationPatch) {
        normalizeTextFields(reservationPatch);
        validateOptionalMeaningfulText("审核备注", reservationPatch.getReviewRemark(), 4, 120);
        ReservationsEntity currentReservation = reservationsMapper.getReservation(id);
        if (currentReservation == null) {
            throw new IllegalArgumentException("预约记录不存在");
        }
        List<String> availabilityIssues = collectAvailabilityIssues(id, currentReservation, true);
        if (!availabilityIssues.isEmpty()) {
            throw new IllegalArgumentException(String.join("；", availabilityIssues));
        }
        int updatedCount = reservationsMapper.getApproved(id, reservationPatch);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("预约记录不存在");
        }
        ReservationsEntity reservation = reservationsMapper.getReservation(id);
        businessLoopService.syncUsageRecordAfterReservationApproved(reservation);
        refreshLabStatus(reservation.getLabId());
        return reservation;
    }

    @Override
    @Transactional
    public ReservationsEntity getRejected(Integer id, ReservationsEntity reservationPatch) {
        normalizeTextFields(reservationPatch);
        validateMeaningfulText("驳回原因", reservationPatch.getReviewRemark(), 4, 120);
        int updatedCount = reservationsMapper.getRejected(id, reservationPatch);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("预约记录不存在");
        }
        ReservationsEntity reservation = reservationsMapper.getReservation(id);
        refreshLabStatus(reservation.getLabId());
        businessLoopService.recordEvent("reservation", "reject", reservation.getLab(), reservation.getStatus(), Map.of(
                "reservationId", id,
                "reason", firstNonBlank(reservation.getNote(), "驳回")
        ));
        return reservation;
    }

    @Override
    public Map<String, Object> getScanReservationProfile(Integer labId, String labCode) {
        LabEntity lab = resolveLab(labId == null ? null : labId.longValue(), labCode, null);
        if (lab == null) {
            throw new IllegalArgumentException("实验室信息不存在");
        }
        if (!isBlank(labCode) && !labCode.equalsIgnoreCase(firstNonBlank(lab.getLabCode(), ""))) {
            throw new IllegalArgumentException("二维码与实验室信息不匹配");
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", lab.getId());
        profile.put("code", lab.getLabCode());
        profile.put("name", lab.getLabName());
        profile.put("building", lab.getBuilding());
        profile.put("floor", lab.getFloor());
        profile.put("room", lab.getRoomNo());
        profile.put("type", lab.getLabType());
        profile.put("capacity", lab.getCapacity());
        profile.put("manager", lab.getManagerName());
        profile.put("status", lab.getOpenStatus());
        profile.put("displayName", formatLabDisplayName(lab));
        return profile;
    }

    @Override
    @Transactional
    public ReservationsEntity submitScanReservation(ReservationsEntity reservation) {
        if (reservation == null) {
            throw new IllegalArgumentException("请填写预约信息");
        }

        prepareScanReservation(reservation);
        List<String> availabilityIssues = collectAvailabilityIssues(null, reservation, true);
        if (!availabilityIssues.isEmpty()) {
            throw new IllegalArgumentException(String.join("；", availabilityIssues));
        }

        reservationsMapper.insertReservation(reservation);
        ReservationsEntity savedReservation = reservationsMapper.getReservation(reservation.getId().intValue());
        businessLoopService.recordEvent("reservation", "scan-create", savedReservation.getLab(), savedReservation.getStatus(), Map.of(
                "reservationId", savedReservation.getId(),
                "applicant", savedReservation.getApplicant()
        ));
        return savedReservation;
    }

    @Override
    public Map<String, Object> getScanReservationStatus(Integer id, String contact) {
        if (id == null) {
            throw new IllegalArgumentException("请填写预约编号");
        }
        if (isBlank(contact)) {
            throw new IllegalArgumentException("请填写提交时的联系方式");
        }

        ReservationsEntity reservation = reservationsMapper.getReservation(id);
        if (reservation == null || !normalizeToken(contact).equals(normalizeToken(reservation.getContact()))) {
            throw new IllegalArgumentException("预约编号或联系方式不匹配");
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("id", reservation.getId());
        status.put("status", reservation.getStatus());
        status.put("tagType", reservation.getTagType());
        status.put("applicant", reservation.getApplicant());
        status.put("department", reservation.getDepartment());
        status.put("lab", reservation.getLab());
        status.put("date", reservation.getDate());
        status.put("timeRange", reservation.getTimeRange());
        status.put("scene", reservation.getScene());
        status.put("attendees", reservation.getAttendees());
        status.put("reviewer", reservation.getReviewer());
        status.put("submittedAt", reservation.getSubmittedAt());
        status.put("reviewedAt", reservation.getReviewedAt());
        status.put("note", reservation.getNote());
        return status;
    }

    private void normalizeReservation(Integer id, ReservationsEntity reservation) {
        normalizeTextFields(reservation);
        validateApplicantFields(reservation, false);
        validateRequiredFields(reservation);
        resolveReservationLab(reservation);

        if (reservation.getStatus() == null || reservation.getStatus().isBlank()) {
            reservation.setStatus(STATUS_PENDING);
        }
        if (reservation.getReviewerName() == null || reservation.getReviewerName().isBlank()) {
            reservation.setReviewerName(STATUS_PENDING);
        }

        if ("已驳回".equals(reservation.getStatus()) || "已取消".equals(reservation.getStatus())) {
            reservation.setConflict(false);
            return;
        }

        List<String> availabilityIssues = collectAvailabilityIssues(id, reservation, false);
        reservation.setConflict(!availabilityIssues.isEmpty());
        if (Boolean.TRUE.equals(reservation.getConflict()) && STATUS_PENDING.equals(reservation.getStatus())) {
            reservation.setStatus(STATUS_CONFLICT);
            if (reservation.getReviewRemark() == null || reservation.getReviewRemark().isBlank()) {
                reservation.setReviewRemark(String.join("；", availabilityIssues));
            }
        }
    }

    private void prepareScanReservation(ReservationsEntity reservation) {
        normalizeTextFields(reservation);
        validateApplicantFields(reservation, true);
        validateRequiredFields(reservation);
        if (reservation.getReservationDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("预约日期不能早于今天");
        }
        resolveReservationLab(reservation);

        reservation.setStatus(STATUS_PENDING);
        reservation.setConflict(false);
        reservation.setReviewerName(STATUS_PENDING);
        if (isBlank(reservation.getReviewRemark())) {
            reservation.setReviewRemark("扫码提交，待管理员审核。");
        }
    }

    private List<String> collectAvailabilityIssues(Integer id, ReservationsEntity reservation, boolean includeCapacity) {
        validateRequiredFields(reservation);
        LabEntity lab = resolveReservationLab(reservation);
        List<String> issues = new ArrayList<>();

        if (reservationsMapper.countConflictingReservations(id, reservation) > 0) {
            issues.add("所选时段已有预约记录，请更换实验室、日期或时段");
        }

        Integer week = resolveAcademicWeek(reservation.getReservationDate());
        String weekday = resolveWeekday(reservation.getReservationDate());
        if (week != null && weekday != null && reservationsMapper.countTimetableConflicts(reservation, week, weekday) > 0) {
            issues.add("所选时段已有课表占用，请更换实验室、日期或时段");
        }

        if (includeCapacity) {
            int capacity = lab.getCapacity() == null ? 0 : lab.getCapacity();
            int attendeeCount = reservation.getAttendeeCount() == null ? 1 : reservation.getAttendeeCount();
            if (capacity > 0 && attendeeCount > capacity) {
                issues.add("预约人数不能超过实验室容量 " + capacity + " 人");
            }
        }

        return issues;
    }

    private void validateRequiredFields(ReservationsEntity reservation) {
        if (reservation == null) {
            throw new IllegalArgumentException("请填写预约信息");
        }
        normalizeTextFields(reservation);
        if (reservation.getReservationDate() == null) {
            throw new IllegalArgumentException("请选择预约日期");
        }
        if (!isValidTimeRange(reservation.getTimeRange())) {
            throw new IllegalArgumentException("请选择有效的预约时段");
        }
        if (isBlank(reservation.getScene())) {
            throw new IllegalArgumentException("请选择预约类型");
        }
        if (!ALLOWED_SCENES.contains(reservation.getScene())) {
            throw new IllegalArgumentException("预约类型仅支持课程、自主、考试或竞赛");
        }
        if (reservation.getAttendeeCount() == null || reservation.getAttendeeCount() < 1) {
            reservation.setAttendeeCount(1);
        }
        validateMeaningfulText("预约用途", reservation.getReason(), 4, 160);
        validateOptionalMeaningfulText("补充说明", reservation.getReviewRemark(), 4, 120);
    }

    private void validateApplicantFields(ReservationsEntity reservation, boolean strictContact) {
        validateMeaningfulText("申请人姓名", reservation.getApplicantName(), 2, 50);
        validateOptionalMeaningfulText("学院、班级或组织", reservation.getDepartment(), 2, 80);
        if (strictContact && isBlank(reservation.getDepartment())) {
            throw new IllegalArgumentException("请填写学院、班级或组织");
        }
        validateContact(strictContact ? "联系方式" : "联系人", reservation.getContact(), strictContact);
    }

    private void normalizeTextFields(ReservationsEntity reservation) {
        if (reservation == null) {
            return;
        }
        reservation.setApplicantName(trimToNull(reservation.getApplicantName()));
        reservation.setDepartment(trimToNull(reservation.getDepartment()));
        reservation.setContact(trimToNull(reservation.getContact()));
        reservation.setLabName(trimToNull(reservation.getLabName()));
        reservation.setLabCode(trimToNull(reservation.getLabCode()));
        reservation.setTimeRange(trimToNull(reservation.getTimeRange()));
        reservation.setScene(trimToNull(reservation.getScene()));
        reservation.setReason(trimToNull(reservation.getReason()));
        reservation.setReviewRemark(trimToNull(reservation.getReviewRemark()));
    }

    private void validateMeaningfulText(String label, String value, int minLength, int maxLength) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("请填写" + label);
        }
        if (hasUnsafeContent(value)) {
            throw new IllegalArgumentException(label + "不能包含脚本或 HTML 标签");
        }
        if (value.length() < minLength) {
            throw new IllegalArgumentException(label + "至少填写 " + minLength + " 个字符");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(label + "不能超过 " + maxLength + " 个字符");
        }
        if (!hasMeaningfulText(value)) {
            throw new IllegalArgumentException(label + "不能只填写数字或符号");
        }
    }

    private void validateOptionalMeaningfulText(String label, String value, int minLength, int maxLength) {
        if (isBlank(value)) {
            return;
        }
        validateMeaningfulText(label, value, minLength, maxLength);
    }

    private void validateContact(String label, String value, boolean strict) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("请填写" + label);
        }
        if (hasUnsafeContent(value)) {
            throw new IllegalArgumentException(label + "不能包含脚本或 HTML 标签");
        }
        if (value.length() > 80) {
            throw new IllegalArgumentException(label + "不能超过 80 个字符");
        }
        if (isMobile(value) || isEmail(value) || isLandline(value)) {
            return;
        }
        if (!strict && hasMeaningfulText(value) && value.length() >= 2) {
            return;
        }
        throw new IllegalArgumentException(strict
                ? label + "请填写正确的手机号、邮箱或座机"
                : label + "请填写姓名、手机号、邮箱或座机");
    }

    private boolean hasUnsafeContent(String value) {
        return value != null && value.matches("(?i).*(<\\s*script|javascript:|on\\w+\\s*=|<[^>]+>).*");
    }

    private boolean hasMeaningfulText(String value) {
        return value != null && value.matches(".*[\\p{IsHan}A-Za-z].*");
    }

    private boolean isMobile(String value) {
        String compactValue = value == null ? "" : value.replaceAll("\\s+", "");
        return compactValue.matches("^1[3-9]\\d{9}$");
    }

    private boolean isLandline(String value) {
        String compactValue = value == null ? "" : value.replaceAll("\\s+", "");
        return compactValue.matches("^0\\d{2,3}-?\\d{7,8}(-?\\d{1,6})?$");
    }

    private boolean isEmail(String value) {
        return value != null && value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private LabEntity resolveReservationLab(ReservationsEntity reservation) {
        LabEntity lab = resolveLab(reservation.getLabId(), reservation.getLabCode(), reservation.getLabName());
        if (lab == null) {
            throw new IllegalArgumentException("实验室信息不存在");
        }

        reservation.setLabId(lab.getId().longValue());
        reservation.setLabCode(lab.getLabCode());
        reservation.setLabName(lab.getLabName());
        return lab;
    }

    private void refreshLabStatus(Long labId) {
        if (labId == null) {
            return;
        }

        String nextStatus;
        if (reservationsMapper.countCurrentApprovedReservationsByLabId(labId) > 0) {
            nextStatus = "使用中";
        } else if (reservationsMapper.countUpcomingApprovedReservationsByLabId(labId) > 0) {
            nextStatus = "预约中";
        } else {
            LabEntity lab = labMapper.getLabById(labId.intValue());
            String currentStatus = lab == null ? "" : firstNonBlank(lab.getOpenStatus(), "");
            nextStatus = "维护中".equals(currentStatus) || "停用".equals(currentStatus) ? currentStatus : "开放";
        }

        labMapper.updateLabOpenStatus(labId.intValue(), nextStatus);
    }

    private LabEntity resolveLab(Long labId, String labCode, String labName) {
        LabEntity lab = null;
        if (labId != null) {
            lab = labMapper.getLabById(labId.intValue());
        }

        if (lab != null) {
            return lab;
        }

        String normalizedCode = normalizeToken(firstNonBlank(labCode, extractLeadingCode(labName)));
        String normalizedName = normalizeToken(labName);

        if (isBlank(normalizedCode) && isBlank(normalizedName)) {
            return null;
        }

        return labMapper.getLabs()
                .stream()
                .filter(item -> {
                    String itemCode = normalizeToken(item.getLabCode());
                    String itemName = normalizeToken(item.getLabName());
                    String itemDisplayName = normalizeToken(formatLabDisplayName(item));
                    return (!isBlank(normalizedCode) && normalizedCode.equals(itemCode))
                            || (!isBlank(normalizedName) && (normalizedName.equals(itemName) || normalizedName.equals(itemDisplayName)));
                })
                .findFirst()
                .orElse(null);
    }

    private Integer resolveAcademicWeek(LocalDate reservationDate) {
        if (reservationDate == null) {
            return null;
        }

        LocalDate semesterStartDate = LocalDate.parse(DEFAULT_SEMESTER_START_DATE);
        try {
            Map<String, Object> crawlerConfig = classTimetableMapper.getCrawlerConfig(CRAWLER_CONFIG_KEY);
            Object configuredStartDate = crawlerConfig == null ? null : crawlerConfig.get("semesterStartDate");
            if (configuredStartDate != null && !String.valueOf(configuredStartDate).isBlank()) {
                semesterStartDate = LocalDate.parse(String.valueOf(configuredStartDate));
            }
        } catch (RuntimeException ignored) {
            semesterStartDate = LocalDate.parse(DEFAULT_SEMESTER_START_DATE);
        }

        long diffDays = ChronoUnit.DAYS.between(semesterStartDate, reservationDate);
        return Math.max(1, (int) (diffDays / 7) + 1);
    }

    private String resolveWeekday(LocalDate reservationDate) {
        if (reservationDate == null) {
            return null;
        }

        return switch (reservationDate.getDayOfWeek().getValue()) {
            case 1 -> "星期一";
            case 2 -> "星期二";
            case 3 -> "星期三";
            case 4 -> "星期四";
            case 5 -> "星期五";
            case 6 -> "星期六";
            case 7 -> "星期日";
            default -> null;
        };
    }

    private boolean isValidTimeRange(String timeRange) {
        if (isBlank(timeRange) || !timeRange.matches("^\\d{1,2}:\\d{2}-\\d{1,2}:\\d{2}$")) {
            return false;
        }

        try {
            String[] parts = timeRange.split("-");
            return LocalTime.parse(parts[0]).isBefore(LocalTime.parse(parts[1]));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String formatLabDisplayName(LabEntity lab) {
        String code = firstNonBlank(lab.getLabCode(), "");
        String name = firstNonBlank(lab.getLabName(), "");
        if (isBlank(name)) {
            return code;
        }
        if (isBlank(code) || name.toLowerCase().startsWith(code.toLowerCase())) {
            return name;
        }
        return code + " " + name;
    }

    private String extractLeadingCode(String value) {
        if (isBlank(value)) {
            return "";
        }
        String text = value.trim();
        int spaceIndex = text.indexOf(' ');
        return spaceIndex > 0 ? text.substring(0, spaceIndex) : text;
    }

    private String normalizeToken(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "").toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
