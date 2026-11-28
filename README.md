# 轮渡码头客运管理系统

基于 `Servlet + JSP + DAO + SQL Server + Tomcat` 的轮渡售票课程项目，包含乘客、售票员和管理员三种角色。

## 功能

- 乘客：注册登录、查询班次、选座购票、支付、退票、未截止班次改签、查看个人资料。
- 售票员：代客售票、查看本人经办订单、检票、查看检票记录与统计。
- 管理员：航线、船舶、班次、用户、订单管理，以及营收/上座率/售票员业绩报表。
- 业务规则：5 分钟锁座、发船后停止售票、退票手续费、整单取消自动退款。
- 改签入口仅在订单所属班次尚未发船时展示，可换同班次其他座位或改到更晚班次。

## 技术栈

- JDK 21
- Maven 3.8+
- Tomcat 9（项目使用 `javax.servlet`，不要直接换 Tomcat 10）
- SQL Server 2019/2022
- HikariCP 5.1（数据库连接池）
- JUnit 4.13.2

## 目录

```text
db/
  schema.sql                 建库、建表、索引、视图
  seed.sql                   演示账号、航线、船舶、班次、座位
  migrations/
    2026-11-27_business_fixes.sql  已有数据库的升级脚本
    2026-11-28_d_e_performance.sql D+E：可空 passenger_id、分页索引
src/main/java/com/ferry/
  dao/                       数据访问与事务
  entity/                    实体
  filter/                    编码、登录、角色过滤
  servlet/                   控制器
  util/                      数据库、身份证、密码工具
src/main/resources/
  db.properties.example      数据库配置模板
src/main/webapp/             JSP、CSS、WEB-INF
src/test/java/               集成测试
```

## 1. 初始化数据库

### 新环境

用 SSMS 或 sqlcmd 按顺序执行：

1. `db/schema.sql`
2. `db/seed.sql`

脚本默认创建/使用数据库 `FerryDB`。如果已经存在同名数据库，`schema.sql` 不会删除数据，但重复建表会报“对象已存在”，请改用下面的“已有数据库升级”。

### 已有 FerryDB

只执行一次：

```text
db/migrations/2026-11-27_business_fixes.sql
db/migrations/2026-11-28_d_e_performance.sql
```

第二个脚本会把 `order.passenger_id` 改为可空，并按票面身份证把历史代客订单
确定性关联到已注册乘客；匹配不到时保留 `NULL`，表示“未注册乘客”。真实乘客
身份始终以 `ticket` 表的姓名、身份证、手机号为准。

## 2. 配置数据库连接

复制：

```text
src/main/resources/db.properties.example
```

为：

```text
src/main/resources/db.properties
```

填写本机 SQL Server 地址、账号和密码。`db.properties` 已被 `.gitignore` 排除，不要提交真实密码。

也可以用环境变量覆盖：

```text
FERRY_DB_URL
FERRY_DB_USER
FERRY_DB_PASSWORD
FERRY_DB_DRIVER
FERRY_DB_POOL_MAX
FERRY_DB_POOL_MIN
FERRY_DB_POOL_TIMEOUT
```

默认地址是 `localhost:1433`，数据库名 `FerryDB`。

连接池默认最大 10 个连接、最小空闲 2 个、获取连接超时 10 秒，可在
`db.properties` 中用 `db.pool.maxSize`、`db.pool.minIdle`、
`db.pool.connectionTimeoutMs` 调整。应用停止时由 `AppLifecycleListener` 关闭连接池。

## 3. 在 IDEA 中导入

1. 打开 `E:\Mycode\ferry-ticket-system`。
2. 让 IDEA 以 Maven 项目导入 `pom.xml`。
3. Project SDK 选择 JDK 21。
4. 右侧 Maven 面板点击刷新，等待依赖下载完成。
5. 确认 `src/main/resources/db.properties` 存在。

如果修改了 `DBUtil`、`db.properties` 或类路径资源，先执行 Maven 刷新/重新构建，再启动 Tomcat。否则 Smart Tomcat 可能继续使用旧的 `target/classes`。

## 4. 使用 Smart Tomcat 启动

