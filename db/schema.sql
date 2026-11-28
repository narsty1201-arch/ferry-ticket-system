/*
 * 轮渡码头客运管理系统 —— FerryDB 初始化脚本
 *
 * 适用环境：SQL Server 2019/2022
 * 使用方式：在 SSMS 或 sqlcmd 中整体执行；已有数据库不会被删除。
 * 注意：本脚本只负责建表，不插入演示数据。演示数据见 seed.sql。
 */

IF DB_ID(N'FerryDB') IS NULL
    CREATE DATABASE FerryDB;
GO

USE FerryDB;
GO

CREATE TABLE dbo.route (
    id              INT IDENTITY(1,1) NOT NULL,
    route_name      NVARCHAR(50)  NOT NULL,
    start_port      NVARCHAR(50)  NOT NULL,
    end_port        NVARCHAR(50)  NOT NULL,
    duration_min    INT           NOT NULL,
    description     NVARCHAR(200) NULL,
    status          VARCHAR(10)   NOT NULL CONSTRAINT DF_route_status DEFAULT ('active'),
    is_deleted      BIT           NOT NULL CONSTRAINT DF_route_deleted DEFAULT (0),
    CONSTRAINT PK_route PRIMARY KEY (id),
    CONSTRAINT UQ_route_identity UNIQUE (route_name, start_port, end_port),
    CONSTRAINT CK_route_duration CHECK (duration_min > 0),
    CONSTRAINT CK_route_status CHECK (status IN ('active','closed'))
);
GO

CREATE TABLE dbo.ship (
    id              INT IDENTITY(1,1) NOT NULL,
    ship_name       NVARCHAR(50)  NOT NULL,
    total_seats     INT           NOT NULL,
    ship_type       NVARCHAR(20)  NOT NULL,
    description     NVARCHAR(200) NULL,
    status          VARCHAR(10)   NOT NULL CONSTRAINT DF_ship_status DEFAULT ('active'),
    is_deleted      BIT           NOT NULL CONSTRAINT DF_ship_deleted DEFAULT (0),
    CONSTRAINT PK_ship PRIMARY KEY (id),
    CONSTRAINT UQ_ship_name UNIQUE (ship_name),
    CONSTRAINT CK_ship_seats CHECK (total_seats > 0),
    CONSTRAINT CK_ship_type CHECK (ship_type IN (N'普通船',N'高速船',N'豪华船')),
    CONSTRAINT CK_ship_status CHECK (status IN ('active','disabled'))
);
GO

CREATE TABLE dbo.ship_seat (
    id          INT IDENTITY(1,1) NOT NULL,
    ship_id     INT           NOT NULL,
    seat_no     VARCHAR(10)   NOT NULL,
    seat_type   NVARCHAR(20)  NOT NULL CONSTRAINT DF_shipseat_type DEFAULT (N'普通座'),
    CONSTRAINT PK_ship_seat PRIMARY KEY (id),
    CONSTRAINT UQ_shipseat_no UNIQUE (ship_id, seat_no),
    CONSTRAINT CK_shipseat_type CHECK (seat_type IN (N'VIP座',N'商务座',N'普通座')),
    CONSTRAINT FK_shipseat_ship FOREIGN KEY (ship_id) REFERENCES dbo.ship(id)
);
GO

CREATE TABLE dbo.schedule (
    id              INT IDENTITY(1,1) NOT NULL,
    route_id        INT           NOT NULL,
    ship_id         INT           NOT NULL,
    depart_time     DATETIME      NOT NULL,
    arrive_time     DATETIME      NOT NULL,
    price           DECIMAL(10,2) NOT NULL,
    total_seats     INT           NOT NULL,
    sold_seats      INT           NOT NULL CONSTRAINT DF_schedule_sold DEFAULT (0),
    status          NVARCHAR(10)  NOT NULL CONSTRAINT DF_schedule_status DEFAULT (N'售票中'),
    is_deleted      BIT           NOT NULL CONSTRAINT DF_schedule_deleted DEFAULT (0),
    create_time     DATETIME      NOT NULL CONSTRAINT DF_schedule_create DEFAULT (GETDATE()),
    CONSTRAINT PK_schedule PRIMARY KEY (id),
    CONSTRAINT UQ_sched_ship_time UNIQUE (ship_id, depart_time),
    CONSTRAINT CK_sched_price CHECK (price > 0),
    CONSTRAINT CK_sched_sold CHECK (sold_seats >= 0),
    CONSTRAINT CK_sched_seats CHECK (sold_seats <= total_seats),
    CONSTRAINT CK_sched_status CHECK (status IN (N'待发布',N'售票中',N'已发船',N'已完成',N'已取消')),
    CONSTRAINT FK_sched_route FOREIGN KEY (route_id) REFERENCES dbo.route(id),
    CONSTRAINT FK_sched_ship FOREIGN KEY (ship_id) REFERENCES dbo.ship(id)
);
GO

