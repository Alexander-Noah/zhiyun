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
        ensureNoticeColumns();
        ensureNoticeRecipientTable();
        ensureApprovalCountersignTable();
        ensureAiAssistantConversationTable();
        ensureHostStaticAssetTable();
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

    private void ensureHostStaticAssetTable() {
        jdbcTemplate.execute("""
                create table if not exists host_static_asset (
                  id bigint primary key auto_increment comment '主键',
                  hostname varchar(120) not null comment '主机名',
                  platform varchar(300) default null comment '系统平台',
                  system_name varchar(80) default null comment '系统名称',
                  release_version varchar(80) default null comment '系统版本',
                  architecture varchar(80) default null comment 'CPU架构',
                  python_version varchar(80) default null comment 'Python版本',
                  environment_json longtext default null comment '环境变量JSON',
                  running_apps_json longtext default null comment '运行程序JSON',
                  installed_software_json longtext default null comment '已安装软件JSON',
                  running_app_count int not null default 0 comment '运行程序数量',
                  installed_software_count int not null default 0 comment '已安装软件数量',
                  reported_at varchar(80) default null comment '探针上报时间',
                  received_at datetime not null default current_timestamp comment '后端接收时间',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  unique key uk_host_static_asset_hostname (hostname),
                  key idx_host_static_asset_updated_at (updated_at)
                ) comment '主机静态资产快照'
                """);
    }
}
