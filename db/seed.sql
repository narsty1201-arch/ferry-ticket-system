/*
 * FerryDB 演示数据
 * 先执行 schema.sql，再执行本脚本。
 * 所有账号密码均为 123456。
 */
USE FerryDB;
GO

SET NOCOUNT ON;

IF NOT EXISTS (SELECT 1 FROM dbo.[user] WHERE username='admin')
    INSERT INTO dbo.[user](username,password,role,real_name,phone,id_card,status)
    VALUES ('admin','e10adc3949ba59abbe56e057f20f883e','admin',N'系统管理员','13800000001','110101199001010011','normal');
IF NOT EXISTS (SELECT 1 FROM dbo.[user] WHERE username='seller1')
    INSERT INTO dbo.[user](username,password,role,real_name,phone,id_card,status)
    VALUES ('seller1','e10adc3949ba59abbe56e057f20f883e','seller',N'张三','13800000002','110101199202020022','normal');
IF NOT EXISTS (SELECT 1 FROM dbo.[user] WHERE username='seller2')
    INSERT INTO dbo.[user](username,password,role,real_name,phone,id_card,status)
    VALUES ('seller2','e10adc3949ba59abbe56e057f20f883e','seller',N'李四','13800000003','110101199303030033','normal');
IF NOT EXISTS (SELECT 1 FROM dbo.[user] WHERE username='pass1')
    INSERT INTO dbo.[user](username,password,role,real_name,phone,id_card,status)
    VALUES ('pass1','e10adc3949ba59abbe56e057f20f883e','passenger',N'王五一','13800000004','320681201504027538','normal');
IF NOT EXISTS (SELECT 1 FROM dbo.[user] WHERE username='pass2')
    INSERT INTO dbo.[user](username,password,role,real_name,phone,id_card,status)
    VALUES ('pass2','e10adc3949ba59abbe56e057f20f883e','passenger',N'赵六','13800000005','110101199505050055','normal');
GO

IF NOT EXISTS (SELECT 1 FROM dbo.route WHERE route_name=N'机场快线')
    INSERT INTO dbo.route(route_name,start_port,end_port,duration_min,description)
    VALUES (N'机场快线',N'码头A',N'码头B',90,N'码头与机场之间的快速航线');
IF NOT EXISTS (SELECT 1 FROM dbo.route WHERE route_name=N'海岛观光')
    INSERT INTO dbo.route(route_name,start_port,end_port,duration_min,description)
    VALUES (N'海岛观光',N'码头A',N'码头C',120,N'观光航线');
IF NOT EXISTS (SELECT 1 FROM dbo.route WHERE route_name=N'夜航专线')
    INSERT INTO dbo.route(route_name,start_port,end_port,duration_min,description)
    VALUES (N'夜航专线',N'码头B',N'码头A',100,N'夜间航线');
GO

IF NOT EXISTS (SELECT 1 FROM dbo.ship WHERE ship_name=N'海鸥号')
    INSERT INTO dbo.ship(ship_name,total_seats,ship_type,description)
    VALUES (N'海鸥号',100,N'普通船',N'日常通勤船');
IF NOT EXISTS (SELECT 1 FROM dbo.ship WHERE ship_name=N'远航号')
    INSERT INTO dbo.ship(ship_name,total_seats,ship_type,description)
    VALUES (N'远航号',120,N'高速船',N'高速客船');
IF NOT EXISTS (SELECT 1 FROM dbo.ship WHERE ship_name=N'蓝鲸号')
    INSERT INTO dbo.ship(ship_name,total_seats,ship_type,description)
    VALUES (N'蓝鲸号',60,N'豪华船',N'观光客船');
GO

/* 为每艘船补齐 ship_seat（已有编号不会重复插入） */
DECLARE @shipId INT, @shipSeats INT, @j INT, @seatNo VARCHAR(10);
DECLARE ship_cur CURSOR FOR SELECT id,total_seats FROM dbo.ship WHERE is_deleted=0;
OPEN ship_cur;
FETCH NEXT FROM ship_cur INTO @shipId,@shipSeats;
WHILE @@FETCH_STATUS=0
BEGIN
    SET @j=1;
    WHILE @j<=@shipSeats
    BEGIN
        SET @seatNo = CHAR(65 + (@j-1)/10) + CAST(((@j-1)%10)+1 AS VARCHAR(2));
        IF NOT EXISTS (SELECT 1 FROM dbo.ship_seat WHERE ship_id=@shipId AND seat_no=@seatNo)
            INSERT INTO dbo.ship_seat(ship_id,seat_no,seat_type)
            VALUES (@shipId,@seatNo,N'普通座');
        SET @j=@j+1;
    END
    FETCH NEXT FROM ship_cur INTO @shipId,@shipSeats;