CREATE TABLE dbo.seat (
    id              INT IDENTITY(1,1) NOT NULL,
    schedule_id     INT          NOT NULL,
    seat_no         VARCHAR(10)  NOT NULL,
    seat_status     NVARCHAR(10) NOT NULL CONSTRAINT DF_seat_status DEFAULT (N'可售'),
    lock_time       DATETIME     NULL,
    CONSTRAINT PK_seat PRIMARY KEY (id),
    CONSTRAINT UQ_seat_no UNIQUE (schedule_id, seat_no),
    CONSTRAINT CK_seat_status CHECK (seat_status IN (N'可售',N'锁定',N'已售')),
    CONSTRAINT FK_seat_sched FOREIGN KEY (schedule_id) REFERENCES dbo.schedule(id)
);
GO

CREATE TABLE dbo.[user] (
    id              INT IDENTITY(1,1) NOT NULL,
    username        VARCHAR(50)   NOT NULL,
    password        VARCHAR(100)  NOT NULL,
    role            VARCHAR(20)   NOT NULL,
    real_name       NVARCHAR(50)  NOT NULL,
    phone           VARCHAR(11)   NOT NULL,
    id_card         VARCHAR(18)   NULL,
    status          VARCHAR(10)   NOT NULL CONSTRAINT DF_user_status DEFAULT ('normal'),
    fail_count      INT           NOT NULL CONSTRAINT DF_user_fail DEFAULT (0),
    lock_until      DATETIME      NULL,
    create_time     DATETIME      NOT NULL CONSTRAINT DF_user_create DEFAULT (GETDATE()),
    CONSTRAINT PK_user PRIMARY KEY (id),
    CONSTRAINT UQ_user_username UNIQUE (username),
    CONSTRAINT CK_user_role CHECK (role IN ('passenger','seller','admin')),
    CONSTRAINT CK_user_status CHECK (status IN ('normal','disabled')),
    CONSTRAINT CK_user_phone CHECK (phone LIKE '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'),
    CONSTRAINT CK_user_idcard CHECK (id_card IS NULL OR id_card LIKE '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9Xx]')
);
GO

CREATE TABLE dbo.[order] (
    id              INT IDENTITY(1,1) NOT NULL,
    order_no        VARCHAR(30)   NOT NULL,
    schedule_id     INT           NOT NULL,
    passenger_id    INT           NULL,
    seller_id       INT           NULL,
    total_amount    DECIMAL(10,2) NOT NULL,
    status          NVARCHAR(10)  NOT NULL CONSTRAINT DF_order_status DEFAULT (N'待支付'),
    create_time     DATETIME      NOT NULL CONSTRAINT DF_order_create DEFAULT (GETDATE()),
    pay_time        DATETIME      NULL,
    cancel_time     DATETIME      NULL,
    refund_amount   DECIMAL(10,2) NULL,
    refund_reason   NVARCHAR(200) NULL,
    refund_time     DATETIME      NULL,
    refund_eta      DATETIME      NULL,
    CONSTRAINT PK_order PRIMARY KEY (id),
    CONSTRAINT UQ_order_no UNIQUE (order_no),
    CONSTRAINT CK_order_amount CHECK (total_amount > 0),
    CONSTRAINT CK_order_status CHECK (status IN (N'待支付',N'已支付',N'已检票',N'已完成',N'已退票',N'已取消')),
    CONSTRAINT FK_order_sched FOREIGN KEY (schedule_id) REFERENCES dbo.schedule(id),
    CONSTRAINT FK_order_passenger FOREIGN KEY (passenger_id) REFERENCES dbo.[user](id),
    CONSTRAINT FK_order_seller FOREIGN KEY (seller_id) REFERENCES dbo.[user](id)
);
GO

CREATE TABLE dbo.ticket (
    id              INT IDENTITY(1,1) NOT NULL,
    order_id        INT           NOT NULL,
    schedule_id     INT           NOT NULL,
    seat_id         INT           NOT NULL,
    passenger_name  NVARCHAR(50)  NOT NULL,
    id_card         VARCHAR(18)   NOT NULL,
    passenger_phone VARCHAR(11)   NOT NULL,
    price           DECIMAL(10,2) NOT NULL,
    status          NVARCHAR(10)  NOT NULL CONSTRAINT DF_ticket_status DEFAULT (N'待支付'),
    qr_code         VARCHAR(50)   NULL,
    create_time     DATETIME      NOT NULL CONSTRAINT DF_ticket_create DEFAULT (GETDATE()),
    CONSTRAINT PK_ticket PRIMARY KEY (id),
    CONSTRAINT UQ_ticket_person_sched UNIQUE (id_card, schedule_id),
    CONSTRAINT CK_ticket_status CHECK (status IN (N'待支付',N'已支付',N'已检票',N'已退票',N'已取消')),
    CONSTRAINT CK_ticket_idcard CHECK (id_card LIKE '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9Xx]'),
    CONSTRAINT CK_ticket_phone CHECK (passenger_phone LIKE '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'),
    CONSTRAINT FK_ticket_order FOREIGN KEY (order_id) REFERENCES dbo.[order](id),
    CONSTRAINT FK_ticket_sched FOREIGN KEY (schedule_id) REFERENCES dbo.schedule(id),
    CONSTRAINT FK_ticket_seat FOREIGN KEY (seat_id) REFERENCES dbo.seat(id)
);
GO

