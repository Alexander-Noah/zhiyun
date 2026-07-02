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
        ensureDeviceRuntimeAndAccessTables();
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

    private void ensureDeviceRuntimeAndAccessTables() {
        jdbcTemplate.execute("""
                create table if not exists lab_activation_code (
                  id bigint primary key auto_increment,
                  lab_id bigint not null comment 'lab id',
                  lab_code varchar(50) default null comment 'lab code snapshot',
                  lab_name varchar(100) default null comment 'lab name snapshot',
                  code_hash varchar(128) not null comment 'access code hash',
                  masked_code varchar(80) not null comment 'masked access code',
                  status varchar(32) not null default 'active' comment 'active, inactive, expired, revoked',
                  enabled tinyint(1) not null default 1 comment 'whether code can be used',
                  terminal_quota int not null default 1 comment 'terminal access quota',
                  bound_terminal_count int not null default 0 comment 'bound terminal count snapshot',
                  expire_at datetime default null comment 'expiration time',
                  source_type varchar(32) not null default 'manual' comment 'manual, import, system',
                  remark varchar(500) default null comment 'remark',
                  created_by bigint default null comment 'creator user id',
                  created_by_name varchar(80) default null comment 'creator name snapshot',
                  created_at datetime not null default current_timestamp,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  deleted tinyint(1) not null default 0,
                  unique key uk_lab_activation_code_hash (code_hash),
                  key idx_lab_activation_code_lab_status (lab_id, status, enabled, deleted),
                  key idx_lab_activation_code_expire_at (expire_at),
                  constraint fk_lab_activation_code_lab foreign key (lab_id) references lab(id)
                ) engine=InnoDB default charset=utf8mb4 comment='lab access authorization code'
                """);

        jdbcTemplate.execute("""
                create table if not exists lab_terminal (
                  id bigint primary key auto_increment,
                  terminal_id varchar(80) not null comment 'stable terminal id returned to client',
                  terminal_token_hash varchar(128) not null comment 'terminal token hash',
                  lab_id bigint not null comment 'lab id resolved by access code',
                  lab_code varchar(50) default null comment 'lab code snapshot',
                  lab_name varchar(100) default null comment 'lab name snapshot',
                  activation_code_id bigint default null comment 'lab access code id',
                  terminal_name varchar(120) default null comment 'terminal display name',
                  terminal_type varchar(40) not null default 'lab_client' comment 'teacher_client, lab_client, probe_client',
                  host_name varchar(120) default null comment 'host name',
                  machine_code varchar(160) default null comment 'machine fingerprint',
                  mac_address varchar(64) default null comment 'mac address',
                  ip_address varchar(64) default null comment 'ip address',
                  client_version varchar(80) default null comment 'client version',
                  status varchar(32) not null default 'active' comment 'active, inactive, unbound, blocked',
                  bound_at datetime not null default current_timestamp comment 'bind time',
                  first_connected_at datetime default null comment 'first client access time',
                  last_seen_at datetime default null comment 'last heartbeat time',
                  unbound_at datetime default null comment 'unbind time',
                  unbound_by varchar(80) default null comment 'unbind operator',
                  unbind_reason varchar(300) default null comment 'unbind reason',
                  source_type varchar(32) not null default 'activation_code' comment 'activation_code, manual, mock',
                  remark varchar(500) default null comment 'remark',
                  created_at datetime not null default current_timestamp,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  deleted tinyint(1) not null default 0,
                  unique key uk_lab_terminal_terminal_id (terminal_id),
                  unique key uk_lab_terminal_token_hash (terminal_token_hash),
                  key idx_lab_terminal_lab_status (lab_id, status, deleted),
                  key idx_lab_terminal_activation_code (activation_code_id),
                  key idx_lab_terminal_machine (machine_code, status),
                  key idx_lab_terminal_mac (mac_address, status),
                  constraint fk_lab_terminal_lab foreign key (lab_id) references lab(id),
                  constraint fk_lab_terminal_activation_code foreign key (activation_code_id) references lab_activation_code(id)
                ) engine=InnoDB default charset=utf8mb4 comment='lab terminal binding'
                """);

        jdbcTemplate.execute("""
                create table if not exists device_runtime_status (
                  id bigint primary key auto_increment,
                  lab_id bigint not null comment 'lab id',
                  lab_code varchar(50) default null comment 'lab code snapshot',
                  lab_name varchar(100) default null comment 'lab name snapshot',
                  device_id bigint default null comment 'optional linked asset device id',
                  terminal_record_id bigint default null comment 'lab_terminal id',
                  terminal_id varchar(80) default null comment 'terminal id',
                  device_code varchar(80) default null comment 'optional asset code snapshot',
                  host_name varchar(120) default null comment 'host name',
                  ip_address varchar(64) default null comment 'ip address',
                  mac_address varchar(64) default null comment 'mac address',
                  online_status varchar(20) not null default 'offline' comment 'online, offline',
                  runtime_status varchar(20) not null default 'normal' comment 'normal, abnormal, warning, unknown',
                  health varchar(20) not null default 'unknown' comment 'good, warning, abnormal, unknown',
                  cpu_usage decimal(5,2) default null comment 'cpu percentage',
                  memory_usage decimal(5,2) default null comment 'memory percentage',
                  disk_usage decimal(5,2) default null comment 'disk percentage',
                  login_user varchar(100) default null comment 'current login user',
                  client_version varchar(80) default null comment 'client version',
                  last_report_time datetime default null comment 'last report time',
                  source_type varchar(32) not null default 'manual' comment 'terminal, manual, mock, import',
                  metric_snapshot json default null comment 'raw metrics snapshot',
                  created_at datetime not null default current_timestamp,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  unique key uk_device_runtime_terminal (terminal_id),
                  key idx_device_runtime_lab_status (lab_id, online_status, runtime_status),
                  key idx_device_runtime_device (device_id),
                  key idx_device_runtime_last_report (last_report_time),
                  constraint fk_device_runtime_lab foreign key (lab_id) references lab(id),
                  constraint fk_device_runtime_device foreign key (device_id) references device(id),
                  constraint fk_device_runtime_terminal foreign key (terminal_record_id) references lab_terminal(id)
                ) engine=InnoDB default charset=utf8mb4 comment='latest terminal runtime status'
                """);

        jdbcTemplate.execute("""
                create table if not exists device_status_event (
                  id bigint primary key auto_increment,
                  runtime_status_id bigint default null comment 'device_runtime_status id',
                  terminal_record_id bigint default null comment 'lab_terminal id',
                  terminal_id varchar(80) default null comment 'terminal id snapshot',
                  device_id bigint default null comment 'optional linked asset device id',
                  lab_id bigint not null comment 'lab id',
                  event_type varchar(50) not null comment 'heartbeat, offline, online, abnormal, recover, manual',
                  event_level varchar(20) not null default 'info' comment 'info, warning, danger',
                  before_status varchar(50) default null comment 'previous status',
                  after_status varchar(50) default null comment 'new status',
                  title varchar(160) default null comment 'event title',
                  content varchar(1000) default null comment 'event content',
                  source_type varchar(32) not null default 'terminal' comment 'terminal, manual, mock, system',
                  occurred_at datetime not null default current_timestamp comment 'event time',
                  created_at datetime not null default current_timestamp,
                  key idx_device_status_event_lab_time (lab_id, occurred_at),
                  key idx_device_status_event_terminal (terminal_id),
                  key idx_device_status_event_type (event_type, event_level),
                  constraint fk_device_status_event_runtime foreign key (runtime_status_id) references device_runtime_status(id),
                  constraint fk_device_status_event_terminal_record foreign key (terminal_record_id) references lab_terminal(id),
                  constraint fk_device_status_event_device foreign key (device_id) references device(id),
                  constraint fk_device_status_event_lab foreign key (lab_id) references lab(id)
                ) engine=InnoDB default charset=utf8mb4 comment='device runtime status event'
                """);

        jdbcTemplate.execute("""
                create table if not exists iot_device (
                  id bigint primary key auto_increment,
                  iot_code varchar(80) not null comment 'iot device code',
                  iot_name varchar(120) not null comment 'iot device name',
                  device_type varchar(40) not null comment 'access_control, air_conditioner, light, camera, temperature_sensor, humidity_sensor, smoke_sensor',
                  lab_id bigint not null comment 'lab id',
                  lab_code varchar(50) default null comment 'lab code snapshot',
                  lab_name varchar(100) default null comment 'lab name snapshot',
                  asset_device_id bigint default null comment 'optional linked asset device id',
                  protocol varchar(40) not null default 'mock' comment 'mock, http, tcp, mqtt, rtsp',
                  base_url varchar(500) default null comment 'gateway base url',
                  endpoint varchar(500) default null comment 'status or control endpoint',
                  auth_type varchar(40) default null comment 'none, token, basic, custom',
                  configured tinyint(1) not null default 0 comment 'whether hardware gateway is configured',
                  enabled tinyint(1) not null default 1 comment 'whether device is enabled',
                  status varchar(32) not null default 'unconfigured' comment 'unconfigured, online, offline, abnormal',
                  snapshot_url varchar(500) default null comment 'camera snapshot url',
                  stream_url varchar(500) default null comment 'camera stream url',
                  source_type varchar(32) not null default 'manual' comment 'manual, yaml, import, mock',
                  remark varchar(500) default null comment 'remark',
                  created_at datetime not null default current_timestamp,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  deleted tinyint(1) not null default 0,
                  unique key uk_iot_device_code (iot_code),
                  key idx_iot_device_lab_type (lab_id, device_type, enabled, deleted),
                  key idx_iot_device_asset (asset_device_id),
                  constraint fk_iot_device_lab foreign key (lab_id) references lab(id),
                  constraint fk_iot_device_asset foreign key (asset_device_id) references device(id)
                ) engine=InnoDB default charset=utf8mb4 comment='iot device configuration'
                """);

        jdbcTemplate.execute("""
                create table if not exists iot_device_capability (
                  id bigint primary key auto_increment,
                  iot_device_id bigint not null comment 'iot_device id',
                  capability_key varchar(80) not null comment 'capability key',
                  capability_name varchar(120) not null comment 'capability name',
                  capability_type varchar(40) not null default 'control' comment 'control, telemetry, media',
                  command_key varchar(80) default null comment 'command key for writable capability',
                  readable tinyint(1) not null default 1 comment 'can be read',
                  writable tinyint(1) not null default 0 comment 'can be controlled',
                  unit varchar(20) default null comment 'metric unit',
                  min_value decimal(10,2) default null comment 'minimum value',
                  max_value decimal(10,2) default null comment 'maximum value',
                  options_json json default null comment 'options or command payload template',
                  enabled tinyint(1) not null default 1 comment 'whether capability is enabled',
                  sort_order int not null default 0 comment 'sort order',
                  created_at datetime not null default current_timestamp,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  unique key uk_iot_capability_device_key (iot_device_id, capability_key),
                  key idx_iot_capability_type (capability_type, enabled),
                  constraint fk_iot_capability_device foreign key (iot_device_id) references iot_device(id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 comment='iot device capability'
                """);

        jdbcTemplate.execute("""
                create table if not exists iot_command_log (
                  id bigint primary key auto_increment,
                  command_no varchar(80) not null comment 'command business number',
                  iot_device_id bigint default null comment 'iot_device id',
                  iot_code varchar(80) default null comment 'iot device code snapshot',
                  lab_id bigint not null comment 'lab id',
                  command_key varchar(80) not null comment 'command key',
                  action varchar(80) not null comment 'action name',
                  payload_json json default null comment 'request payload',
                  result_status varchar(32) not null default 'pending' comment 'mock_success, pending, success, failed',
                  response_summary varchar(1000) default null comment 'response summary',
                  operator_id bigint default null comment 'operator user id',
                  operator_name varchar(80) default null comment 'operator name',
                  source_type varchar(32) not null default 'manual' comment 'manual, schedule, mock, gateway',
                  executed_at datetime not null default current_timestamp comment 'command start time',
                  finished_at datetime default null comment 'command finish time',
                  created_at datetime not null default current_timestamp,
                  unique key uk_iot_command_no (command_no),
                  key idx_iot_command_lab_time (lab_id, executed_at),
                  key idx_iot_command_device (iot_device_id),
                  key idx_iot_command_result (result_status),
                  constraint fk_iot_command_log_lab foreign key (lab_id) references lab(id),
                  constraint fk_iot_command_log_device foreign key (iot_device_id) references iot_device(id) on delete set null
                ) engine=InnoDB default charset=utf8mb4 comment='iot control command log'
                """);

        jdbcTemplate.execute("""
                create table if not exists iot_telemetry_latest (
                  id bigint primary key auto_increment,
                  iot_device_id bigint not null comment 'iot_device id',
                  lab_id bigint not null comment 'lab id',
                  metric_key varchar(80) not null comment 'temperature, humidity, smoke, motion',
                  metric_value decimal(12,4) default null comment 'numeric metric value',
                  metric_text varchar(200) default null comment 'text metric value',
                  unit varchar(20) default null comment 'metric unit',
                  status varchar(32) not null default 'normal' comment 'normal, warning, abnormal, unknown',
                  source_type varchar(32) not null default 'gateway' comment 'gateway, manual, mock',
                  raw_payload json default null comment 'raw telemetry payload',
                  reported_at datetime not null default current_timestamp comment 'report time',
                  created_at datetime not null default current_timestamp,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  unique key uk_iot_telemetry_device_metric (iot_device_id, metric_key),
                  key idx_iot_telemetry_lab_metric (lab_id, metric_key, status),
                  key idx_iot_telemetry_reported_at (reported_at),
                  constraint fk_iot_telemetry_device foreign key (iot_device_id) references iot_device(id) on delete cascade,
                  constraint fk_iot_telemetry_lab foreign key (lab_id) references lab(id)
                ) engine=InnoDB default charset=utf8mb4 comment='latest iot telemetry'
                """);
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
