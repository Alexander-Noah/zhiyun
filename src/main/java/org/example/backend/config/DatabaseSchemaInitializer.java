package org.example.backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureUserAvatarColumn();
    }

    private void ensureUserAvatarColumn() {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = database()
                  and table_name = 'sys_user'
                  and column_name = 'avatar_url'
                """, Integer.class);

        if (count != null && count > 0) {
            return;
        }

        jdbcTemplate.execute("""
                alter table sys_user
                  add column avatar_url varchar(500) default null comment '头像OSS访问地址' after department
                """);
    }
}
