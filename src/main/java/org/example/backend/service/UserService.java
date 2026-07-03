package org.example.backend.service;

import org.example.backend.entity.UserEntity;

import java.util.List;

public interface UserService {
    UserEntity login(String username, String password);

    UserEntity profile(String username);

    UserEntity updateProfile(String username, UserEntity profile);

    List<UserEntity> listUsers();

    UserEntity createUser(UserEntity user);

    UserEntity updateUser(Integer id, UserEntity user);

    UserEntity updateUserStatus(Integer id, Integer status);

    UserEntity resetUserPassword(Integer id);

    UserEntity deleteUser(Integer id);

    List<UserEntity> batchUpdateStatus(List<Integer> ids, Integer status);

    List<UserEntity> batchDeleteUsers(List<Integer> ids);

    List<UserEntity> batchUpdateRole(List<Integer> ids, String roleName, String roleCode);

    List<UserEntity> batchUpdateDepartment(List<Integer> ids, String department);
}
