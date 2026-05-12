package org.example.backend.mapper;

import org.apache.ibatis.annotations.*;
import org.example.backend.entity.UserEntity;

import java.util.List;

@Mapper
public interface UserMapper {
    @Select("""
            select
                u.id,
                u.username,
                u.password,
                u.real_name as realName,
                u.phone,
                u.email,
                u.department,
                u.status,
                u.created_at as createdAt,
                u.updated_at as updatedAt,
                u.permissions,
                r.role_code as roleCode,
                r.role_name as roleName
            from sys_user u
            left join user_role ur on ur.user_id = u.id
            left join role r on r.id = ur.role_id
            where u.username=#{username}
            """)
    UserEntity login(String username);

    @Select("""
            select
                u.id,
                u.username,
                u.password,
                u.real_name as realName,
                u.phone,
                u.email,
                u.department,
                u.status,
                u.created_at as createdAt,
                u.updated_at as updatedAt,
                u.permissions,
                r.role_code as roleCode,
                r.role_name as roleName
            from sys_user u
            left join user_role ur on ur.user_id = u.id
            left join role r on r.id = ur.role_id
            where u.username=#{username}
            """)
    UserEntity profile(String username);

    @Select("""
            select
                u.id,
                u.username,
                u.password,
                u.real_name as realName,
                u.phone,
                u.email,
                u.department,
                u.status,
                u.created_at as createdAt,
                u.updated_at as updatedAt,
                u.permissions,
                r.role_code as roleCode,
                r.role_name as roleName
            from sys_user u
            left join user_role ur on ur.user_id = u.id
            left join role r on r.id = ur.role_id
            order by u.id
            """)
    List<UserEntity> listUsers();

    @Select("""
            select
                u.id,
                u.username,
                u.password,
                u.real_name as realName,
                u.phone,
                u.email,
                u.department,
                u.status,
                u.created_at as createdAt,
                u.updated_at as updatedAt,
                u.permissions,
                r.role_code as roleCode,
                r.role_name as roleName
            from sys_user u
            left join user_role ur on ur.user_id = u.id
            left join role r on r.id = ur.role_id
            where u.id = #{id}
            """)
    UserEntity getUser(@Param("id") Integer id);

    @Insert("""
            insert into sys_user(username, password, real_name, phone, email, department, status, permissions)
            values(#{username}, #{password}, #{realName}, #{phone}, #{email}, #{department}, coalesce(#{status}, 1), #{permissions})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(UserEntity user);

    @Update("""
            update sys_user
            set
                username = #{user.username},
                password = coalesce(nullif(#{user.password}, ''), password),
                real_name = #{user.realName},
                phone = #{user.phone},
                email = #{user.email},
                department = #{user.department},
                status = coalesce(#{user.status}, status),
                permissions = #{user.permissions},
                updated_at = now()
            where id = #{id}
            """)
    int updateUser(@Param("id") Integer id, @Param("user") UserEntity user);

    @Update("update sys_user set status = #{status}, updated_at = now() where id = #{id}")
    int updateUserStatus(@Param("id") Integer id, @Param("status") Integer status);

    @Update("update sys_user set password = #{password}, updated_at = now() where id = #{id}")
    int updatePassword(@Param("id") Integer id, @Param("password") String password);

    @Delete("delete from user_role where user_id = #{userId}")
    void deleteUserRoles(@Param("userId") Integer userId);

    @Insert("""
            insert into user_role(user_id, role_id)
            select #{userId}, id from role where role_code = #{roleCode} limit 1
            """)
    void insertUserRole(@Param("userId") Integer userId, @Param("roleCode") String roleCode);
}
