/*
 * 已有 FerryDB 的 A+B 优化迁移脚本
 * 作用：
 *   1. 把售票员业绩视图改为净收入口径（退款扣除实际退款金额）
 *   2. 补订单/船票/改签查询索引
 * 不修改任何业务数据。
 */
USE FerryDB;
GO

IF OBJECT_ID(N'dbo.v_seller_performance', N'V') IS NOT NULL
    DROP VIEW dbo.v_seller_performance;
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

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_ticket_order' AND object_id=OBJECT_ID(N'dbo.ticket'))
    CREATE INDEX IX_ticket_order ON dbo.ticket(order_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_change_order' AND object_id=OBJECT_ID(N'dbo.change_log'))
    CREATE INDEX IX_change_order ON dbo.change_log(order_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_order_schedule' AND object_id=OBJECT_ID(N'dbo.[order]'))
    CREATE INDEX IX_order_schedule ON dbo.[order](schedule_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_schedule_route' AND object_id=OBJECT_ID(N'dbo.schedule'))
    CREATE INDEX IX_schedule_route ON dbo.schedule(route_id);
GO

PRINT N'业务修复迁移完成。';
GO
