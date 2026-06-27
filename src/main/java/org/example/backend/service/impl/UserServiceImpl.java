package org.example.backend.service.impl;

import org.example.backend.entity.UserEntity;
import org.example.backend.mapper.UserMapper;
import org.example.backend.security.PasswordService;
import org.example.backend.service.BusinessLoopService;
import org.example.backend.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final BusinessLoopService businessLoopService;
    private final PasswordService passwordService;

    public UserServiceImpl(UserMapper userMapper, BusinessLoopService businessLoopService, PasswordService passwordService) {
        this.userMapper = userMapper;
        this.businessLoopService = businessLoopService;
        this.passwordService = passwordService;
    }

    @Override
    public UserEntity login(String username, String password) {
        UserEntity user = userMapper.login(username);
        if (user != null && passwordService.matches(password, user.getPassword()) && Integer.valueOf(1).equals(user.getStatus())) {
            if (passwordService.needsRehash(user.getPassword())) {
                String encodedPassword = passwordService.encode(password);
                userMapper.updatePassword(user.getId(), encodedPassword);
                user.setPassword(encodedPassword);
            }
            return user;
        }
        return null;
    }

    @Override
    public UserEntity profile(String username) {
        return userMapper.profile(username);
    }

    @Override
    public List<UserEntity> listUsers() {
        List<UserEntity> users = userMapper.listUsers();
        return users == null ? List.of() : users;
    }

    @Override
    @Transactional
    public UserEntity createUser(UserEntity user) {
        normalizeUser(user, true, true);
        user.setPassword(passwordService.encode(user.getPassword()));
        userMapper.insertUser(user);
        saveRole(user.getId(), user.getRoleCode());
        UserEntity savedUser = userMapper.getUser(user.getId());
        recordUserEvent("create", savedUser);
        return savedUser;
    }

    @Override
    @Transactional
    public UserEntity updateUser(Integer id, UserEntity user) {
        UserEntity existingUser = userMapper.getUser(id);
        if (existingUser == null) {
            throw new IllegalArgumentException("未知用户");
        }
        boolean hasNewPassword = user.getPassword() != null && !user.getPassword().isBlank();
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            user.setUsername(existingUser.getUsername());
        }
        if (!hasNewPassword) {
            user.setPassword(existingUser.getPassword());
        }
        if (user.getStatus() == null) {
            user.setStatus(existingUser.getStatus());
        }
        if (user.getPermissions() == null) {
            user.setPermissions(existingUser.getPermissions());
        }
        if (user.getAvatarUrl() == null) {
            user.setAvatarUrl(existingUser.getAvatarUrl());
        }
        normalizeUser(user, false, hasNewPassword);
        if (hasNewPassword && !passwordService.isEncoded(user.getPassword())) {
            user.setPassword(passwordService.encode(user.getPassword()));
        }
        int updatedCount = userMapper.updateUser(id, user);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("未知用户");
        }
        saveRole(id, user.getRoleCode());
        UserEntity savedUser = userMapper.getUser(id);
        recordUserEvent("update", savedUser);
        return savedUser;
    }

    @Override
    public UserEntity updateUserStatus(Integer id, Integer status) {
        int updatedCount = userMapper.updateUserStatus(id, status == null ? 1 : status);
        if (updatedCount == 0) {
            throw new IllegalArgumentException("未知用户");
        }
        UserEntity savedUser = userMapper.getUser(id);
        recordUserEvent(Integer.valueOf(1).equals(savedUser.getStatus()) ? "enable" : "disable", savedUser);
        return savedUser;
    }

    private void normalizeUser(UserEntity user, boolean creating, boolean hasNewPassword) {
        if (user == null) {
            throw new IllegalArgumentException("请填写账号信息");
        }
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            user.setUsername("user" + System.currentTimeMillis());
        }
        if (user.getRealName() == null || user.getRealName().isBlank()) {
            user.setRealName(user.getUsername());
        }
        if (creating && (user.getPassword() == null || user.getPassword().isBlank())) {
            throw new IllegalArgumentException("请填写初始密码");
        }
        if (hasNewPassword) {
            validatePassword(user.getPassword());
        }
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        if (user.getRoleCode() == null || user.getRoleCode().isBlank()) {
            user.setRoleCode(resolveRoleCode(user.getRoleName()));
        }
        if (user.getRoleCode() == null || user.getRoleCode().isBlank()) {
            user.setRoleCode("teacher");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("密码至少 8 位，并包含字母和数字");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("密码必须同时包含字母和数字");
        }
        if (password.matches("(?i)^(123456|password|admin123|qwerty\\d*)$")) {
            throw new IllegalArgumentException("密码过于简单，请更换更安全的密码");
        }
    }

    private void saveRole(Integer userId, String roleCode) {
        userMapper.deleteUserRoles(userId);
        userMapper.insertUserRole(userId, roleCode == null || roleCode.isBlank() ? "teacher" : roleCode);
    }

    private String resolveRoleCode(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "";
        }
        return switch (roleName) {
            case "系统管理员" -> "systemAdmin";
            case "实验室管理员" -> "labAdmin";
            case "教师" -> "teacher";
            case "维修人员" -> "maintenance";
            default -> roleName;
        };
    }

    private void recordUserEvent(String action, UserEntity user) {
        if (user == null) {
            return;
        }
        businessLoopService.recordEvent("user", action, user.getRealName(), Integer.valueOf(1).equals(user.getStatus()) ? "启用" : "停用", Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "role", user.getRoleName() == null ? user.getRoleCode() : user.getRoleName()
        ));
    }
}
