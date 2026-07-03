package org.example.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.example.backend.result.Result;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result> handleIllegalArgumentException(IllegalArgumentException exception, HttpServletRequest request) {
        log.warn("bad request method={} uri={} requestId={} message={}",
                request.getMethod(),
                request.getRequestURI(),
                MDC.get("requestId"),
                exception.getMessage());
        return ResponseEntity.badRequest().body(Result.error(exception.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result> handleDataIntegrityViolationException(DataIntegrityViolationException exception, HttpServletRequest request) {
        String message = isDeleteConstraintRequest(request)
                ? "当前数据已被其他业务引用，请先解除关联后再删除"
                : "数据格式或约束不符合要求，请检查后重试";

        log.warn("data integrity violation method={} uri={} requestId={} message={}",
                request.getMethod(),
                request.getRequestURI(),
                MDC.get("requestId"),
                exception.getMostSpecificCause().getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Result.error(message));
    }

    private boolean isDeleteConstraintRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "DELETE".equalsIgnoreCase(request.getMethod())
                || (uri != null && uri.contains("batch/delete"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleException(Exception exception, HttpServletRequest request) {
        String requestId = MDC.get("requestId");
        log.error("request failed method={} uri={} requestId={}",
                request.getMethod(),
                request.getRequestURI(),
                requestId,
                exception);
        String message = requestId == null || requestId.isBlank()
                ? "系统处理失败，请联系管理员查看后端日志"
                : "系统处理失败，请联系管理员并提供请求编号：" + requestId;
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(message));
    }
}
