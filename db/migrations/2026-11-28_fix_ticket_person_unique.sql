/*
 * P0：允许退票/取消待支付订单后重新购买同一班次。
 * 旧约束 UNIQUE(id_card, schedule_id) 会让历史退票记录永久占用购票名额；
 * 新约束只覆盖“待支付/已支付/已检票”三种有效票。
 *
 * 执行前请备份 FerryDB。本脚本不删除业务数据；
 * 如果当前库已存在同一证件的多条有效票，脚本会报错中止，需要人工处理后再执行。
 */
USE FerryDB;
GO

SET NOCOUNT ON;
GO

IF EXISTS (
    SELECT 1
    FROM dbo.ticket
    WHERE status IN (N'待支付', N'已支付', N'已检票')
    GROUP BY id_card, schedule_id
    HAVING COUNT_BIG(*) > 1
)
BEGIN
    THROW 51000, N'存在同一证件同一班次的多条有效票，请先人工核对，迁移已中止。', 1;
END
GO

IF EXISTS (
    SELECT 1
    FROM sys.objects
    WHERE parent_object_id = OBJECT_ID(N'dbo.ticket')
      AND name = N'UQ_ticket_person_sched'
      AND type = 'UQ'
)
BEGIN
    ALTER TABLE dbo.ticket DROP CONSTRAINT UQ_ticket_person_sched;
END
GO

-- 兼容历史上“用普通唯一索引代替约束”的库：先移除同名旧索引。
IF EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.ticket')
      AND name = N'UQ_ticket_person_sched'
)
BEGIN
    DROP INDEX UQ_ticket_person_sched ON dbo.ticket;
END
GO

IF EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.ticket')
      AND name = N'UX_ticket_active_person_sched'
)
BEGIN
    DROP INDEX UX_ticket_active_person_sched ON dbo.ticket;
END
GO

CREATE UNIQUE INDEX UX_ticket_active_person_sched
    ON dbo.ticket(id_card, schedule_id)
    WHERE status IN (N'待支付', N'已支付', N'已检票');
GO

PRINT N'P0 迁移完成：票务唯一约束已改为只约束有效票，退票/取消后可重新购买同一班次。';
GO
