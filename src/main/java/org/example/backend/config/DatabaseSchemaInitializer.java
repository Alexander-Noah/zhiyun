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
        ensureDeviceAssetColumns();
        ensureNoticeColumns();
        ensureNoticeRecipientTable();
        ensureApprovalCountersignTable();
        ensureAiAssistantConversationTable();
        ensureAiAssistantUserConfigTable();
    }

    private void ensureDeviceAssetColumns() {
        addDeviceColumnIfMissing("lab_name", """
                alter table device
                  add column lab_name varchar(100) default null comment '所属实验室' after lab_id
                """);
        addDeviceColumnIfMissing("quantity", """
                alter table device
                  add column quantity int not null default 1 comment '数量' after owner_user_id
                """);
        addDeviceColumnIfMissing("unit", """
                alter table device
                  add column unit varchar(20) not null default '台' comment '单位' after quantity
                """);
        addDeviceColumnIfMissing("standard_requirement", """
                alter table device
                  add column standard_requirement text default null comment '执行标准和数量要求' after specs
                """);
        addDeviceColumnIfMissing("remark", """
                alter table device
                  add column remark text default null comment '备注' after standard_requirement
                """);
        addDeviceColumnIfMissing("source_type", """
                alter table device
                  add column source_type varchar(20) not null default '手动录入' comment '数据来源' after remark
                """);
        addDeviceColumnIfMissing("deleted", """
                alter table device
                  add column deleted tinyint(1) not null default 0 comment '逻辑删除：0未删除，1已删除' after updated_at
                """);
    }

    private void addDeviceColumnIfMissing(String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = database()
                  and table_name = 'device'
                  and column_name = ?
                """, Integer.class, columnName);

        if (count != null && count > 0) {
            return;
        }

        jdbcTemplate.execute(ddl);
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

    private void ensureNoticeColumns() {
        addNoticeColumnIfMissing("priority", """
                alter table notice
                  add column priority varchar(20) default '普通' comment '通知优先级' after target_role
                """);
        addNoticeColumnIfMissing("source_module", """
                alter table notice
                  add column source_module varchar(80) default null comment '业务来源模块' after priority
                """);
        addNoticeColumnIfMissing("source_id", """
                alter table notice
                  add column source_id varchar(80) default null comment '业务来源ID' after source_module
                """);
        addNoticeColumnIfMissing("business_type", """
                alter table notice
                  add column business_type varchar(50) default null comment '业务提醒类型' after source_id
                """);
        addNoticeColumnIfMissing("withdrawn_at", """
                alter table notice
                  add column withdrawn_at datetime default null comment '撤回时间' after publish_time
                """);
        addNoticeColumnIfMissing("archived_at", """
                alter table notice
                  add column archived_at datetime default null comment '归档时间' after withdrawn_at
                """);
        addNoticeColumnIfMissing("deleted", """
                alter table notice
                  add column deleted tinyint(1) not null default 0 comment '逻辑删除标记' after archived_at
                """);
        addNoticeColumnIfMissing("deleted_at", """
                alter table notice
                  add column deleted_at datetime default null comment '删除时间' after deleted
                """);
    }

    private void addNoticeColumnIfMissing(String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = database()
                  and table_name = 'notice'
                  and column_name = ?
                """, Integer.class, columnName);

        if (count != null && count > 0) {
            return;
        }

        jdbcTemplate.execute(ddl);
    }

    private void ensureNoticeRecipientTable() {
        jdbcTemplate.execute("""
                create table if not exists notice_recipient (
                  id bigint primary key auto_increment comment '主键',
                  notice_id bigint not null comment '通知ID',
                  user_id bigint not null comment '接收用户ID',
                  username varchar(80) default null comment '接收账号',
                  real_name varchar(80) default null comment '接收人姓名',
                  role_code varchar(50) default null comment '接收角色',
                  read_status varchar(20) not null default '未读' comment '阅读状态',
                  read_time datetime default null comment '阅读时间',
                  archived tinyint(1) not null default 0 comment '用户侧归档',
                  archived_at datetime default null comment '用户侧归档时间',
                  deleted tinyint(1) not null default 0 comment '用户侧删除',
                  deleted_at datetime default null comment '用户侧删除时间',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  unique key uk_notice_recipient_user (notice_id, user_id),
                  key idx_notice_recipient_user_status (user_id, read_status),
                  key idx_notice_recipient_notice (notice_id)
                ) comment '通知接收对象表'
                """);
    }

    private void ensureApprovalCountersignTable() {
        jdbcTemplate.execute("""
                create table if not exists approval_countersign (
                  id bigint primary key auto_increment comment '主键',
                  business_type varchar(60) not null comment '业务类型，如 reservation、repair、usage-record',
                  business_id varchar(80) not null comment '业务记录ID',
                  business_title varchar(200) default null comment '业务标题或摘要',
                  business_status varchar(40) default null comment '发起加签时业务状态',
                  assigner_id varchar(80) default null comment '发起人ID',
                  assigner_name varchar(80) default null comment '发起人姓名',
                  assignee_id varchar(80) default null comment '加签处理人ID',
                  assignee_name varchar(80) not null comment '加签处理人姓名',
                  reason varchar(500) not null comment '加签原因',
                  status varchar(30) not null default '待加签' comment '加签状态',
                  result varchar(30) default null comment '处理结果',
                  result_remark varchar(500) default null comment '处理意见',
                  handled_at datetime default null comment '处理时间',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  key idx_countersign_business (business_type, business_id),
                  key idx_countersign_assignee (assignee_name, status),
                  key idx_countersign_status (status)
                ) comment '通用审批加签表'
                """);
    }

    private void ensureAiAssistantConversationTable() {
        jdbcTemplate.execute("""
                create table if not exists ai_assistant_conversation (
                  id bigint primary key auto_increment comment '主键',
                  user_id int not null comment '用户ID',
                  conversation_id varchar(120) not null comment '前端会话ID',
                  title varchar(120) not null comment '会话标题',
                  preview varchar(300) default null comment '会话预览',
                  updated_at_millis bigint not null comment '会话更新时间毫秒时间戳',
                  messages_json mediumtext not null comment '消息列表JSON',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  unique key uk_ai_conversation_user_session (user_id, conversation_id),
                  key idx_ai_conversation_user_time (user_id, updated_at_millis)
                ) comment 'AI助手聊天记录表'
                """);
    }

    private void ensureAiAssistantUserConfigTable() {
        jdbcTemplate.execute("""
                create table if not exists ai_assistant_user_config (
                  id bigint primary key auto_increment comment 'primary key',
                  user_id int not null comment 'user id',
                  enabled tinyint(1) not null default 0 comment 'custom api enabled',
                  base_url varchar(500) not null comment 'openai compatible base url',
                  api_key varchar(1000) default null comment 'api key',
                  model varchar(120) not null comment 'text model',
                  vision_model varchar(120) default null comment 'vision model',
                  system_prompt varchar(1000) default null comment 'system prompt',
                  created_at datetime not null default current_timestamp comment 'created at',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment 'updated at',
                  unique key uk_ai_user_config_user (user_id)
                ) comment 'ai assistant user config'
                """);
    }
}
