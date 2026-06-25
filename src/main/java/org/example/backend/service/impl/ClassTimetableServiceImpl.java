package org.example.backend.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.entity.AcademicCredentialView;
import org.example.backend.entity.ClassTimetableEntity;
import org.example.backend.mapper.ClassTimetableMapper;
import org.example.backend.service.ClassTimetableService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class ClassTimetableServiceImpl implements ClassTimetableService {
    private static final int DEFAULT_LIMIT = 500;
    private static final int MAX_LIMIT = 5000;
    private static final int DEFAULT_TASK_LIMIT = 50;
    private static final int MAX_TASK_LIMIT = 200;
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final String CRAWLER_CREDENTIAL_KEY = "lab-admin-class-timetable";
    private static final String CRAWLER_CONFIG_KEY = "lab-admin-class-timetable";
    private static final String DEFAULT_CRON = "0 0 */6 * * *";
    private static final String DEFAULT_SEMESTER_START_DATE = "2026-03-02";
    private static final String DEFAULT_SECRET = "zhiyun-lab-timetable-dev-secret-change-me";

    private final SecureRandom secureRandom = new SecureRandom();
    private final ClassTimetableMapper classTimetableMapper;
    private final Environment environment;
    private final SecretKeySpec secretKey;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final AtomicBoolean crawlerRunning = new AtomicBoolean(false);
    private final Object schedulerMonitor = new Object();
    private ScheduledFuture<?> scheduledCrawler;

    public ClassTimetableServiceImpl(
            ClassTimetableMapper classTimetableMapper,
            Environment environment,
            ThreadPoolTaskScheduler classTimetableTaskScheduler,
            @Value("${timetable.crawler.credential-secret:${ACADEMIC_CREDENTIAL_SECRET:}}") String secret
    ) {
        this.classTimetableMapper = classTimetableMapper;
        this.environment = environment;
        this.taskScheduler = classTimetableTaskScheduler;
        this.secretKey = buildSecretKey(isBlank(secret) ? DEFAULT_SECRET : secret);
    }

    @PostConstruct
    public void initializeCrawlerScheduler() {
        try {
            ensureCrawlerTables();
            ensureDefaultCrawlerConfig();
            rescheduleCrawler();
        } catch (RuntimeException exception) {
            log.warn("课表定时抓取初始化失败，保存配置后会再次尝试初始化", exception);
        }
    }

    @Override
    public List<ClassTimetableEntity> listTimetables(
            String semester,
            String teacher,
            String className,
            String classroom,
            String courseName,
            String keyword,
            Integer week,
            Integer limit
    ) {
        return classTimetableMapper.listTimetables(
                trimToNull(semester),
                trimToNull(teacher),
                trimToNull(className),
                trimToNull(classroom),
                trimToNull(courseName),
                trimToNull(keyword),
                week,
                normalizeLimit(limit)
        );
    }

    @Override
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Object> databaseSummary = classTimetableMapper.getSummary();
        if (databaseSummary != null) {
            summary.putAll(databaseSummary);
        }
        summary.put("semesters", classTimetableMapper.listSemesters());
        return summary;
    }

    @Override
    public List<String> listSemesters() {
        return classTimetableMapper.listSemesters();
    }

    @Override
    public Map<String, Object> getCrawlerConfig() {
        ensureCrawlerTables();
        ensureDefaultCrawlerConfig();
        Map<String, Object> config = normalizeCrawlerConfig(classTimetableMapper.getCrawlerConfig(CRAWLER_CONFIG_KEY));
        config.put("nextRunAt", computeNextRunAt(config));
        config.put("running", crawlerRunning.get());
        return config;
    }

    @Override
    public Map<String, Object> saveCrawlerConfig(Map<String, Object> payload) {
        ensureCrawlerTables();
        boolean scheduleEnabled = toBoolean(payload == null ? null : payload.get("scheduleEnabled"), true);
        String cron = normalizeCron(stringValue(payload == null ? null : payload.get("cron")));
        String semesterStartDate = normalizeSemesterStartDate(stringValue(payload == null ? null : payload.get("semesterStartDate")));

        classTimetableMapper.upsertCrawlerConfig(CRAWLER_CONFIG_KEY, scheduleEnabled, cron, semesterStartDate);
        rescheduleCrawler();
        return getCrawlerConfig();
    }

    @Override
    public List<Map<String, Object>> listCrawlerTasks(Integer limit) {
        ensureCrawlerTables();
        List<Map<String, Object>> tasks = classTimetableMapper.listCrawlerTasks(normalizeTaskLimit(limit));
        for (Map<String, Object> task : tasks) {
            if (isBlank(stringValue(task.get("output"))) && !isBlank(stringValue(task.get("logPath")))) {
                task.put("output", readLogTail(Path.of(stringValue(task.get("logPath")))));
            }
        }
        return tasks;
    }

    @Override
    public void clearCrawlerTasks() {
        ensureCrawlerTables();
        classTimetableMapper.clearCrawlerTasks();
    }

    @Override
    public Map<String, Object> triggerCrawler() {
        return runCrawlerTask("manual");
    }

    private Map<String, Object> runCrawlerTask(String triggerType) {
        if (!crawlerRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("课表抓取任务正在执行，请稍后再试");
        }
        try {
            return executeCrawlerTask(triggerType);
        } finally {
            crawlerRunning.set(false);
        }
    }

    private Map<String, Object> executeCrawlerTask(String triggerType) {
        StoredCrawlerCredential credential = readStoredCrawlerCredential();
        if (credential == null) {
            throw new IllegalArgumentException("请先在课表抓取页面保存教务系统账号和密码");
        }

        String python = resolvePythonExecutable();
        String scriptPathText = environment.getProperty("timetable.crawler.script-path", "Python/crawl_school_timetable.py");
        String importScriptPathText = environment.getProperty("timetable.crawler.import-script-path", "Python/parse_and_import_timetable.py");
        String workingDirText = environment.getProperty("timetable.crawler.working-dir", "Python");
        long timeoutSeconds = environment.getProperty("timetable.crawler.timeout-seconds", Long.class, 600L);

        Path scriptPath = resolveConfiguredScriptPath(scriptPathText);
        Path importScriptPath = resolveConfiguredScriptPath(importScriptPathText);
        Path workingDir = Path.of(workingDirText).toAbsolutePath().normalize();
        if (!Files.exists(scriptPath)) {
            throw new IllegalArgumentException("课表爬虫脚本不存在：" + scriptPath);
        }
        if (!Files.exists(importScriptPath)) {
            throw new IllegalArgumentException("课表导入脚本不存在：" + importScriptPath);
        }

        Path logPath = buildCrawlerLogPath();
        Map<String, Object> task = insertStartedCrawlerTask(triggerType, credential, logPath);
        Map<String, Object> crawlerConfig = getCrawlerConfig();

        Map<String, String> crawlerEnv = new HashMap<>();
        crawlerEnv.put("TIMETABLE_USERNAME", credential.username());
        crawlerEnv.put("TIMETABLE_PASSWORD", credential.password());
        crawlerEnv.put("TIMETABLE_HEADLESS", environment.getProperty("timetable.crawler.headless", "true"));
        crawlerEnv.put("TIMETABLE_SEMESTER_START_DATE", stringValue(crawlerConfig.get("semesterStartDate")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.get("id"));
        result.put("triggerType", triggerType);
        result.put("successAccount", maskUsername(credential.username()));
        result.put("scriptPath", scriptPath.toString());
        result.put("importScriptPath", importScriptPath.toString());
        result.put("workingDir", workingDir.toString());
        result.put("logPath", logPath.toString());
        result.put("startedAt", LocalDateTime.now().toString());

        try {
            int crawlExitCode = runPythonScript(python, scriptPath, workingDir, logPath, timeoutSeconds, false, crawlerEnv);
            result.put("crawlExitCode", crawlExitCode);

            if (crawlExitCode == 0) {
                int importExitCode = runPythonScript(python, importScriptPath, workingDir, logPath, timeoutSeconds, true, Map.of());
                result.put("importExitCode", importExitCode);
                if (importExitCode == 0) {
                    int javaImportCount = importStandardCsv(workingDir, logPath);
                    result.put("javaImportCount", javaImportCount);
                    result.put("status", "success");
                    result.put("exitCode", importExitCode);
                    result.put("message", "课表爬取并导入完成");
                } else {
                    result.put("status", "failed");
                    result.put("exitCode", importExitCode);
                    result.put("message", "课表导入失败，请查看日志");
                }
            } else if (crawlExitCode == -1) {
                result.put("status", "timeout");
                result.put("exitCode", crawlExitCode);
                result.put("message", "课表爬虫执行超时，已终止进程");
            } else {
                result.put("status", "failed");
                result.put("exitCode", crawlExitCode);
                result.put("message", "课表爬取失败，未执行导入，请查看日志");
            }

            result.put("output", readLogTail(logPath));
            result.put("summary", getSummary());
            result.put("finishedAt", LocalDateTime.now().toString());
            updateFinishedCrawlerTask(task, result, credential, logPath);
            return result;
        } catch (IOException exception) {
            updateFailedCrawlerTask(task, credential, logPath, "无法启动课表爬虫脚本：" + exception.getMessage());
            throw new IllegalStateException("无法启动课表爬虫脚本：" + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            updateFailedCrawlerTask(task, credential, logPath, "课表爬虫脚本执行被中断");
            throw new IllegalStateException("课表爬虫脚本执行被中断", exception);
        } catch (RuntimeException exception) {
            updateFailedCrawlerTask(task, credential, logPath, exception.getMessage());
            throw exception;
        }
    }

    private void runScheduledCrawlerSafely() {
        try {
            runCrawlerTask("scheduled");
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("正在执行")) {
                insertSimpleCrawlerTask("scheduled", "skipped", "已有课表抓取任务正在执行，本次定时触发已跳过");
                return;
            }
            insertSimpleCrawlerTask("scheduled", "failed", exception.getMessage());
            log.warn("课表定时抓取执行失败", exception);
        } catch (RuntimeException exception) {
            insertSimpleCrawlerTask("scheduled", "failed", exception.getMessage());
            log.warn("课表定时抓取执行失败", exception);
        }
    }

    private void rescheduleCrawler() {
        synchronized (schedulerMonitor) {
            if (scheduledCrawler != null) {
                scheduledCrawler.cancel(false);
                scheduledCrawler = null;
            }

            Map<String, Object> config = normalizeCrawlerConfig(classTimetableMapper.getCrawlerConfig(CRAWLER_CONFIG_KEY));
            if (!toBoolean(config.get("scheduleEnabled"), true)) {
                return;
            }

            String cron = normalizeCron(stringValue(config.get("cron")));
            scheduledCrawler = taskScheduler.schedule(this::runScheduledCrawlerSafely, new CronTrigger(cron));
        }
    }

    private Map<String, Object> insertStartedCrawlerTask(
            String triggerType,
            StoredCrawlerCredential credential,
            Path logPath
    ) {
        ensureCrawlerTables();
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", nextTaskId());
        task.put("triggerType", triggerType);
        task.put("status", "running");
        task.put("scope", "class");
        task.put("source", "class-all");
        task.put("successAccount", maskUsername(credential.username()));
        task.put("attempts", 0);
        task.put("updatedCount", 0);
        task.put("startedAt", LocalDateTime.now());
        task.put("finishedAt", null);
        task.put("error", "");
        task.put("output", "");
        task.put("logPath", logPath.toString());
        classTimetableMapper.insertCrawlerTask(task);
        return task;
    }

    private void updateFinishedCrawlerTask(
            Map<String, Object> task,
            Map<String, Object> result,
            StoredCrawlerCredential credential,
            Path logPath
    ) {
        try {
            String status = stringValue(result.get("status"));
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("id", task.get("id"));
            update.put("status", isBlank(status) ? "unknown" : status);
            update.put("successAccount", maskUsername(credential.username()));
            update.put("attempts", result.get("crawlExitCode") == null ? 0 : 1);
            update.put("updatedCount", extractUpdatedCount(result));
            update.put("finishedAt", LocalDateTime.now());
            update.put("error", "success".equals(status) ? "" : stringValue(result.get("message")));
            update.put("output", stringValue(result.get("output")));
            update.put("logPath", logPath.toString());
            classTimetableMapper.updateCrawlerTask(update);
        } catch (RuntimeException exception) {
            log.warn("更新课表抓取任务记录失败", exception);
        }
    }

    private void updateFailedCrawlerTask(
            Map<String, Object> task,
            StoredCrawlerCredential credential,
            Path logPath,
            String error
    ) {
        try {
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("id", task.get("id"));
            update.put("status", "failed");
            update.put("successAccount", credential == null ? "" : maskUsername(credential.username()));
            update.put("attempts", 1);
            update.put("updatedCount", 0);
            update.put("finishedAt", LocalDateTime.now());
            update.put("error", isBlank(error) ? "课表抓取失败" : error);
            update.put("output", readLogTail(logPath));
            update.put("logPath", logPath.toString());
            classTimetableMapper.updateCrawlerTask(update);
        } catch (RuntimeException exception) {
            log.warn("更新课表抓取失败记录失败", exception);
        }
    }

    private void insertSimpleCrawlerTask(String triggerType, String status, String error) {
        try {
            ensureCrawlerTables();
            StoredCrawlerCredential credential = null;
            try {
                credential = readStoredCrawlerCredential();
            } catch (RuntimeException ignored) {
            }
            Map<String, Object> task = new LinkedHashMap<>();
            task.put("id", nextTaskId());
            task.put("triggerType", triggerType);
            task.put("status", status);
            task.put("scope", "class");
            task.put("source", "class-all");
            task.put("successAccount", credential == null ? "" : maskUsername(credential.username()));
            task.put("attempts", 0);
            task.put("updatedCount", 0);
            task.put("startedAt", LocalDateTime.now());
            task.put("finishedAt", LocalDateTime.now());
            task.put("error", isBlank(error) ? status : error);
            task.put("output", "");
            task.put("logPath", "");
            classTimetableMapper.insertCrawlerTask(task);
        } catch (RuntimeException exception) {
            log.warn("写入课表定时抓取任务记录失败", exception);
        }
    }

    private int extractUpdatedCount(Map<String, Object> result) {
        Object javaImportCount = result.get("javaImportCount");
        if (javaImportCount instanceof Number number) {
            return number.intValue();
        }
        Object summary = result.get("summary");
        if (summary instanceof Map<?, ?> summaryMap) {
            Object total = summaryMap.get("total");
            if (total instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(stringValue(total));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private void ensureCrawlerTables() {
        classTimetableMapper.createCrawlerConfigTableIfNotExists();
        classTimetableMapper.createCrawlerTaskTableIfNotExists();
    }

    private void ensureDefaultCrawlerConfig() {
        if (classTimetableMapper.getCrawlerConfig(CRAWLER_CONFIG_KEY) == null) {
            classTimetableMapper.upsertCrawlerConfig(
                    CRAWLER_CONFIG_KEY,
                    true,
                    DEFAULT_CRON,
                    DEFAULT_SEMESTER_START_DATE
            );
        }
    }

    private Map<String, Object> normalizeCrawlerConfig(Map<String, Object> row) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("configKey", CRAWLER_CONFIG_KEY);
        config.put("scheduleEnabled", true);
        config.put("cron", DEFAULT_CRON);
        config.put("semesterStartDate", DEFAULT_SEMESTER_START_DATE);
        config.put("updatedAt", "");
        if (row != null) {
            config.put("scheduleEnabled", toBoolean(row.get("scheduleEnabled"), true));
            config.put("cron", isBlank(stringValue(row.get("cron"))) ? DEFAULT_CRON : stringValue(row.get("cron")));
            config.put(
                    "semesterStartDate",
                    isBlank(stringValue(row.get("semesterStartDate")))
                            ? DEFAULT_SEMESTER_START_DATE
                            : stringValue(row.get("semesterStartDate"))
            );
            config.put("updatedAt", stringValue(row.get("updatedAt")));
        }
        return config;
    }

    private String computeNextRunAt(Map<String, Object> config) {
        if (!toBoolean(config.get("scheduleEnabled"), true)) {
            return "";
        }
        try {
            LocalDateTime next = CronExpression.parse(stringValue(config.get("cron"))).next(LocalDateTime.now());
            return next == null ? "" : next.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private String normalizeCron(String value) {
        String cron = trimToNull(value);
        if (cron == null) {
            throw new IllegalArgumentException("请设置抓取频率");
        }
        try {
            CronExpression.parse(cron);
            return cron;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("抓取时间设置不合法：" + exception.getMessage(), exception);
        }
    }

    private String normalizeSemesterStartDate(String value) {
        String semesterStartDate = trimToNull(value);
        if (semesterStartDate == null) {
            throw new IllegalArgumentException("请选择学期起始时间");
        }
        try {
            LocalDate.parse(semesterStartDate);
            return semesterStartDate;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("学期起始时间格式应为 YYYY-MM-DD", exception);
        }
    }

    private boolean toBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = stringValue(value).trim();
        if (text.equalsIgnoreCase("true") || text.equals("1") || text.equals("是")) {
            return true;
        }
        if (text.equalsIgnoreCase("false") || text.equals("0") || text.equals("否")) {
            return false;
        }
        return defaultValue;
    }

    private int normalizeTaskLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_TASK_LIMIT;
        }
        return Math.min(limit, MAX_TASK_LIMIT);
    }

    private long nextTaskId() {
        return System.currentTimeMillis() * 1000 + secureRandom.nextInt(1000);
    }

    private int importStandardCsv(Path workingDir, Path logPath) {
        Path csvPath = workingDir.resolve("课表导出结果").resolve("班级课表标准数据.csv").normalize();
        if (!Files.exists(csvPath)) {
            appendLog(logPath, "后端 JDBC 导入失败：未找到标准 CSV：" + csvPath);
            throw new IllegalStateException("未找到标准 CSV：" + csvPath);
        }

        try {
            List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
            if (lines.size() <= 1) {
                appendLog(logPath, "后端 JDBC 导入失败：标准 CSV 没有课表数据");
                throw new IllegalStateException("标准 CSV 没有课表数据");
            }

            List<String> headers = parseCsvLine(stripBom(lines.get(0)));
            List<ClassTimetableEntity> entities = new ArrayList<>();
            Set<String> semesters = new LinkedHashSet<>();

            for (int index = 1; index < lines.size(); index++) {
                if (lines.get(index).isBlank()) {
                    continue;
                }
                Map<String, String> row = toCsvRow(headers, parseCsvLine(lines.get(index)));
                ClassTimetableEntity entity = toTimetableEntity(row);
                if (isBlank(entity.getSemester()) || isBlank(entity.getCourseName())) {
                    continue;
                }
                entities.add(entity);
                semesters.add(entity.getSemester());
            }

            classTimetableMapper.createClassTimetableTableIfNotExists();
            for (String semester : semesters) {
                classTimetableMapper.deleteBySemester(semester);
            }
            for (ClassTimetableEntity entity : entities) {
                classTimetableMapper.insertTimetable(entity);
            }

            appendLog(logPath, "后端 JDBC 导入完成，共导入 " + entities.size() + " 条课表数据");
            return entities.size();
        } catch (IOException exception) {
            appendLog(logPath, "后端 JDBC 导入失败：" + exception.getMessage());
            throw new IllegalStateException("读取标准 CSV 失败：" + exception.getMessage(), exception);
        }
    }

    private ClassTimetableEntity toTimetableEntity(Map<String, String> row) {
        ClassTimetableEntity entity = new ClassTimetableEntity();
        entity.setSemester(row.getOrDefault("学年学期", ""));
        entity.setRowClassName(row.getOrDefault("行班级", ""));
        entity.setClassName(row.getOrDefault("班级", ""));
        entity.setWeekday(row.getOrDefault("星期", ""));
        entity.setSectionCode(row.getOrDefault("节次代码", ""));
        entity.setSectionText(row.getOrDefault("节次", ""));
        entity.setStartSection(parseInteger(row.get("开始节次")));
        entity.setEndSection(parseInteger(row.get("结束节次")));
        entity.setCourseName(row.getOrDefault("课程名称", ""));
        entity.setTeacher(row.getOrDefault("教师", ""));
        entity.setWeekRaw(row.getOrDefault("周次原文", ""));
        entity.setWeekText(row.getOrDefault("周次", ""));
        entity.setWeekExpanded(row.getOrDefault("展开周次", ""));
        entity.setClassroom(row.getOrDefault("教室", ""));
        return entity;
    }

    private Map<String, String> toCsvRow(List<String> headers, List<String> values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            row.put(headers.get(index), index < values.size() ? values.get(index) : "");
        }
        return row;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private Integer parseInteger(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return (int) Double.parseDouble(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String stripBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private void appendLog(Path logPath, String message) {
        try {
            Files.writeString(
                    logPath,
                    System.lineSeparator() + message + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    Files.exists(logPath)
                            ? java.nio.file.StandardOpenOption.APPEND
                            : java.nio.file.StandardOpenOption.CREATE
            );
        } catch (IOException ignored) {
        }
    }

    @Override
    public AcademicCredentialView getCrawlerCredential() {
        ensureCredentialTable();
        Map<String, Object> row = classTimetableMapper.getCrawlerCredential(CRAWLER_CREDENTIAL_KEY);
        return toCredentialView(row);
    }

    @Override
    public AcademicCredentialView saveCrawlerCredential(Map<String, String> payload) {
        String username = trimToNull(payload == null ? null : payload.get("username"));
        String password = trimToNull(payload == null ? null : payload.get("password"));
        if (isBlank(username) || isBlank(password)) {
            throw new IllegalArgumentException("请填写教务系统账号和密码");
        }

        ensureCredentialTable();
        classTimetableMapper.upsertCrawlerCredential(
                CRAWLER_CREDENTIAL_KEY,
                encrypt(username),
                encrypt(password)
        );
        return getCrawlerCredential();
    }

    @Override
    public void deleteCrawlerCredential() {
        ensureCredentialTable();
        classTimetableMapper.deleteCrawlerCredential(CRAWLER_CREDENTIAL_KEY);
    }

    private StoredCrawlerCredential readStoredCrawlerCredential() {
        ensureCredentialTable();
        Map<String, Object> row = classTimetableMapper.getCrawlerCredential(CRAWLER_CREDENTIAL_KEY);
        if (row == null || row.isEmpty()) {
            return null;
        }
        String usernameCipher = stringValue(row.get("usernameCipher"));
        String passwordCipher = stringValue(row.get("passwordCipher"));
        if (isBlank(usernameCipher) || isBlank(passwordCipher)) {
            return null;
        }
        return new StoredCrawlerCredential(decrypt(usernameCipher), decrypt(passwordCipher));
    }

    private AcademicCredentialView toCredentialView(Map<String, Object> row) {
        AcademicCredentialView view = new AcademicCredentialView();
        view.setCredentialKey(CRAWLER_CREDENTIAL_KEY);
        view.setConfigured(row != null && !row.isEmpty());
        if (Boolean.TRUE.equals(view.getConfigured())) {
            view.setUsernameMasked(maskUsername(decrypt(stringValue(row.get("usernameCipher")))));
            view.setUpdatedAt(stringValue(row.get("updatedAt")));
        }
        return view;
    }

    private void ensureCredentialTable() {
        classTimetableMapper.createCredentialTableIfNotExists();
    }

    private int runPythonScript(
            String python,
            Path scriptPath,
            Path workingDir,
            Path logPath,
            long timeoutSeconds,
            boolean appendLog,
            Map<String, String> extraEnv
    ) throws IOException, InterruptedException {
        Files.writeString(
                logPath,
                "Python executable: " + python + System.lineSeparator()
                        + "Script path: " + scriptPath + System.lineSeparator()
                        + "Working dir: " + workingDir + System.lineSeparator(),
                StandardCharsets.UTF_8,
                appendLog ? StandardOpenOption.APPEND : StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        );
        ProcessBuilder processBuilder = new ProcessBuilder(python, scriptPath.toString());
        processBuilder.directory(workingDir.toFile());
        processBuilder.environment().put("PYTHONIOENCODING", "utf-8");
        processBuilder.environment().put("PYTHONUTF8", "1");
        processBuilder.environment().putAll(extraEnv);
        processBuilder.redirectErrorStream(true);
        if (appendLog) {
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
        } else {
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logPath.toFile()));
        }

        Process process = processBuilder.start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return -1;
        }
        return process.exitValue();
    }

    private String resolvePythonExecutable() {
        String configuredPython = environment.getProperty("timetable.crawler.python", "").trim();
        if (!configuredPython.isEmpty()) {
            return configuredPython;
        }

        List<Path> candidates = List.of(
                Path.of("Python/.venv/Scripts/python.exe"),
                Path.of("../Python/.venv/Scripts/python.exe"),
                Path.of("Python/.venv/bin/python"),
                Path.of("../Python/.venv/bin/python")
        );
        for (Path candidate : candidates) {
            Path pythonPath = candidate.toAbsolutePath().normalize();
            if (Files.exists(pythonPath)) {
                return pythonPath.toString();
            }
        }
        return "python";
    }

    private Path resolveConfiguredScriptPath(String scriptPathText) {
        Path rawPath = Path.of(scriptPathText);
        Path configuredPath = rawPath.toAbsolutePath().normalize();
        if (Files.exists(configuredPath) || rawPath.isAbsolute()) {
            return configuredPath;
        }

        Path fileName = rawPath.getFileName();
        if (fileName == null) {
            return configuredPath;
        }

        Path rootPythonPath = Path.of("Python").resolve(fileName).toAbsolutePath().normalize();
        if (Files.exists(rootPythonPath)) {
            return rootPythonPath;
        }

        Path backendSiblingPythonPath = Path.of("../Python").resolve(fileName).toAbsolutePath().normalize();
        if (Files.exists(backendSiblingPythonPath)) {
            return backendSiblingPythonPath;
        }

        return configuredPath;
    }

    private Path buildCrawlerLogPath() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Path logDir = Path.of("logs").toAbsolutePath().normalize();
        try {
            Files.createDirectories(logDir);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建课表爬虫日志目录", exception);
        }
        return logDir.resolve("class-timetable-crawler-" + timestamp + ".log");
    }

    private String readLogTail(Path logPath) {
        try {
            return tailText(Files.readString(logPath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            try {
                return tailText(Files.readString(logPath, Charset.forName("GBK")));
            } catch (IOException ignored) {
                return "";
            }
        }
    }

    private String tailText(String content) {
        if (content == null) {
            return "";
        }
        if (content.length() <= 3000) {
            return content;
        }
        return content.substring(content.length() - 3000);
    }

    private String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception exception) {
            throw new IllegalStateException("教务账号加密失败", exception);
        }
    }

    private String decrypt(String value) {
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("教务账号解密失败，请确认 timetable.crawler.credential-secret 未变更", exception);
        }
    }

    private SecretKeySpec buildSecretKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(key, "AES");
        } catch (Exception exception) {
            throw new IllegalStateException("初始化教务账号加密密钥失败", exception);
        }
    }

    private String maskUsername(String username) {
        if (isBlank(username)) {
            return "";
        }
        if (username.length() <= 4) {
            return username.charAt(0) + "***";
        }
        return username.substring(0, 2) + "****" + username.substring(username.length() - 2);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record StoredCrawlerCredential(String username, String password) {
    }
}
