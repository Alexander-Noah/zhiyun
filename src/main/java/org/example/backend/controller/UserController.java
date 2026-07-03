package org.example.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.backend.entity.UserEntity;
import org.example.backend.result.Result;
import org.example.backend.security.JwtAuthenticationFilter;
import org.example.backend.security.JwtService;
import org.example.backend.security.LoginAttemptLimiter;
import org.example.backend.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@CrossOrigin
@RestController
public class UserController {
    private final UserService userService;
    private final JwtService jwtService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public UserController(UserService userService, JwtService jwtService, LoginAttemptLimiter loginAttemptLimiter) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    @PostMapping("/auth/login")
    public Result login(@RequestBody(required = false) Map<String, String> params, HttpServletRequest request) {
        String username = params == null ? "" : params.get("username");
        String password = params == null ? "" : params.get("password");
        loginAttemptLimiter.assertAllowed(username, request);
        UserEntity user = userService.login(username, password);
        if (user == null) {
            loginAttemptLimiter.recordFailure(username, request);
            return Result.error("用户名或密码错误");
        }

        loginAttemptLimiter.recordSuccess(username, request);
        String token = jwtService.generateToken(user);
        return Result.success(Map.of(
                "token", token,
                "role", user.getRoleCode(),
                "user", sanitizeUser(user)
        ));
    }

    @PostMapping("/auth/logout")
    public Result logout() {
        return Result.success("退出登录成功");
    }

    @GetMapping("/auth/profile")
    public Result profile(
            @RequestParam(value = "username", required = false) String username,
            HttpServletRequest request
    ) {
        String authUsername = stringAttribute(request, JwtAuthenticationFilter.AUTH_USERNAME_ATTRIBUTE);
        String authRole = stringAttribute(request, JwtAuthenticationFilter.AUTH_ROLE_ATTRIBUTE);
        String targetUsername = username == null || username.isBlank() ? authUsername : username;

        if (targetUsername == null || targetUsername.isBlank()) {
            return Result.error(401, "登入状态无效");
        }

        if (authUsername != null && !authUsername.equals(targetUsername) && !"systemAdmin".equals(authRole)) {
            return Result.error(403, "无权查看其他账号资料");
        }

        UserEntity user = userService.profile(targetUsername);
        if (user != null) {
        return Result.success("获取个人资料成功", sanitizeUser(user));
        }
        return Result.error("用户不存在");
    }

    @PutMapping("/auth/profile")
    public Result updateProfile(@RequestBody(required = false) UserEntity profile, HttpServletRequest request) {
        String authUsername = stringAttribute(request, JwtAuthenticationFilter.AUTH_USERNAME_ATTRIBUTE);
        if (authUsername == null || authUsername.isBlank()) {
            return Result.error(401, "登录状态无效");
        }

        return Result.success("更新个人资料成功", sanitizeUser(userService.updateProfile(authUsername, profile)));
    }

    @GetMapping({"/users", "/admin/users"})
    public Result listUsers() {
        return Result.success("获取用户列表成功", sanitizeUsers(userService.listUsers()));
    }

    @GetMapping("/users/options")
    public Result listUserOptions() {
        return Result.success("获取用户选项成功", userService.listUsers().stream().map(this::toUserOption).toList());
    }

    @PostMapping({"/users", "/admin/users"})
    public Result createUser(@RequestBody UserEntity user) {
        return Result.success("新增用户成功", sanitizeUser(userService.createUser(user)));
    }

    @PutMapping({"/users/{id}", "/admin/users/{id}"})
    public Result updateUser(@PathVariable Integer id, @RequestBody UserEntity user) {
        return Result.success("更新用户成功", sanitizeUser(userService.updateUser(id, user)));
    }

    @PostMapping({"/users/{id}/status", "/admin/users/{id}/status"})
    public Result updateUserStatus(@PathVariable Integer id, @RequestBody(required = false) Map<String, Integer> payload) {
        Integer status = payload == null ? 1 : payload.get("status");
        return Result.success("更新用户状态成功", sanitizeUser(userService.updateUserStatus(id, status)));
    }

    @DeleteMapping({"/users/{id}", "/admin/users/{id}"})
    public Result deleteUser(@PathVariable Integer id) {
        return Result.success("删除用户成功", sanitizeUser(userService.deleteUser(id)));
    }

    @PostMapping({"/users/{id}/reset-password", "/admin/users/{id}/reset-password"})
    public Result resetUserPassword(@PathVariable Integer id) {
        return Result.success("重置密码成功", sanitizeUser(userService.resetUserPassword(id)));
    }

    @PostMapping({"/users/batch/enable", "/admin/users/batch/enable"})
    public Result batchEnableUsers(@RequestBody(required = false) Map<String, Object> payload) {
        return Result.success("批量启用成功", sanitizeUsers(userService.batchUpdateStatus(extractIds(payload), 1)));
    }

    @PostMapping({"/users/batch/disable", "/admin/users/batch/disable"})
    public Result batchDisableUsers(@RequestBody(required = false) Map<String, Object> payload) {
        return Result.success("批量停用成功", sanitizeUsers(userService.batchUpdateStatus(extractIds(payload), 0)));
    }

    @PostMapping({"/users/batch/delete", "/admin/users/batch/delete"})
    public Result batchDeleteUsers(@RequestBody(required = false) Map<String, Object> payload) {
        return Result.success("批量删除成功", sanitizeUsers(userService.batchDeleteUsers(extractIds(payload))));
    }

    @PostMapping({"/users/batch/role", "/admin/users/batch/role"})
    public Result batchUpdateRole(@RequestBody(required = false) Map<String, Object> payload) {
        String roleName = stringPayload(payload, "role");
        String roleCode = stringPayload(payload, "roleCode");
        return Result.success("批量分配角色成功", sanitizeUsers(userService.batchUpdateRole(extractIds(payload), roleName, roleCode)));
    }

    @PostMapping({"/users/batch/department", "/admin/users/batch/department"})
    public Result batchUpdateDepartment(@RequestBody(required = false) Map<String, Object> payload) {
        return Result.success("批量调整部门成功", sanitizeUsers(userService.batchUpdateDepartment(extractIds(payload), stringPayload(payload, "department"))));
    }

    private String stringAttribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : String.valueOf(value);
    }

    private String stringPayload(Map<String, Object> payload, String name) {
        Object value = payload == null ? null : payload.get(name);
        return value == null ? "" : String.valueOf(value);
    }

    private List<Integer> extractIds(Map<String, Object> payload) {
        Object rawIds = payload == null ? null : payload.get("ids");
        if (!(rawIds instanceof List<?> ids)) {
            return List.of();
        }

        return ids.stream()
                .map(this::toInteger)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Map<String, Object> toUserOption(UserEntity user) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("id", user.getId());
        option.put("username", user.getUsername());
        option.put("realName", user.getRealName());
        option.put("roleCode", user.getRoleCode());
        option.put("roleName", user.getRoleName());
        option.put("department", user.getDepartment());
        option.put("status", user.getStatus());
        return option;
    }

    private UserEntity sanitizeUser(UserEntity user) {
        if (user != null) {
            user.setPassword(null);
        }
        return user;
    }

    private List<UserEntity> sanitizeUsers(List<UserEntity> users) {
        if (users != null) {
            users.forEach(this::sanitizeUser);
        }
        return users;
    }
}
