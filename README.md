# 智云实验室后端项目说明

这份文档写给后续维护和二次开发使用。重点说明后端代码怎么分层、接口在哪里、Mapper 怎么写、数据库字段怎么对应、前后端联调时怎么排错。
前端仓库：https://gitee.com/translator-of-zheng-haotao/zhiyun-laboratory
## 1. 技术栈

后端目录：`Backend`

核心技术：

- Java 17
- Spring Boot 4
- Spring Web MVC
- MyBatis
- MySQL
- HikariCP
- JWT
- SpringDoc OpenAPI
- Vosk 语音识别
- WebSocket

数据库：

```text
smart_lab_basic
```

默认服务端口：

```text
8080
```

## 2. 启动与编译

进入后端目录：

```powershell
cd Backend
```

编译：

```powershell
mvn.cmd -DskipTests compile
```

启动：

```powershell
mvn.cmd spring-boot:run
```

也可以在 IntelliJ IDEA 中直接运行：

```text
org.example.backend.BackendApplication
```

启动成功后通常可以看到：

```text
Tomcat started on port 8080
Started BackendApplication
```

## 3. 配置文件

主配置文件：

```text
Backend/src/main/resources/application.yaml
```

关键配置：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/smart_lab_basic
    username: root
    password: root
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis:
  mapper-locations: classpath*:mapper/*.xml
  configuration:
    map-underscore-to-camel-case: true
```

含义：

- 后端监听 `8080`。
- 数据库连接到本机 MySQL 的 `smart_lab_basic`。
- Mapper XML 放在 `src/main/resources/mapper/`。
- 数据库下划线字段可以映射到 Java 驼峰字段。

## 4. 重要目录结构

```text
Backend/src/main/java/org/example/backend
├─ config/                  全局配置、日志过滤器、异常处理等
├─ controller/              HTTP 接口入口
├─ entity/                  实体类，对应数据库表或业务记录
├─ mapper/                  MyBatis Mapper 接口
├─ result/                  统一响应 Result
├─ security/                JWT、密码、认证过滤器
├─ service/                 Service 接口
├─ service/impl/            Service 实现
├─ VO/                      视图对象、复杂响应对象
├─ websocket/               WebSocket
└─ BackendApplication.java  Spring Boot 启动类
```

SQL 映射目录：

```text
Backend/src/main/resources/mapper
```

## 5. 后端分层方式

典型调用链：

```text
Controller -> Service -> ServiceImpl -> Mapper -> Mapper XML -> MySQL
```

各层职责：

### Controller

位置：

```text
Backend/src/main/java/org/example/backend/controller
```

职责：

- 定义接口路径。
- 接收请求参数。
- 调用 Service。
- 返回统一 `Result`。

示例：

```java
@GetMapping("/devices")
public Result getDevices() {
    return Result.success("list devices success", devicesService.getDevices());
}
```

### Service

位置：

```text
Backend/src/main/java/org/example/backend/service
```

职责：

- 定义业务能力。
- 让 Controller 依赖接口，而不是直接依赖实现。

### ServiceImpl

位置：

```text
Backend/src/main/java/org/example/backend/service/impl
```

职责：

- 写业务逻辑。
- 做字段默认值处理。
- 做数据校验。
- 调用多个 Mapper。
- 控制事务。
- 记录业务事件。

### Mapper 接口

位置：

```text
Backend/src/main/java/org/example/backend/mapper
```

职责：

- 定义数据库操作方法。
- 参数名要和 XML 中使用的参数对应。

多参数建议使用 `@Param`。

### Mapper XML

位置：

```text
Backend/src/main/resources/mapper
```

职责：

- 写 SQL。
- 配置 resultType、insert、update、delete、select。

## 6. 统一返回结构

统一响应类：

```text
Backend/src/main/java/org/example/backend/result/Result.java
```

成功响应：

```java
return Result.success("message", data);
```

返回结构：

```json
{
  "code": 200,
  "message": "message",
  "data": {}
}
```

失败响应：

```java
return Result.error("错误信息");
```

前端 Axios 会检查 `code`，不是 `200` 时会当成业务失败。

## 7. Controller 列表

当前主要 Controller：

```text
AcademicScheduleController.java       教务课表相关
AiAssistantConfigController.java      AI 助手配置
BusinessLoopReportController.java     业务闭环报表
ClassTimetableController.java         班级课表
ConsumableController.java             耗材库存
CourseEnvironmentController.java      课程环境
DashboardController.java              工作台概览
DevicesController.java                设备资产
EnvironmentTemplateController.java    环境模板
IotHardwareController.java            物联网硬件
LabController.java                    实验室
ModuleRecordController.java           通用模块记录
NoticeController.java                 通知公告
RepairController.java                 报修
ReservationsController.java           预约
ScheduleAdjustmentController.java     调课
softwareController.java               软件环境
SystemSettingsController.java         系统设置
UsageRecordController.java            使用记录
UserController.java                   用户与权限
```

找接口时优先去 `controller` 目录按业务名搜索。

## 8. Mapper 与 XML 对应关系

Mapper 接口目录：

```text
Backend/src/main/java/org/example/backend/mapper
```

Mapper XML 目录：

```text
Backend/src/main/resources/mapper
```

常见对应：

```text
DevicesMapper.java          -> DevicesMapper.xml
LabMapper.java              -> LabMapper.xml
UserMapper.java             -> 用户相关 SQL
RepairMapper.java           -> RepairMapper.xml
ReservationsMapper.java     -> ReservationsMapper.xml
```

注意：

- XML 的 namespace 必须等于 Mapper 接口完整类名。
- XML 的 `id` 必须等于 Mapper 方法名。
- 参数名要对应。

示例：

```java
int updateDevices(@Param("id") Long id, @Param("devices") DevicesEntity devices);
```

```xml
<update id="updateDevices">
    update device
    set device_name = #{devices.deviceName}
    where id = #{id}
</update>
```

## 9. 实体与数据库字段

实体目录：

```text
Backend/src/main/java/org/example/backend/entity
```

实体字段一般使用 Java 驼峰：

```java
private String deviceCode;
private String deviceName;
private Long labId;
private Long ownerUserId;
```

数据库字段一般使用下划线：

```sql
device_code
device_name
lab_id
owner_user_id
```

因为开启了：

```yaml
map-underscore-to-camel-case: true
```

所以多数查询结果可以自动映射。

如果前端字段名和后端实体字段名不一样，可以使用：

```java
@JsonAlias("code")
private String deviceCode;
```

这样前端传：

```json
{ "code": "PC-204-01" }
```

后端也能接到 `deviceCode`。

## 10. 设备资产模块详解

设备资产模块是当前比较完整的模块，可作为新增业务参考。

### 10.1 Controller

```text
Backend/src/main/java/org/example/backend/controller/DevicesController.java
```

主要接口：

```text
GET    /devices
POST   /devices
GET    /devices/{id}
PUT    /devices/{id}
DELETE /devices/{id}
PUT    /devices/batch
POST   /devices/reset

GET    /device-inventory-records
GET    /devices/{id}/inventory-records
POST   /devices/{id}/inventory

GET    /device-transfer-records
GET    /devices/{id}/transfer-records
POST   /devices/{id}/transfer
```

### 10.2 Service

```text
Backend/src/main/java/org/example/backend/service/DevicesService.java
```

这里声明：

- 查询设备
- 新增设备
- 编辑设备
- 删除设备
- 批量更新设备
- 查询盘点记录
- 保存盘点记录
- 查询调拨记录
- 保存调拨记录

### 10.3 ServiceImpl

```text
Backend/src/main/java/org/example/backend/service/impl/DevicesServiceImpl.java
```

主要逻辑：

- 设备默认状态处理。
- 设备默认健康度处理。
- 新增设备后记录业务事件。
- 更新设备后记录业务事件。
- 删除设备后记录业务事件。
- 保存盘点记录。
- 盘点后同步更新设备状态、健康度、在线状态、盘点日期。
- 保存调拨记录。
- 调拨后同步更新设备实验室、责任人、位置。
- 调拨使用 `@Transactional` 保证设备状态和调拨记录一起成功或一起失败。

### 10.4 设备实体

```text
Backend/src/main/java/org/example/backend/entity/DevicesEntity.java
```

常用字段：

```java
private Long id;
private String deviceCode;
private String deviceName;
private String category;
private Long labId;
private String labName;
private String location;
private Long ownerUserId;
private String ownerUsername;
private String status;
private String health;
private Boolean online;
private String specs;
private String purchaseDate;
private String inventoryDate;
private String warrantyDate;
private Integer usageHours;
private String maintenance;
```

### 10.5 设备 Mapper

```text
Backend/src/main/java/org/example/backend/mapper/DevicesMapper.java
Backend/src/main/resources/mapper/DevicesMapper.xml
```

主要 SQL：

- 插入设备。
- 更新设备。
- 删除设备。
- 查询设备列表。
- 查询单个设备。
- 按实验室查询设备。
- 更新盘点状态。
- 更新调拨状态。

### 10.6 盘点记录

实体：

```text
DeviceInventoryRecordEntity.java
```

Mapper：

```text
DeviceInventoryRecordMapper.java
DeviceInventoryRecordMapper.xml
```

功能：

- 自动创建盘点记录表。
- 查询全部盘点记录。
- 按设备查询盘点记录。
- 插入盘点记录。

保存盘点时，前端调用：

```text
POST /devices/{id}/inventory
```

后端会：

1. 查询设备是否存在。
2. 标准化盘点记录。
3. 插入盘点记录。
4. 更新设备状态。
5. 返回最新设备和该设备盘点记录。

### 10.7 调拨记录

实体：

```text
DeviceTransferRecordEntity.java
```

Mapper：

```text
DeviceTransferRecordMapper.java
DeviceTransferRecordMapper.xml
```

功能：

- 自动创建调拨记录表。
- 查询全部调拨记录。
- 按设备查询调拨记录。
- 插入调拨记录。

保存调拨时，前端调用：

```text
POST /devices/{id}/transfer
```

后端会：

1. 查询设备是否存在。
2. 判断实验室、责任人、安装位置是否有变化。
3. 自动识别调拨类型：
   - 设备调拨
   - 责任人变更
   - 安装位置变更
   - 综合调拨
4. 更新设备当前实验室、责任人、位置。
5. 插入调拨记录。
6. 返回最新设备和该设备调拨记录。

## 11. 新增一个业务接口的完整步骤

假设新增“设备借用”。

### 11.1 新建实体

```text
Backend/src/main/java/org/example/backend/entity/DeviceBorrowRecordEntity.java
```

### 11.2 新建 Mapper 接口

```text
Backend/src/main/java/org/example/backend/mapper/DeviceBorrowRecordMapper.java
```

### 11.3 新建 Mapper XML

```text
Backend/src/main/resources/mapper/DeviceBorrowRecordMapper.xml
```

### 11.4 在 Service 中声明方法

```text
Backend/src/main/java/org/example/backend/service/DevicesService.java
```

### 11.5 在 ServiceImpl 中实现

```text
Backend/src/main/java/org/example/backend/service/impl/DevicesServiceImpl.java
```

如果涉及多表写入，加：

```java
@Transactional
```

### 11.6 在 Controller 暴露接口

```java
@PostMapping("/devices/{id:\\d+}/borrow")
public Result borrowDevice(@PathVariable Long id, @RequestBody DeviceBorrowRecordEntity record) {
    return Result.success("borrow device success", devicesService.borrowDevice(id, record));
}
```

### 11.7 前端对接

前端需要改：

```text
web/src/api/endpoints.js
web/src/api/services/basicResources.js 或新 service 文件
web/src/composables/useDeviceManagement.js
对应页面组件
```

## 12. 常见 SQL 写法

### 12.1 查询列表

```xml
<select id="getDevices" resultType="org.example.backend.entity.DevicesEntity">
    select
        d.id,
        d.device_code as deviceCode,
        d.device_name as deviceName
    from device d
    order by d.id
</select>
```

### 12.2 查询详情

```xml
<select id="getDevicesById" resultType="org.example.backend.entity.DevicesEntity">
    select
        <include refid="DeviceColumns" />
    from device d
    where d.id = #{id}
</select>
```

### 12.3 新增并回填自增 id

```xml
<insert id="insertRecord" useGeneratedKeys="true" keyProperty="id">
    insert into table_name (name)
    values (#{name})
</insert>
```

### 12.4 更新

```xml
<update id="updateRecord">
    update table_name
    set
        name = #{record.name},
        updated_at = now()
    where id = #{id}
</update>
```

### 12.5 动态条件

```xml
<where>
    <if test="deviceId != null">
        and device_id = #{deviceId}
    </if>
</where>
```

## 13. 事务使用建议

需要事务的场景：

- 同时更新主表和记录表。
- 同时插入多张表。
- 一个业务动作里有多个数据库写操作。
- 中途失败时必须全部回滚。

写法：

```java
@Override
@Transactional
public SomeResult doSomething(...) {
    // 多个数据库写操作
}
```

注意：

- `@Transactional` 通常放在 ServiceImpl 的 public 方法上。
- 同类内部方法互相调用时，事务可能不会生效，尽量把事务放在外部入口方法上。

## 14. JWT 与登录

安全相关目录：

```text
Backend/src/main/java/org/example/backend/security
```

常见文件：

```text
JwtAuthenticationFilter.java
JwtService.java
PasswordService.java
```

配置：

```yaml
smart-lab:
  security:
    jwt:
      enabled: true
      secret: ${SMART_LAB_JWT_SECRET:smart-lab-dev-jwt-secret-change-in-production-2026}
      expiration-seconds: 7200
```

前端请求会带：

```text
Authorization: Bearer <token>
```

如果接口返回 401，前端会清理登录态并跳回登录页。

## 15. 请求日志与异常

请求日志过滤器会输出：

```text
request start method=GET uri=/devices ...
request end method=GET uri=/devices status=200 durationMs=...
```

全局异常处理会输出：

```text
request failed method=POST uri=/devices/7/inventory requestId=...
```

排查后端错误时，优先看：

1. requestId。
2. 异常类型。
3. Caused by。
4. JSON 字段路径。
5. Mapper SQL。

常见错误：

- `HttpMessageNotReadableException`：前端传的 JSON 和实体字段类型不匹配。
- `InvalidFormatException`：例如后端 Long 字段收到了 `"inv-xxx"` 字符串。
- SQL 字段不存在：Mapper XML 和数据库表结构不一致。
- 参数找不到：Mapper 接口参数名和 XML 中引用的名字不一致。

## 16. 与前端联调

后端启动：

```text
http://127.0.0.1:8080
```

前端代理：

```text
/api -> http://127.0.0.1:8080
```

联调流程：

1. 启动 MySQL。
2. 确认 `smart_lab_basic` 数据库存在。
3. 启动后端。
4. 启动前端。
5. 登录系统。
6. 打开浏览器 Network。
7. 查看接口路径和响应。
8. 如果接口 500，看后端控制台。

## 17. SpringDoc 接口文档

项目引入了 SpringDoc。

启动后可以尝试访问：

```text
http://127.0.0.1:8080/swagger-ui.html
http://127.0.0.1:8080/v3/api-docs
```

如果生产环境不想暴露接口文档，可以在配置中关闭：

```yaml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

## 18. Python 与课表抓取

配置在：

```yaml
timetable:
  crawler:
    python: ${TIMETABLE_CRAWLER_PYTHON:python}
    script-path: ${TIMETABLE_CRAWLER_SCRIPT:Python/crawl_school_timetable.py}
    import-script-path: ${TIMETABLE_IMPORT_SCRIPT:Python/parse_and_import_timetable.py}
    working-dir: ${TIMETABLE_CRAWLER_WORKDIR:Python}
```

含义：

- 后端会调用 Python 脚本抓取或导入课表。
- 默认工作目录是项目根目录下的 `Python`。
- 如果 Python 路径或脚本路径不同，用环境变量覆盖。

## 19. Vosk 语音模型

配置：

```yaml
speech:
  vosk:
    model-path: ${VOSK_MODEL_PATH:../vosk-model-small-cn-0.22/vosk-model-small-cn-0.22}
```

注意：

- 模型路径包含中文时，服务里可能会复制到临时 ASCII 路径再加载。
- 启动日志里会看到 Vosk 模型加载信息。

## 20. 编译和提交前检查

后端提交前建议执行：

```powershell
mvn.cmd -DskipTests compile
```

如果改了接口，还要实际启动后端并从前端页面点一遍。

查看 Git 状态：

```powershell
git status --short
```

只暂存本次相关文件：

```powershell
git add -- src/main/java/org/example/backend/controller/DevicesController.java
```

提交：

```powershell
git commit -m "完善设备盘点调拨接口"
```

## 21. 常见修改入口速查

### 改设备接口

```text
controller/DevicesController.java
service/DevicesService.java
service/impl/DevicesServiceImpl.java
mapper/DevicesMapper.java
resources/mapper/DevicesMapper.xml
entity/DevicesEntity.java
```

### 改实验室接口

```text
controller/LabController.java
mapper/LabMapper.java
resources/mapper/LabMapper.xml
entity/LabEntity.java
```

### 改用户权限

```text
controller/UserController.java
mapper/UserMapper.java
security/
```

### 改预约

```text
controller/ReservationsController.java
mapper/ReservationsMapper.java
resources/mapper/ReservationsMapper.xml
```

### 改报修

```text
controller/RepairController.java
mapper/RepairMapper.java
resources/mapper/RepairMapper.xml
```

### 改通知

```text
controller/NoticeController.java
mapper/NoticeMapper.java
resources/mapper/NoticeMapper.xml
```

## 22. 数据类型注意事项

前后端最容易出问题的是 id 类型。

后端通常是：

```java
private Long id;
```

前端如果传：

```json
{ "id": "inv-1778671698192-3euwly" }
```

后端会报错，因为字符串不能转 Long。

处理方式：

- 新增记录时不要传前端临时字符串 id。
- 前端提交前删除临时 id。
- 后端实体如果确实要支持字符串，就把字段改成 String，但数据库也要同步。

## 23. Git 说明

`Backend` 是独立 Git 仓库。

进入仓库：

```powershell
cd Backend
```

查看状态：

```powershell
git status --short
```

不要把以下内容随便提交：

- 临时 Python 输出目录。
- 大模型文件。
- 日志文件。
- IDE 临时文件。
- 和当前功能无关的大量改动。