CREATE TABLE dbo.check_in (
    id              INT IDENTITY(1,1) NOT NULL,
    ticket_id       INT           NOT NULL,
    seller_id       INT           NOT NULL,
    check_time      DATETIME      NOT NULL CONSTRAINT DF_check_create DEFAULT (GETDATE()),
    status          NVARCHAR(10)  NOT NULL,
    fail_reason     NVARCHAR(100) NULL,
    CONSTRAINT PK_check_in PRIMARY KEY (id),
    CONSTRAINT UQ_check_ticket UNIQUE (ticket_id),
    CONSTRAINT CK_check_status CHECK (status IN (N'成功',N'失败')),
    CONSTRAINT FK_check_ticket FOREIGN KEY (ticket_id) REFERENCES dbo.ticket(id),
    CONSTRAINT FK_check_seller FOREIGN KEY (seller_id) REFERENCES dbo.[user](id)
);
GO

CREATE TABLE dbo.change_log (
    id              INT IDENTITY(1,1) NOT NULL,
    order_id        INT           NOT NULL,
    old_schedule_id INT           NOT NULL,
    new_schedule_id INT           NOT NULL,
    old_seat_id     INT           NOT NULL,
    new_seat_id     INT           NOT NULL,
    price_diff      DECIMAL(10,2) NOT NULL CONSTRAINT DF_change_diff DEFAULT (0),
    operator_id     INT           NOT NULL,
    change_time     DATETIME      NOT NULL CONSTRAINT DF_change_create DEFAULT (GETDATE()),
    reason          NVARCHAR(200) NULL,
    CONSTRAINT PK_change_log PRIMARY KEY (id),
    CONSTRAINT FK_change_order FOREIGN KEY (order_id) REFERENCES dbo.[order](id),
    CONSTRAINT FK_change_old FOREIGN KEY (old_schedule_id) REFERENCES dbo.schedule(id),
    CONSTRAINT FK_change_new FOREIGN KEY (new_schedule_id) REFERENCES dbo.schedule(id),
    CONSTRAINT FK_change_operator FOREIGN KEY (operator_id) REFERENCES dbo.[user](id)
);
GO

CREATE TABLE dbo.payment_log (
    id              INT IDENTITY(1,1) NOT NULL,
    order_id        INT           NOT NULL,
    order_no        VARCHAR(30)   NULL,
    type            NVARCHAR(20)  NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    direction       VARCHAR(3)    NOT NULL,
    remark          NVARCHAR(200) NULL,
    create_time     DATETIME      NOT NULL CONSTRAINT DF_paylog_create DEFAULT (GETDATE()),
    CONSTRAINT PK_payment_log PRIMARY KEY (id),
    CONSTRAINT CK_paylog_type CHECK (type IN (N'支付',N'退票退款',N'改签补款',N'改签退款')),
    CONSTRAINT CK_paylog_amount CHECK (amount > 0),
    CONSTRAINT CK_paylog_dir CHECK (direction IN ('in','out')),
    CONSTRAINT FK_paylog_order FOREIGN KEY (order_id) REFERENCES dbo.[order](id)
);
GO

CREATE INDEX IX_paylog_order ON dbo.payment_log(order_id);
CREATE INDEX IX_ticket_order ON dbo.ticket(order_id);
CREATE INDEX IX_change_order ON dbo.change_log(order_id);
CREATE INDEX IX_order_schedule ON dbo.[order](schedule_id);
CREATE INDEX IX_order_passenger ON dbo.[order](passenger_id);
CREATE INDEX IX_order_seller ON dbo.[order](seller_id);
CREATE INDEX IX_order_status_id ON dbo.[order](status, id);
CREATE INDEX IX_schedule_route ON dbo.schedule(route_id);
CREATE INDEX IX_user_role_id ON dbo.[user](role, id);
CREATE INDEX IX_user_role_idcard ON dbo.[user](role, id_card);
GO

CREATE VIEW dbo.v_seller_performance AS
SELECT u.id AS seller_id,
       u.real_name AS seller_name,
       COUNT(CASE WHEN o.status IN (N'已支付',N'已检票',N'已完成',N'已退票') THEN 1 END) AS paid_orders,
       ISNULL(SUM(CASE WHEN o.status IN (N'已支付',N'已检票',N'已完成') THEN o.total_amount
                       WHEN o.status=N'已退票' THEN o.total_amount-ISNULL(o.refund_amount,0)
                       ELSE 0 END),0) AS total_amount
FROM dbo.[user] u
LEFT JOIN dbo.[order] o ON o.seller_id=u.id
WHERE u.role='seller'
GROUP BY u.id, u.real_name;
GO

PRINT N'FerryDB 表结构创建完成。下一步执行 db/seed.sql 导入演示数据。';
GO
