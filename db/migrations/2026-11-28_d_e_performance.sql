/*
 * D+E：代客购票乘客账号关联、分页索引和连接池配套迁移。
 * 执行顺序：先停应用，再执行本脚本，最后重新构建并启动。
 */
USE FerryDB;
GO

IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id=OBJECT_ID(N'dbo.[order]')
      AND name=N'passenger_id'
      AND is_nullable=0
)
BEGIN
    ALTER TABLE dbo.[order] ALTER COLUMN passenger_id INT NULL;
END
GO

/*
 * 历史代客订单回填：按票面身份证匹配注册乘客。
 * user.id_card 没有唯一约束，因此用 OUTER APPLY + TOP 1 + ORDER BY u.id
 * 做确定性选择；匹配不到时写 NULL，表示“未注册乘客”。
 */
UPDATE o
SET passenger_id = matched.passenger_id
FROM dbo.[order] o
OUTER APPLY (
    SELECT TOP (1) u.id AS passenger_id
    FROM dbo.ticket t
    JOIN dbo.[user] u ON u.role='passenger'
                     AND UPPER(LTRIM(RTRIM(u.id_card))) = UPPER(LTRIM(RTRIM(t.id_card)))
    WHERE t.order_id = o.id
    ORDER BY u.id
) matched
WHERE o.seller_id IS NOT NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_order_passenger' AND object_id=OBJECT_ID(N'dbo.[order]'))
    CREATE INDEX IX_order_passenger ON dbo.[order](passenger_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_order_seller' AND object_id=OBJECT_ID(N'dbo.[order]'))
    CREATE INDEX IX_order_seller ON dbo.[order](seller_id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_order_status_id' AND object_id=OBJECT_ID(N'dbo.[order]'))
    CREATE INDEX IX_order_status_id ON dbo.[order](status, id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_user_role_id' AND object_id=OBJECT_ID(N'dbo.[user]'))
    CREATE INDEX IX_user_role_id ON dbo.[user](role, id);
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name='IX_user_role_idcard' AND object_id=OBJECT_ID(N'dbo.[user]'))
    CREATE INDEX IX_user_role_idcard ON dbo.[user](role, id_card);
GO

PRINT N'D+E 迁移完成：passenger_id 已可空，历史代客订单已按身份证确定性回填乘客账号。';
GO