1. 安装 IDEA 的 Smart Tomcat 插件。
2. `Run -> Edit Configurations -> + -> Smart Tomcat`。
3. Tomcat Server 选择本机 Tomcat 9 目录。
4. Deployment 选择项目模块 `ferry-ticket-system`，部署目录选 `src/main/webapp`。
5. Context path 填 `/ferry`，端口填 `8080`。
6. VM options 建议加：

```text
-Dfile.encoding=UTF-8
```

7. 启动后访问：

```text
http://localhost:8080/ferry/
```

演示账号（密码均为 `123456`）：

```text
管理员：admin
售票员：seller1
乘客：pass1
```

## 5. 运行测试

测试会连接本机 `FerryDB`，运行前先启动 SQL Server 并导入演示数据。

命令行：

```text
mvn test
```

也可以在 IDEA 中分别运行 `src/test/java/com/ferry/dao/` 下的集成测试：

- `OrderDAOTest`：改签流程。
- `OrderPaymentAndCancelTest`：支付、退票、取消班次。
- `ScheduleDAOTest`：班次编辑、到达时间、容量与座位扩容。
- `StatDAOTest`：净收入统计与按航线已售数。
- `PassengerLinkAndPagingTest`：未注册乘客存 `NULL`、身份证大小写匹配、分页边界。

## 订单乘客关联规则

- 乘客本人购票：`order.passenger_id` 写当前登录乘客的 `user.id`。
- 售票员代客购票：按票面身份证匹配 `role='passenger'` 的注册账号；
  找到则写该账号 ID，找不到写 `NULL`。
- `order.seller_id` 始终表示经办售票员，与乘客账号无关。
- 密码按课程要求继续使用 MD5 存储，本轮不做哈希算法升级。

## 页面访问与分页

- 业务 JSP 仍位于 `src/main/webapp/{admin,seller,passenger}`，但 `AuthFilter`
  会拒绝浏览器直接请求这些目录下的 `.jsp`；页面只能由 Servlet 转发进入。
  首页和班次页分别走 `/admin/home`、`/seller/home`、`/passenger/home`、
  `/passenger/schedules`。
- 管理员订单、管理员用户、检票记录使用 SQL Server
  `OFFSET ... ROWS FETCH NEXT ... ROWS ONLY` 分页，默认每页 10 / 10 / 15 条。
- 订单列表的资金流水和改签记录改为按订单 ID 批量查询，不再逐单查询。
- 已截止班次只保留退票入口；未截止班次可换座或按更晚班次改签。
- 原 `dbtest.jsp` 已删除，避免公网暴露数据库连通性测试和异常堆栈。

## 常见问题

### 控制台中文乱码

在 Smart Tomcat 的 VM options 加 `-Dfile.encoding=UTF-8`，并确认 IDEA 的 File Encodings 使用 UTF-8。

### 登录失败

先确认 `db.properties` 的账号密码正确，再确认 `FerryDB` 中已执行 `seed.sql`。数据库中的演示密码是 MD5 存储的 `123456`。

### SQL Server 报 SSL/证书错误

连接串保留：

```text
encrypt=true;trustServerCertificate=true;
```

本机开发使用自签名证书时这是必要的；生产环境应换成受信任证书。

### 改了 DBUtil 但运行仍是旧代码

执行 Maven 刷新/重新构建，确认 `target/classes/com/ferry/util/DBUtil.class` 已更新；Smart Tomcat 不会自动识别所有源码资源变化。

## 当前优化范围

本轮已完成：

- 新增建库、演示数据和已有库迁移脚本。
- 数据库密码从源码移到本地配置。
- 支付失败不再显示“支付成功”。
- 退票、改签增加订单行锁和状态条件更新。
- 取消班次只允许发船前操作，并完整处理待支付、已支付、已检票订单。
- 班次编辑重新计算到达时间，校验容量和时间冲突。
- 统计报表改为净收入口径，修复按航线重复计算已售座位。
- 引入 HikariCP 连接池，应用关闭时释放连接。
- 订单 `passenger_id` 改为可空，支持“未注册乘客”的代客购票。
- 管理员订单/用户、检票记录分页；订单页资金流水/改签记录改为批量查询。
- 下线 `dbtest.jsp`，业务 JSP 禁止浏览器直接访问。

仍未包含（后续可选）：

- XSS 转义、CSRF Token、密码哈希算法升级。
- JSP 全面迁移到 JSTL/EL。
