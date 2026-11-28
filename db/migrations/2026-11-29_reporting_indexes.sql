/*
 * 统计报表日期筛选与售票员业绩查询索引。
 * 执行顺序：先停应用，再执行本脚本，最后重新构建并启动。
 */
USE FerryDB;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name='IX_order_create_time'
                 AND object_id=OBJECT_ID(N'dbo.[order]'))
    CREATE INDEX IX_order_create_time ON dbo.[order](create_time, status, seller_id)
        INCLUDE (schedule_id, total_amount, refund_amount);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name='IX_order_refund_time'
                 AND object_id=OBJECT_ID(N'dbo.[order]'))
    CREATE INDEX IX_order_refund_time ON dbo.[order](refund_time)
        INCLUDE (status, schedule_id, total_amount, refund_amount)
        WHERE refund_time IS NOT NULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name='IX_schedule_depart_status'
                 AND object_id=OBJECT_ID(N'dbo.schedule'))
    CREATE INDEX IX_schedule_depart_status ON dbo.schedule(depart_time, route_id)
        INCLUDE (total_seats, sold_seats, status)
        WHERE is_deleted = 0;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name='IX_check_seller_time'
                 AND object_id=OBJECT_ID(N'dbo.check_in'))
    CREATE INDEX IX_check_seller_time ON dbo.check_in(seller_id, check_time, status);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name='IX_ticket_status_schedule'
                 AND object_id=OBJECT_ID(N'dbo.ticket'))
    CREATE INDEX IX_ticket_status_schedule ON dbo.ticket(status, schedule_id)
        INCLUDE (order_id);
GO

PRINT N'统计报表索引迁移完成。';
GO
