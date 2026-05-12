package org.example.backend.service.impl;

import org.example.backend.entity.AcademicScheduleCourse;
import org.example.backend.entity.AcademicScheduleImportRequest;
import org.example.backend.entity.CourseEnvironmentEntity;
import org.example.backend.service.AcademicScheduleService;
import org.example.backend.service.AcademicCredentialService;
import org.example.backend.service.CourseEnvironmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AcademicScheduleServiceImpl implements AcademicScheduleService {
    private static final String DEFAULT_BASE_URL = "https://jw.hniu.cn";
    private static final String DEFAULT_SCHEDULE_PATH = "/jsxsd/xskb/xskb_list.do";
    private static final String LOGIN_PATH = "/jsxsd/xk/LoginToXk";
    private static final Pattern SESSION_DATA_PATTERN = Pattern.compile("\"data\"\\s*:\\s*\"([^\"]+)\"");
    private static final String DEFAULT_WEEK_LABEL = "5月 第10周";
    private static final String[] DAY_LABELS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final Pattern ROW_PATTERN = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>");
    private static final Pattern CELL_PATTERN = Pattern.compile("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>");
    private static final Pattern BR_PATTERN = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern TAG_PATTERN = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern PERIOD_PATTERN = Pattern.compile("(第\\s*\\d+\\s*节).*?(\\d{1,2}:\\d{2}\\s*[-~—]\\s*\\d{1,2}:\\d{2})");

    private final CourseEnvironmentService courseEnvironmentService;
    private final AcademicCredentialService academicCredentialService;
    private final HttpClient httpClient;

    public AcademicScheduleServiceImpl(
            CourseEnvironmentService courseEnvironmentService,
            AcademicCredentialService academicCredentialService
    ) {
        this.courseEnvironmentService = courseEnvironmentService;
        this.academicCredentialService = academicCredentialService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public List<AcademicScheduleCourse> parseSchedule(AcademicScheduleImportRequest request) {
        request = ensureRequest(request);
        String html = request == null ? "" : request.getHtml();
        if (isBlank(html)) {
            throw new IllegalArgumentException("请提供教务课表 HTML");
        }
        return parseScheduleHtml(html, request);
    }

    @Override
    public List<AcademicScheduleCourse> fetchSchedule(AcademicScheduleImportRequest request) {
        request = ensureRequest(request);
        if (isBlank(request.getCookie()) && !hasLoginCredential(request)) {
            if (Boolean.TRUE.equals(request.getUseSavedCredential())) {
                academicCredentialService.applySavedCredential(request);
            } else {
                throw new IllegalArgumentException("请提供已登录教务系统的 Cookie、账号密码或选择本地已保存账号");
            }
        }

        if (isBlank(request.getCookie()) && hasLoginCredential(request)) {
            request.setCookie(loginAndBuildCookie(request));
            if (Boolean.TRUE.equals(request.getSaveCredential())) {
                academicCredentialService.saveCredential(request);
            }
        }

        URI uri = resolveScheduleUri(request);
        HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Referer", DEFAULT_BASE_URL + "/jsxsd/framework/xsMain.htmlx")
                .header("Cookie", request.getCookie())
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("教务系统课表抓取失败，HTTP " + response.statusCode());
            }
            request.setHtml(response.body());
            return parseScheduleHtml(response.body(), request);
        } catch (IOException exception) {
            throw new IllegalStateException("无法连接教务系统，请确认服务器可访问 jw.hniu.cn", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("教务系统课表抓取被中断", exception);
        }
    }

    @Override
    @Transactional
    public List<CourseEnvironmentEntity> importSchedule(AcademicScheduleImportRequest request) {
        request = ensureRequest(request);
        List<AcademicScheduleCourse> courses = isBlank(request.getHtml())
                ? fetchSchedule(request)
                : parseSchedule(request);

        List<CourseEnvironmentEntity> savedRecords = new ArrayList<>();
        for (AcademicScheduleCourse course : courses) {
            CourseEnvironmentEntity environment = new CourseEnvironmentEntity();
            environment.setCourse(course.getCourseName());
            environment.setCourseName(course.getCourseName());
            environment.setTeacherName(firstNonBlank(course.getTeacherName(), request.getTeacherName(), "任课教师"));
            environment.setTeacher(firstNonBlank(course.getTeacherName(), request.getTeacherName(), "任课教师"));
            environment.setTeacherUserId(request.getTeacherUserId());
            environment.setClassName(firstNonBlank(course.getClassName(), request.getClassName(), "待同步班级"));
            environment.setUseTime(course.getUseTime());
            environment.setLabType(firstNonBlank(course.getLabName(), request.getLabType(), "待分配实验室"));
            environment.setAssignedLabName(course.getLabName());
            environment.setSoftware("按教务课表课程要求配置");
            environment.setSoftwareRequirements("按教务课表课程要求配置");
            environment.setSpecialRequirements("来源：湖南信息职院教务系统课表导入；原始信息：" + limit(course.getRawText(), 180));
            environment.setProcessStatus("待配置");
            environment.setStatus("待配置");
            environment.setConfirmStatus("待确认");
            Object saved = courseEnvironmentService.InserterCourseEnvironment(environment);
            if (saved instanceof CourseEnvironmentEntity savedEntity) {
                savedRecords.add(savedEntity);
            }
        }
        return savedRecords;
    }

    private AcademicScheduleImportRequest ensureRequest(AcademicScheduleImportRequest request) {
        return request == null ? new AcademicScheduleImportRequest() : request;
    }

    private List<AcademicScheduleCourse> parseScheduleHtml(String html, AcademicScheduleImportRequest request) {
        List<AcademicScheduleCourse> courses = new ArrayList<>();
        Matcher rowMatcher = ROW_PATTERN.matcher(html);
        while (rowMatcher.find()) {
            List<String> cells = extractCells(rowMatcher.group(1));
            if (cells.size() < 2) {
                continue;
            }

            PeriodInfo periodInfo = parsePeriod(cells.get(0));
            if (periodInfo == null) {
                continue;
            }

            for (int dayIndex = 0; dayIndex < DAY_LABELS.length && dayIndex + 1 < cells.size(); dayIndex++) {
                List<String> blocks = extractCourseBlocks(cells.get(dayIndex + 1));
                for (String block : blocks) {
                    AcademicScheduleCourse course = parseCourseBlock(block, periodInfo, DAY_LABELS[dayIndex], request);
                    if (course != null) {
                        courses.add(course);
                    }
                }
            }
        }
        return courses;
    }

    private List<String> extractCells(String rowHtml) {
        List<String> cells = new ArrayList<>();
        Matcher cellMatcher = CELL_PATTERN.matcher(rowHtml);
        while (cellMatcher.find()) {
            cells.add(cellMatcher.group(1));
        }
        return cells;
    }

    private PeriodInfo parsePeriod(String cellHtml) {
        String text = cleanText(cellHtml).replace(" ", "");
        Matcher matcher = PERIOD_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return new PeriodInfo(matcher.group(1).replace(" ", ""), matcher.group(2).replace("~", "-").replace("—", "-"));
    }

    private List<String> extractCourseBlocks(String cellHtml) {
        String normalizedHtml = cellHtml
                .replaceAll("(?is)<hr\\b[^>]*>", "\n---COURSE---\n")
                .replaceAll("(?is)</div>\\s*<div\\b", "\n---COURSE---\n<div");
        String text = BR_PATTERN.matcher(normalizedHtml).replaceAll("\n");
        text = TAG_PATTERN.matcher(text).replaceAll("\n");
        text = decodeHtml(text)
                .replaceAll("[-—_]{5,}", "\n---COURSE---\n")
                .replaceAll("\\r", "\n");

        List<String> blocks = new ArrayList<>();
        for (String block : text.split("---COURSE---")) {
            String cleanBlock = normalizeLines(block);
            if (!isBlank(cleanBlock) && !"&nbsp;".equals(cleanBlock)) {
                blocks.add(cleanBlock);
            }
        }
        return blocks;
    }

    private AcademicScheduleCourse parseCourseBlock(String block, PeriodInfo periodInfo, String dayLabel, AcademicScheduleImportRequest request) {
        List<String> lines = new ArrayList<>();
        for (String line : block.split("\\n")) {
            String normalizedLine = line.trim();
            if (!isBlank(normalizedLine)) {
                lines.add(normalizedLine);
            }
        }
        if (lines.isEmpty()) {
            return null;
        }

        String courseName = "";
        String teacherName = firstNonBlank(request.getTeacherName(), "");
        String className = firstNonBlank(request.getClassName(), "");
        String labName = "";

        for (String line : lines) {
            if (isBlank(courseName) && !looksLikeMetadata(line)) {
                courseName = trimLabel(line);
                continue;
            }
            if (containsAny(line, "教师", "老师", "讲师")) {
                teacherName = trimLabel(line);
            } else if (containsAny(line, "班", "级")) {
                className = trimLabel(line);
            } else if (looksLikeLab(line)) {
                labName = trimLabel(line);
            }
        }

        if (isBlank(courseName)) {
            courseName = trimLabel(lines.get(0));
        }
        if (isBlank(courseName) || courseName.length() < 2) {
            return null;
        }

        AcademicScheduleCourse course = new AcademicScheduleCourse();
        course.setCourseName(courseName);
        course.setTeacherName(teacherName);
        course.setClassName(className);
        course.setWeekLabel(firstNonBlank(request.getWeekLabel(), DEFAULT_WEEK_LABEL));
        course.setDayLabel(dayLabel);
        course.setSectionLabel(periodInfo.sectionLabel());
        course.setTimeRange(periodInfo.timeRange());
        course.setUseTime(String.join(" ", course.getWeekLabel(), dayLabel, periodInfo.sectionLabel(), periodInfo.timeRange()));
        course.setLabName(labName);
        course.setRawText(block);
        return course;
    }

    private URI resolveScheduleUri(AcademicScheduleImportRequest request) {
        String baseUrl = firstNonBlank(request.getBaseUrl(), DEFAULT_BASE_URL);
        URI baseUri = URI.create(baseUrl);
        String host = baseUri.getHost();
        if (host == null || !host.toLowerCase(Locale.ROOT).endsWith("hniu.cn")) {
            throw new IllegalArgumentException("仅允许抓取湖南信息职院教务系统域名");
        }

        String schedulePath = firstNonBlank(request.getSchedulePath(), DEFAULT_SCHEDULE_PATH);
        if (!schedulePath.startsWith("/")) {
            schedulePath = "/" + schedulePath;
        }
        return baseUri.resolve(schedulePath);
    }

    private String loginAndBuildCookie(AcademicScheduleImportRequest request) {
        if (isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            throw new IllegalArgumentException("请填写教务账号和密码");
        }

        String baseUrl = firstNonBlank(request.getBaseUrl(), DEFAULT_BASE_URL);
        URI baseUri = URI.create(baseUrl);
        String host = baseUri.getHost();
        if (host == null || !host.toLowerCase(Locale.ROOT).endsWith("hniu.cn")) {
            throw new IllegalArgumentException("仅允许登录湖南信息职院教务系统域名");
        }

        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient loginClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(cookieManager)
                .build();

        URI loginPageUri = baseUri.resolve("/jsxsd/");
        URI sessUri = baseUri.resolve(LOGIN_PATH + "?flag=sess");
        URI loginUri = baseUri.resolve(LOGIN_PATH);

        try {
            loginClient.send(buildGet(loginPageUri), HttpResponse.BodyHandlers.ofString());
            String encoded = buildEncodedCredential(loginClient, sessUri, request.getUsername(), request.getPassword());
            String formBody = buildLoginForm(request, encoded);
            HttpRequest loginRequest = HttpRequest.newBuilder(loginUri)
                    .timeout(Duration.ofSeconds(12))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Origin", baseUri.getScheme() + "://" + baseUri.getHost())
                    .header("Referer", loginPageUri.toString())
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();
            HttpResponse<String> loginResponse = loginClient.send(loginRequest, HttpResponse.BodyHandlers.ofString());
            if (loginResponse.statusCode() < 200 || loginResponse.statusCode() >= 400) {
                throw new IllegalStateException("教务系统登录失败，HTTP " + loginResponse.statusCode());
            }

            String body = loginResponse.body();
            if (body != null && (body.contains("验证码错误") || body.contains("密码错误") || body.contains("用户名") && body.contains("登录"))) {
                throw new IllegalArgumentException("教务系统登录失败，请检查账号、密码或验证码");
            }

            String cookie = cookieManager.getCookieStore().getCookies().stream()
                    .map(cookieItem -> cookieItem.getName() + "=" + cookieItem.getValue())
                    .reduce((left, right) -> left + "; " + right)
                    .orElse("");
            if (isBlank(cookie)) {
                throw new IllegalStateException("教务系统未返回有效登录 Cookie");
            }
            return cookie;
        } catch (IOException exception) {
            throw new IllegalStateException("无法连接教务系统登录服务", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("教务系统登录被中断", exception);
        }
    }

    private HttpRequest buildGet(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .GET()
                .build();
    }

    private String buildEncodedCredential(HttpClient loginClient, URI sessUri, String username, String password)
            throws IOException, InterruptedException {
        HttpRequest sessRequest = HttpRequest.newBuilder(sessUri)
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response = loginClient.send(sessRequest, HttpResponse.BodyHandlers.ofString());
        String responseBody = response.body();
        Matcher matcher = SESSION_DATA_PATTERN.matcher(responseBody == null ? "" : responseBody);
        if (!matcher.find()) {
            return "";
        }

        String[] parts = matcher.group(1).split("#");
        if (parts.length < 2) {
            return "";
        }

        String salt = parts[0];
        String indexText = parts[1];
        String code = username + "%%%" + password;
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i < 20 && i < indexText.length()) {
                int count = Character.digit(indexText.charAt(i), 10);
                if (count < 0) {
                    count = 0;
                }
                encoded.append(code.charAt(i));
                int end = Math.min(count, salt.length());
                encoded.append(salt, 0, end);
                salt = salt.substring(end);
            } else {
                encoded.append(code.substring(i));
                break;
            }
        }
        return encoded.toString();
    }

    private String buildLoginForm(AcademicScheduleImportRequest request, String encoded) {
        List<String> fields = new ArrayList<>();
        fields.add(formField("USERNAME", request.getUsername()));
        fields.add(formField("PASSWORD", request.getPassword()));
        if (!isBlank(encoded)) {
            fields.add(formField("encoded", encoded));
        }
        if (!isBlank(request.getRandomCode())) {
            fields.add(formField("RANDOMCODE", request.getRandomCode()));
        }
        return String.join("&", fields);
    }

    private String formField(String name, String value) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(firstNonBlank(value, ""), StandardCharsets.UTF_8);
    }

    private String cleanText(String html) {
        return normalizeLines(TAG_PATTERN.matcher(BR_PATTERN.matcher(html).replaceAll("\n")).replaceAll("\n"));
    }

    private String normalizeLines(String value) {
        return decodeHtml(value)
                .replace('\u00a0', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n")
                .trim();
    }

    private String decodeHtml(String value) {
        return value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"");
    }

    private boolean looksLikeMetadata(String line) {
        return containsAny(line, "第", "节", "周", "星期", "教师", "老师", "讲师", "教室", "实验室", "实训室", "校区")
                || line.matches(".*\\d{1,2}:\\d{2}.*");
    }

    private boolean looksLikeLab(String line) {
        return containsAny(line, "实验室", "实训室", "机房", "教室", "楼", "A", "B", "C", "D")
                && line.matches(".*([A-D]\\d{3}|实验室|实训室|机房|教室|楼).*");
    }

    private boolean containsAny(String value, String... keywords) {
        if (value == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String trimLabel(String value) {
        return value == null ? "" : value.replaceFirst("^(课程|教师|老师|地点|教室|班级)[:：]", "").trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean hasLoginCredential(AcademicScheduleImportRequest request) {
        return request != null && !isBlank(request.getUsername()) && !isBlank(request.getPassword());
    }

    private record PeriodInfo(String sectionLabel, String timeRange) {
    }
}
