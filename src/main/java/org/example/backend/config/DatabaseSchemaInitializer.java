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
        ensureLanTeacherStudentTables();
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
                  lab_id bigint not null comment '实验室ID',
                  lab_code varchar(50) default null comment '实验室编号快照',
                  lab_name varchar(100) default null comment '实验室名称快照',
                  code_hash varchar(128) not null comment '接入码哈希',
                  masked_code varchar(80) not null comment '脱敏接入码',
                  status varchar(32) not null default 'active' comment '状态：active启用，inactive停用，expired过期，revoked撤销',
                  enabled tinyint(1) not null default 1 comment '是否可用于接入',
                  terminal_quota int not null default 1 comment '终端接入额度',
                  bound_terminal_count int not null default 0 comment '已绑定终端数快照',
                  expire_at datetime default null comment '过期时间',
                  source_type varchar(32) not null default 'manual' comment '来源：manual手动，import导入，system系统',
                  remark varchar(500) default null comment '备注',
                  created_by bigint default null comment '创建人用户ID',
                  created_by_name varchar(80) default null comment '创建人姓名快照',
                  created_at datetime not null default current_timestamp,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  deleted tinyint(1) not null default 0,
                  unique key uk_lab_activation_code_hash (code_hash),
                  key idx_lab_activation_code_lab_status (lab_id, status, enabled, deleted),
                  key idx_lab_activation_code_expire_at (expire_at),
                  constraint fk_lab_activation_code_lab foreign key (lab_id) references lab(id)
                ) engine=InnoDB default charset=utf8mb4 comment='实验室接入码表'
                """);

        jdbcTemplate.execute("""
                create table if not exists lab_terminal (
                  id bigint primary key auto_increment,
                  terminal_id varchar(80) not null comment '返回给客户端的稳定终端ID',
                  terminal_token_hash varchar(128) not null comment '终端令牌哈希',
                  lab_id bigint not null comment '接入码解析出的实验室ID',
                  lab_code varchar(50) default null comment '实验室编号快照',
                  lab_name varchar(100) default null comment '实验室名称快照',
                  activation_code_id bigint default null comment '实验室接入码ID',
                  terminal_name varchar(120) default null comment '终端显示名称',
                  terminal_type varchar(40) not null default 'lab_client' comment '终端类型：teacher_client教师端，lab_client实验室客户端，probe_client探针客户端',
                  host_name varchar(120) default null comment '主机名',
                  machine_code varchar(160) default null comment '机器指纹',
                  mac_address varchar(64) default null comment 'MAC地址',
                  ip_address varchar(64) default null comment 'IP地址',
                  client_version varchar(80) default null comment '客户端版本',
                  status varchar(32) not null default 'active' comment '状态：active启用，inactive停用，unbound已解绑，blocked已阻止',
                  bound_at datetime not null default current_timestamp comment '绑定时间',
                  first_connected_at datetime default null comment '首次接入时间',
                  last_seen_at datetime default null comment '最近心跳时间',
                  unbound_at datetime default null comment '解绑时间',
                  unbound_by varchar(80) default null comment '解绑操作人',
                  unbind_reason varchar(300) default null comment '解绑原因',
                  source_type varchar(32) not null default 'activation_code' comment '来源：activation_code接入码，manual手动，mock模拟',
                  remark varchar(500) default null comment '备注',
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
                ) engine=InnoDB default charset=utf8mb4 comment='实验室终端绑定表'
                """);

        jdbcTemplate.execute("""
                create table if not exists device_runtime_status (
                  id bigint primary key auto_increment,
                  lab_id bigint not null comment '实验室ID',
                  lab_code varchar(50) default null comment '实验室编号快照',
                  lab_name varchar(100) default null comment '实验室名称快照',
                  device_id bigint default null comment '可选关联资产设备ID',
                  terminal_record_id bigint default null comment '实验室终端绑定ID',
                  terminal_id varchar(80) default null comment '终端ID',
                  device_code varchar(80) default null comment '可选资产编号快照',
                  host_name varchar(120) default null comment '主机名',
                  ip_address varchar(64) default null comment 'IP地址',
                  mac_address varchar(64) default null comment 'MAC地址',
                  online_status varchar(20) not null default 'offline' comment '在线状态：online在线，offline离线',
                  runtime_status varchar(20) not null default 'normal' comment '运行状态：normal正常，abnormal异常，warning预警，unknown未知',
                  health varchar(20) not null default 'unknown' comment '健康度：good良好，warning预警，abnormal异常，unknown未知',
                  cpu_usage decimal(5,2) default null comment 'CPU使用率百分比',
                  memory_usage decimal(5,2) default null comment '内存使用率百分比',
                  disk_usage decimal(5,2) default null comment '磁盘使用率百分比',
                  login_user varchar(100) default null comment '当前登录用户',
                  client_version varchar(80) default null comment '客户端版本',
                  last_report_time datetime default null comment '最近上报时间',
                  source_type varchar(32) not null default 'manual' comment '来源：terminal终端，manual手动，mock模拟，import导入',
                  metric_snapshot json default null comment '原始指标快照',
                  created_at datetime not null default current_timestamp,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  unique key uk_device_runtime_terminal (terminal_id),
                  key idx_device_runtime_lab_status (lab_id, online_status, runtime_status),
                  key idx_device_runtime_device (device_id),
                  key idx_device_runtime_last_report (last_report_time),
                  constraint fk_device_runtime_lab foreign key (lab_id) references lab(id),
                  constraint fk_device_runtime_device foreign key (device_id) references device(id),
                  constraint fk_device_runtime_terminal foreign key (terminal_record_id) references lab_terminal(id)
                ) engine=InnoDB default charset=utf8mb4 comment='设备运行状态表'
                """);

        jdbcTemplate.execute("""
                create table if not exists device_status_event (
                  id bigint primary key auto_increment,
                  runtime_status_id bigint default null comment '设备运行状态ID',
                  terminal_record_id bigint default null comment '实验室终端绑定ID',
                  terminal_id varchar(80) default null comment '终端ID快照',
                  device_id bigint default null comment '可选关联资产设备ID',
                  lab_id bigint not null comment '实验室ID',
                  event_type varchar(50) not null comment '事件类型：heartbeat心跳，offline离线，online在线，abnormal异常，recover恢复，manual手动',
                  event_level varchar(20) not null default 'info' comment '事件级别：info信息，warning预警，danger严重',
                  before_status varchar(50) default null comment '变更前状态',
                  after_status varchar(50) default null comment '变更后状态',
                  title varchar(160) default null comment '事件标题',
                  content varchar(1000) default null comment '事件内容',
                  source_type varchar(32) not null default 'terminal' comment '来源：terminal终端，manual手动，mock模拟，system系统',
                  occurred_at datetime not null default current_timestamp comment '事件时间',
                  created_at datetime not null default current_timestamp,
                  key idx_device_status_event_lab_time (lab_id, occurred_at),
                  key idx_device_status_event_terminal (terminal_id),
                  key idx_device_status_event_type (event_type, event_level),
                  constraint fk_device_status_event_runtime foreign key (runtime_status_id) references device_runtime_status(id),
                  constraint fk_device_status_event_terminal_record foreign key (terminal_record_id) references lab_terminal(id),
                  constraint fk_device_status_event_device foreign key (device_id) references device(id),
                  constraint fk_device_status_event_lab foreign key (lab_id) references lab(id)
                ) engine=InnoDB default charset=utf8mb4 comment='设备状态事件表'
                """);

        jdbcTemplate.execute("""
                create table if not exists iot_device (
                  id bigint primary key auto_increment,
                  iot_code varchar(80) not null comment '物联设备编码',
                  iot_name varchar(120) not null comment '物联设备名称',
                  device_type varchar(40) not null comment '设备类型：access_control门禁，air_conditioner空调，light灯光，camera摄像头，temperature_sensor温度传感器，humidity_sensor湿度传感器，smoke_sensor烟雾传感器',
                  lab_id bigint not null comment '实验室ID',
                  lab_code varchar(50) default null comment '实验室编号快照',
                  lab_name varchar(100) default null comment '实验室名称快照',
                  asset_device_id bigint default null comment '可选关联资产设备ID',
                  protocol varchar(40) not null default 'mock' comment '接入协议：mock模拟，http接口，tcp网关，mqtt消息，rtsp视频',
                  base_url varchar(500) default null comment '网关基础地址',
                  endpoint varchar(500) default null comment '状态或控制接口地址',
                  auth_type varchar(40) default null comment '鉴权类型：none无，token令牌，basic基础认证，custom自定义',
                  configured tinyint(1) not null default 0 comment '是否已配置硬件网关',
                  enabled tinyint(1) not null default 1 comment '是否启用设备',
                  status varchar(32) not null default 'unconfigured' comment '状态：unconfigured未配置，online在线，offline离线，abnormal异常',
                  snapshot_url varchar(500) default null comment '摄像头抓拍地址',
                  stream_url varchar(500) default null comment '摄像头视频流地址',
                  source_type varchar(32) not null default 'manual' comment '来源：manual手动，yaml配置，import导入，mock模拟',
                  remark varchar(500) default null comment '备注',
                  created_at datetime not null default current_timestamp,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  deleted tinyint(1) not null default 0,
                  unique key uk_iot_device_code (iot_code),
                  key idx_iot_device_lab_type (lab_id, device_type, enabled, deleted),
                  key idx_iot_device_asset (asset_device_id),
                  constraint fk_iot_device_lab foreign key (lab_id) references lab(id),
                  constraint fk_iot_device_asset foreign key (asset_device_id) references device(id)
                ) engine=InnoDB default charset=utf8mb4 comment='物联设备配置表'
                """);

        jdbcTemplate.execute("""
                create table if not exists iot_gateway_config (
                  id bigint primary key auto_increment,
                  gateway_code varchar(80) not null comment '网关编码',
                  gateway_name varchar(120) not null comment '网关名称',
                  protocol_type varchar(32) not null default 'mock' comment '协议类型：mock模拟，http接口，mqtt消息',
                  gateway_address varchar(500) default null comment '网关地址',
                  auth_type varchar(40) not null default 'none' comment '鉴权方式：none无，token令牌，basic基础认证，custom自定义',
                  auth_config_json json default null comment '鉴权配置JSON，不保存明文密钥时可存引用键',
                  status varchar(32) not null default 'unconfigured' comment '状态：unconfigured未配置，enabled启用，disabled停用，abnormal异常',
                  remark varchar(500) default null comment '备注',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  deleted tinyint(1) not null default 0 comment '逻辑删除：0未删除，1已删除',
                  unique key uk_iot_gateway_code (gateway_code),
                  key idx_iot_gateway_protocol_status (protocol_type, status, deleted)
                ) engine=InnoDB default charset=utf8mb4 comment='物联网关配置表'
                """);

        jdbcTemplate.execute("""
                create table if not exists iot_device_capability (
                  id bigint primary key auto_increment,
                  iot_device_id bigint not null comment '物联设备ID',
                  capability_key varchar(80) not null comment '能力键',
                  capability_name varchar(120) not null comment '能力名称',
                  capability_type varchar(40) not null default 'control' comment '能力类型：control控制，telemetry遥测，media媒体',
                  command_key varchar(80) default null comment '可写能力的控制命令键',
                  readable tinyint(1) not null default 1 comment '是否可读取',
                  writable tinyint(1) not null default 0 comment '是否可控制',
                  unit varchar(20) default null comment '指标单位',
                  min_value decimal(10,2) default null comment '最小值',
                  max_value decimal(10,2) default null comment '最大值',
                  options_json json default null comment '选项或控制载荷模板',
                  enabled tinyint(1) not null default 1 comment '是否启用能力',
                  sort_order int not null default 0 comment '排序号',
                  created_at datetime not null default current_timestamp,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  unique key uk_iot_capability_device_key (iot_device_id, capability_key),
                  key idx_iot_capability_type (capability_type, enabled),
                  constraint fk_iot_capability_device foreign key (iot_device_id) references iot_device(id) on delete cascade
                ) engine=InnoDB default charset=utf8mb4 comment='物联设备能力表'
                """);

        jdbcTemplate.execute("""
                create table if not exists iot_gateway_point (
                  id bigint primary key auto_increment,
                  iot_device_id bigint not null comment '物联设备ID',
                  gateway_id bigint default null comment '网关配置ID',
                  command_type varchar(80) not null comment '命令类型：open开门，lock关门，set设置，off关闭，snapshot抓拍，status状态',
                  point_code varchar(120) not null comment '点位编码',
                  request_path varchar(500) default null comment '请求路径',
                  request_template text default null comment '请求模板',
                  enabled tinyint(1) not null default 1 comment '是否启用点位',
                  remark varchar(500) default null comment '备注',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  deleted tinyint(1) not null default 0 comment '逻辑删除：0未删除，1已删除',
                  unique key uk_iot_gateway_point_device_command (iot_device_id, command_type),
                  key idx_iot_gateway_point_gateway (gateway_id, enabled, deleted),
                  key idx_iot_gateway_point_code (point_code),
                  constraint fk_iot_gateway_point_device foreign key (iot_device_id) references iot_device(id) on delete cascade,
                  constraint fk_iot_gateway_point_gateway foreign key (gateway_id) references iot_gateway_config(id) on delete set null
                ) engine=InnoDB default charset=utf8mb4 comment='物联网关点位配置表'
                """);

        jdbcTemplate.execute("""
                create table if not exists iot_command_log (
                  id bigint primary key auto_increment,
                  command_no varchar(80) not null comment '控制命令业务编号',
                  iot_device_id bigint default null comment '物联设备ID',
                  iot_code varchar(80) default null comment '物联设备编码快照',
                  lab_id bigint not null comment '实验室ID',
                  gateway_id bigint default null comment '网关配置ID',
                  point_id bigint default null comment '点位配置ID',
                  command_key varchar(80) not null comment '控制命令键',
                  action varchar(80) not null comment '操作名称',
                  payload_json json default null comment '请求载荷',
                  request_params_json json default null comment '网关请求参数',
                  response_result_json json default null comment '网关响应结果',
                  result_status varchar(32) not null default 'pending' comment '控制结果：mock_success模拟成功，pending待处理，success成功，failed失败',
                  execution_status varchar(32) not null default 'pending' comment '执行状态：pending待执行，completed完成，failed失败',
                  error_message varchar(1000) default null comment '错误信息',
                  response_summary varchar(1000) default null comment '响应摘要',
                  operator_id bigint default null comment '操作人用户ID',
                  operator_name varchar(80) default null comment '操作人姓名',
                  source_type varchar(32) not null default 'manual' comment '来源：manual手动，schedule计划任务，mock模拟，gateway网关',
                  gateway_mode varchar(40) not null default 'mock_gateway' comment '网关接入模式：mock_gateway模拟网关，real_gateway_unconfigured真实网关未配置，real_gateway真实网关',
                  executed_at datetime not null default current_timestamp comment '命令执行开始时间',
                  finished_at datetime default null comment '命令执行完成时间',
                  created_at datetime not null default current_timestamp,
                  unique key uk_iot_command_no (command_no),
                  key idx_iot_command_lab_time (lab_id, executed_at),
                  key idx_iot_command_device (iot_device_id),
                  key idx_iot_command_gateway (gateway_id, point_id),
                  key idx_iot_command_result (result_status),
                  constraint fk_iot_command_log_lab foreign key (lab_id) references lab(id),
                  constraint fk_iot_command_log_device foreign key (iot_device_id) references iot_device(id) on delete set null
                ) engine=InnoDB default charset=utf8mb4 comment='物联控制命令日志表'
                """);
        addIotCommandLogColumnIfMissing("gateway_id", """
                alter table iot_command_log
                  add column gateway_id bigint default null comment '网关配置ID' after lab_id
                """);
        addIotCommandLogColumnIfMissing("point_id", """
                alter table iot_command_log
                  add column point_id bigint default null comment '点位配置ID' after gateway_id
                """);
        addIotCommandLogColumnIfMissing("request_params_json", """
                alter table iot_command_log
                  add column request_params_json json default null comment '网关请求参数' after payload_json
                """);
        addIotCommandLogColumnIfMissing("response_result_json", """
                alter table iot_command_log
                  add column response_result_json json default null comment '网关响应结果' after request_params_json
                """);
        addIotCommandLogColumnIfMissing("execution_status", """
                alter table iot_command_log
                  add column execution_status varchar(32) not null default 'pending' comment '执行状态：pending待执行，completed完成，failed失败' after result_status
                """);
        addIotCommandLogColumnIfMissing("error_message", """
                alter table iot_command_log
                  add column error_message varchar(1000) default null comment '错误信息' after execution_status
                """);
        addIotCommandLogColumnIfMissing("gateway_mode", """
                alter table iot_command_log
                  add column gateway_mode varchar(40) not null default 'mock_gateway' comment '网关接入模式：mock_gateway模拟网关，real_gateway_unconfigured真实网关未配置，real_gateway真实网关' after source_type
                """);

        jdbcTemplate.execute("""
                create table if not exists iot_telemetry_latest (
                  id bigint primary key auto_increment,
                  iot_device_id bigint not null comment '物联设备ID',
                  lab_id bigint not null comment '实验室ID',
                  metric_key varchar(80) not null comment '指标键：temperature温度，humidity湿度，smoke烟雾，motion人体感应',
                  metric_value decimal(12,4) default null comment '数值型指标值',
                  metric_text varchar(200) default null comment '文本型指标值',
                  unit varchar(20) default null comment '指标单位',
                  status varchar(32) not null default 'normal' comment '状态：normal正常，warning预警，abnormal异常，unknown未知',
                  source_type varchar(32) not null default 'gateway' comment '来源：gateway网关，manual手动，mock模拟',
                  raw_payload json default null comment '原始遥测载荷',
                  reported_at datetime not null default current_timestamp comment '上报时间',
                  created_at datetime not null default current_timestamp,
                  updated_at datetime not null default current_timestamp on update current_timestamp,
                  unique key uk_iot_telemetry_device_metric (iot_device_id, metric_key),
                  key idx_iot_telemetry_lab_metric (lab_id, metric_key, status),
                  key idx_iot_telemetry_reported_at (reported_at),
                  constraint fk_iot_telemetry_device foreign key (iot_device_id) references iot_device(id) on delete cascade,
                  constraint fk_iot_telemetry_lab foreign key (lab_id) references lab(id)
                ) engine=InnoDB default charset=utf8mb4 comment='物联环境最新遥测表'
                """);
    }

    private void addIotCommandLogColumnIfMissing(String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = database()
                  and table_name = 'iot_command_log'
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
                  id bigint primary key auto_increment comment '主键',
                  user_id int not null comment '用户ID',
                  enabled tinyint(1) not null default 0 comment '是否启用自定义API',
                  base_url varchar(500) not null comment 'OpenAI兼容接口地址',
                  api_key varchar(1000) default null comment 'API密钥',
                  model varchar(120) not null comment '文本模型',
                  vision_model varchar(120) default null comment '视觉模型',
                  system_prompt varchar(1000) default null comment '系统提示词',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  unique key uk_ai_user_config_user (user_id)
                ) comment 'AI助手用户配置表'
                """);
    }

    private void ensureLanTeacherStudentTables() {
        jdbcTemplate.execute("""
                create table if not exists teacher_host (
                  id bigint primary key auto_increment comment '主键',
                  lab_id bigint not null comment '实验室ID',
                  teacher_device_id varchar(120) not null comment '教师端设备ID',
                  host_ip varchar(64) not null comment '教师端内网IP',
                  port int not null default 8765 comment '教师端本地WebSocket端口',
                  status varchar(20) not null default 'offline' comment '状态：online在线，offline离线',
                  token varchar(500) default null comment '客户端授权令牌快照',
                  last_heartbeat_time datetime not null comment '最近心跳时间',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  unique key uk_teacher_host_lab_device (lab_id, teacher_device_id),
                  key idx_teacher_host_lab_status_time (lab_id, status, last_heartbeat_time),
                  constraint fk_teacher_host_lab foreign key (lab_id) references lab(id)
                ) engine=InnoDB default charset=utf8mb4 comment='实验室教师端主机登记表'
                """);

        jdbcTemplate.execute("""
                create table if not exists student_client (
                  id bigint primary key auto_increment comment '主键',
                  lab_id bigint not null comment '实验室ID',
                  student_device_id varchar(120) not null comment '学生端设备ID',
                  host_name varchar(120) default null comment '学生端主机名',
                  ip_address varchar(64) default null comment '学生端内网IP',
                  status varchar(20) not null default 'offline' comment '状态：online在线，offline离线',
                  token varchar(500) default null comment '客户端授权令牌快照',
                  last_heartbeat_time datetime not null comment '最近心跳时间',
                  created_at datetime not null default current_timestamp comment '创建时间',
                  updated_at datetime not null default current_timestamp on update current_timestamp comment '更新时间',
                  unique key uk_student_client_lab_device (lab_id, student_device_id),
                  key idx_student_client_lab_status_time (lab_id, status, last_heartbeat_time),
                  constraint fk_student_client_lab foreign key (lab_id) references lab(id)
                ) engine=InnoDB default charset=utf8mb4 comment='实验室学生端在线状态表'
                """);
    }
}
