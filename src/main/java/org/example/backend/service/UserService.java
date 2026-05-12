package org.example.backend.service;

import org.example.backend.entity.UserEntity;

import java.util.List;

public interface UserService {
    UserEntity login(String username, String password);

    UserEntity profile(String username);

    List<UserEntity> listUsers();

    UserEntity createUser(UserEntity user);

    UserEntity updateUser(Integer id, UserEntity user);

    UserEntity updateUserStatus(Integer id, Integer status);
}
