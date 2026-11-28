/*
 * 管理员账号新增/编辑/状态变更/密码重置的审计日志。
 * 只新增审计表和索引，不修改现有用户数据。
 */
USE FerryDB;
GO

IF OBJECT_ID(N'dbo.user_admin_log', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.user_admin_log (
        id              INT IDENTITY(1,1) NOT NULL,
        operator_id     INT           NOT NULL,
        target_user_id  INT           NOT NULL,
        action          NVARCHAR(20)  NOT NULL,
        detail          NVARCHAR(500) NULL,
        create_time     DATETIME      NOT NULL
            CONSTRAINT DF_user_admin_log_time DEFAULT (GETDATE()),
        CONSTRAINT PK_user_admin_log PRIMARY KEY (id),
        CONSTRAINT CK_user_admin_action
            CHECK (action IN (N'新增',N'编辑',N'重置密码',N'启用',N'禁用')),
        CONSTRAINT FK_user_admin_operator
            FOREIGN KEY (operator_id) REFERENCES dbo.[user](id),
        CONSTRAINT FK_user_admin_target
            FOREIGN KEY (target_user_id) REFERENCES dbo.[user](id)
    );
END;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes
               WHERE name='IX_user_admin_log_time'
                 AND object_id=OBJECT_ID(N'dbo.user_admin_log'))
    CREATE INDEX IX_user_admin_log_time ON dbo.user_admin_log(create_time, id);
GO

PRINT N'用户管理审计表迁移完成。';
GO
