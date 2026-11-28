/*
 * 检票失败留痕迁移
 * 1) 移除 check_in(ticket_id) 的全量唯一约束
 * 2) 改为仅“成功”记录唯一，失败尝试可保存多条
 * 3) 增加检票时间索引
 */
SET NOCOUNT ON;

IF EXISTS (
    SELECT 1 FROM sys.key_constraints
    WHERE parent_object_id = OBJECT_ID(N'dbo.check_in')
      AND name = N'UQ_check_ticket'
)
BEGIN
    ALTER TABLE dbo.check_in DROP CONSTRAINT UQ_check_ticket;
END
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.check_in')
      AND name = N'UX_check_success_ticket'
)
BEGIN
    CREATE UNIQUE INDEX UX_check_success_ticket
        ON dbo.check_in(ticket_id)
        WHERE status = N'成功';
END
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.check_in')
      AND name = N'IX_check_time'
)
BEGIN
    CREATE INDEX IX_check_time ON dbo.check_in(check_time);
END
GO

PRINT N'检票失败留痕迁移完成。';
