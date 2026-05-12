package org.example.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.backend.entity.UserEntity;
import org.example.backend.result.Result;
import org.example.backend.security.JwtAuthenticationFilter;
import org.example.backend.security.JwtService;
import org.example.backend.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin
@RestController
public class UserController {
    private final UserService userService;
    private final JwtService jwtService;

    public UserController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @PostMapping("/auth/login")
    public Result login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");
        UserEntity user = userService.login(username, password);
        if (user == null) {
            return Result.error("\u7528\u6237\u540d\u6216\u5bc6\u7801\u9519\u8bef");
        }

        String token = jwtService.generateToken(user);
        return Result.success(Map.of(
                "token", token,
                "role", user.getRoleCode(),
                "user", sanitizeUser(user)
        ));
    }

    @PostMapping("/auth/logout")
    public Result logout() {
        return Result.success("logout success");
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
            return Result.error(401, "\u767b\u5f55\u72b6\u6001\u65e0\u6548");
        }

        if (authUsername != null && !authUsername.equals(targetUsername) && !"systemAdmin".equals(authRole)) {
            return Result.error(403, "\u65e0\u6743\u67e5\u770b\u5176\u4ed6\u8d26\u53f7\u8d44\u6599");
        }

        UserEntity user = userService.profile(targetUsername);
        if (user != null) {
            return Result.success("get profile success", sanitizeUser(user));
        }
        return Result.error("\u7528\u6237\u4e0d\u5b58\u5728");
    }

    @GetMapping({"/users", "/admin/users"})
    public Result listUsers() {
        return Result.success("list users success", sanitizeUsers(userService.listUsers()));
    }

    @PostMapping({"/users", "/admin/users"})
    public Result createUser(@RequestBody UserEntity user) {
        return Result.success("create user success", sanitizeUser(userService.createUser(user)));
    }

    @PutMapping({"/users/{id}", "/admin/users/{id}"})
    public Result updateUser(@PathVariable Integer id, @RequestBody UserEntity user) {
        return Result.success("update user success", sanitizeUser(userService.updateUser(id, user)));
    }

    @PostMapping({"/users/{id}/status", "/admin/users/{id}/status"})
    public Result updateUserStatus(@PathVariable Integer id, @RequestBody(required = false) Map<String, Integer> payload) {
        Integer status = payload == null ? 1 : payload.get("status");
        return Result.success("update user status success", sanitizeUser(userService.updateUserStatus(id, status)));
    }

    private String stringAttribute(HttpServletRequest request, String name) {
        Object value = request.getAttribute(name);
        return value == null ? null : String.valueOf(value);
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
