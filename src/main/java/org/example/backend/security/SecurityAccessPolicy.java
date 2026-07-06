package org.example.backend.security;

import java.util.Locale;
import java.util.Set;

public class SecurityAccessPolicy {
    private static final Set<String> COMMON_PREFIXES = Set.of(
            "/ai-assistant",
            "/auth/profile",
            "/auth/logout",
            "/dashboard",
            "/files",
            "/teacher-hosts",
            "/student-clients"
    );

    public boolean isAllowed(String method, String path, String roleCode) {
        String normalizedPath = normalizePath(path);
        String normalizedMethod = normalizeMethod(method);
        String normalizedRole = normalizeRole(roleCode);

        if (isCommonAuthenticatedPath(normalizedPath)) {
            return true;
        }
        if ("systemAdmin".equals(normalizedRole)) {
            return true;
        }
        if ("labAdmin".equals(normalizedRole)) {
            return isUserOptionsPath(normalizedMethod, normalizedPath)
                    || isLabActivationCodePath(normalizedMethod, normalizedPath)
                    || isLabAdminPath(normalizedPath);
        }
        if ("teacher".equals(normalizedRole)) {
            return isTeacherPath(normalizedMethod, normalizedPath);
        }
        if ("maintenance".equals(normalizedRole)) {
            return isMaintenancePath(normalizedMethod, normalizedPath);
        }
        if ("academic".equals(normalizedRole)) {
            return isAcademicPath(normalizedPath);
        }
        return false;
    }

    private boolean isCommonAuthenticatedPath(String path) {
        if (isNoticeUserPath(path)) {
            return true;
        }
        return COMMON_PREFIXES.stream().anyMatch(prefix -> startsWithSegment(path, prefix));
    }

    private boolean isLabAdminPath(String path) {
        return startsWithAnySegment(path,
                "/academic-schedules",
                "/approval-countersigns",
                "/business-loop",
                "/class-timetables",
                "/consumables",
                "/course-environment-requests",
                "/device-status",
                "/device-inventory-records",
                "/device-transfer-records",
                "/devices",
                "/environment-templates",
                "/iot",
                "/lab-software",
                "/labs",
                "/notices",
                "/repairs",
                "/reservations",
                "/schedule-adjustments",
                "/usage-records"
        );
    }

    private boolean isUserOptionsPath(String method, String path) {
        return "GET".equals(method) && path.equals("/users/options");
    }

    private boolean isLabActivationCodePath(String method, String path) {
        if ("GET".equals(method) && path.equals("/activation-codes/lab-bindings")) {
            return true;
        }
        if ("POST".equals(method) && path.equals("/activation-codes/bind-lab")) {
            return true;
        }
        return "POST".equals(method) && path.matches("^/activation-codes/[^/]+/unbind-lab$");
    }

    private boolean isTeacherPath(String method, String path) {
        if (startsWithSegment(path, "/course-environment-requests")) {
            return isListOrCreateOrRecordUpdate(method, path, "/course-environment-requests");
        }
        if (startsWithSegment(path, "/schedule-adjustments")) {
            return isListOrCreateOrRecordUpdate(method, path, "/schedule-adjustments");
        }
        if (startsWithSegment(path, "/reservations")) {
            return "GET".equals(method) || ("POST".equals(method) && path.equals("/reservations"));
        }
        return "GET".equals(method) && startsWithAnySegment(path, "/class-timetables", "/notices");
    }

    private boolean isMaintenancePath(String method, String path) {
        if (startsWithSegment(path, "/repairs")) {
            return Set.of("GET", "POST", "PUT").contains(method);
        }
        return "GET".equals(method) && startsWithAnySegment(path, "/devices", "/device-inventory-records", "/notices");
    }

    private boolean isAcademicPath(String path) {
        return startsWithAnySegment(path, "/academic-schedules", "/class-timetables", "/notices");
    }

    private boolean isNoticeUserPath(String path) {
        return startsWithSegment(path, "/notices/user")
                || startsWithSegment(path, "/notices/stats")
                || path.matches("^/notices/[^/]+/read$");
    }

    private boolean startsWithAnySegment(String path, String... prefixes) {
        for (String prefix : prefixes) {
            if (startsWithSegment(path, prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean startsWithSegment(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private boolean isListOrCreateOrRecordUpdate(String method, String path, String prefix) {
        if ("GET".equals(method)) {
            return true;
        }
        if ("POST".equals(method)) {
            return path.equals(prefix);
        }
        return "PUT".equals(method) && path.matches("^" + prefix + "/[^/]+$");
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.trim().replaceAll("/{2,}", "/");
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String normalizeMethod(String method) {
        return method == null ? "GET" : method.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeRole(String roleCode) {
        return roleCode == null ? "" : roleCode.trim();
    }
}