END
CLOSE ship_cur;
DEALLOCATE ship_cur;
GO

DECLARE @dep DATETIME;
DECLARE @base DATETIME = CAST(GETDATE() AS DATE);
DECLARE @r1 INT = (SELECT id FROM dbo.route WHERE route_name=N'机场快线');
DECLARE @r2 INT = (SELECT id FROM dbo.route WHERE route_name=N'海岛观光');
DECLARE @r3 INT = (SELECT id FROM dbo.route WHERE route_name=N'夜航专线');
DECLARE @sh1 INT = (SELECT id FROM dbo.ship WHERE ship_name=N'海鸥号');
DECLARE @sh2 INT = (SELECT id FROM dbo.ship WHERE ship_name=N'远航号');
DECLARE @sh3 INT = (SELECT id FROM dbo.ship WHERE ship_name=N'蓝鲸号');

SET @dep = DATEADD(HOUR,8,DATEADD(DAY,2,@base));
IF @r1 IS NOT NULL AND @sh1 IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.schedule WHERE ship_id=@sh1 AND depart_time=@dep)
    INSERT INTO dbo.schedule(route_id,ship_id,depart_time,arrive_time,price,total_seats,status)
    SELECT @r1,@sh1,@dep,DATEADD(MINUTE,duration_min,@dep),80,100,N'售票中'
    FROM dbo.route WHERE id=@r1;

SET @dep = DATEADD(MINUTE,630,DATEADD(DAY,2,@base));
IF @r1 IS NOT NULL AND @sh2 IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.schedule WHERE ship_id=@sh2 AND depart_time=@dep)
    INSERT INTO dbo.schedule(route_id,ship_id,depart_time,arrive_time,price,total_seats,status)
    SELECT @r1,@sh2,@dep,DATEADD(MINUTE,duration_min,@dep),80,120,N'售票中'
    FROM dbo.route WHERE id=@r1;

SET @dep = DATEADD(MINUTE,840,DATEADD(DAY,3,@base));
IF @r2 IS NOT NULL AND @sh3 IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.schedule WHERE ship_id=@sh3 AND depart_time=@dep)
    INSERT INTO dbo.schedule(route_id,ship_id,depart_time,arrive_time,price,total_seats,status)
    SELECT @r2,@sh3,@dep,DATEADD(MINUTE,duration_min,@dep),120,60,N'售票中'
    FROM dbo.route WHERE id=@r2;

SET @dep = DATEADD(MINUTE,1140,DATEADD(DAY,4,@base));
IF @r3 IS NOT NULL AND @sh1 IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM dbo.schedule WHERE ship_id=@sh1 AND depart_time=@dep)
    INSERT INTO dbo.schedule(route_id,ship_id,depart_time,arrive_time,price,total_seats,status)
    SELECT @r3,@sh1,@dep,DATEADD(MINUTE,duration_min,@dep),60,100,N'售票中'
    FROM dbo.route WHERE id=@r3;
GO

/* 为每个班次补齐 seat（已有编号不会重复插入） */
DECLARE @schedId INT, @schedSeats INT, @k INT, @seatNo2 VARCHAR(10);
DECLARE sched_cur CURSOR FOR SELECT id,total_seats FROM dbo.schedule WHERE is_deleted=0;
OPEN sched_cur;
FETCH NEXT FROM sched_cur INTO @schedId,@schedSeats;
WHILE @@FETCH_STATUS=0
BEGIN
    SET @k=1;
    WHILE @k<=@schedSeats
    BEGIN
        SET @seatNo2 = CHAR(65 + (@k-1)/10) + CAST(((@k-1)%10)+1 AS VARCHAR(2));
        IF NOT EXISTS (SELECT 1 FROM dbo.seat WHERE schedule_id=@schedId AND seat_no=@seatNo2)
            INSERT INTO dbo.seat(schedule_id,seat_no,seat_status)
            VALUES (@schedId,@seatNo2,N'可售');
        SET @k=@k+1;
    END
    FETCH NEXT FROM sched_cur INTO @schedId,@schedSeats;
END
CLOSE sched_cur;
DEALLOCATE sched_cur;
GO

SELECT N'用户' AS item, COUNT(*) AS total FROM dbo.[user]
UNION ALL SELECT N'航线', COUNT(*) FROM dbo.route
UNION ALL SELECT N'船舶', COUNT(*) FROM dbo.ship
UNION ALL SELECT N'班次', COUNT(*) FROM dbo.schedule
UNION ALL SELECT N'座位', COUNT(*) FROM dbo.seat;
GO
