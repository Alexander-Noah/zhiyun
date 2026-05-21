package org.example.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginAttemptLimiter {
    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final long lockSeconds;

    public LoginAttemptLimiter(
            @Value("${smart-lab.security.login-rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${smart-lab.security.login-rate-limit.lock-seconds:900}") long lockSeconds
    ) {
        this.maxAttempts = Math.max(maxAttempts, 3);
        this.lockSeconds = Math.max(lockSeconds, 60);
    }

    public void assertAllowed(String username, HttpServletRequest request) {
        AttemptState state = attempts.get(key(username, request));
        if (state == null || state.lockedUntilEpochSecond <= Instant.now().getEpochSecond()) {
            return;
        }
        long waitSeconds = Math.max(1, state.lockedUntilEpochSecond - Instant.now().getEpochSecond());
        throw new IllegalArgumentException("登录失败次数过多，请 " + waitSeconds + " 秒后再试");
    }

    public void recordSuccess(String username, HttpServletRequest request) {
        attempts.remove(key(username, request));
    }

    public void recordFailure(String username, HttpServletRequest request) {
        String key = key(username, request);
        attempts.compute(key, (ignored, state) -> {
            long now = Instant.now().getEpochSecond();
            AttemptState next = state == null || state.lockedUntilEpochSecond <= now ? new AttemptState() : state;
            next.failureCount++;
            if (next.failureCount >= maxAttempts) {
                next.lockedUntilEpochSecond = now + lockSeconds;
            }
            return next;
        });
    }

    private String key(String username, HttpServletRequest request) {
        String normalizedUsername = username == null || username.isBlank()
                ? "unknown"
                : username.trim().toLowerCase(Locale.ROOT);
        return normalizedUsername + "|" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static class AttemptState {
        private int failureCount;
        private long lockedUntilEpochSecond;
    }
}
