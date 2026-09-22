/*
  Демо-данные для локального запуска.

  Значения corpus обязаны совпадать с маппингом LOCATION_TO_CORPUS
  в com.schedule.server.service.BotBridgeService:
      corp_a -> Корпус А
      corp_b -> Корпус Б
      corp_c -> Корпус С

  Этаж Java вычисляет как number / 100, поэтому номера трёхзначные.
*/

USE hacaton;
GO

IF NOT EXISTS (SELECT 1 FROM dbo.auditory)
BEGIN
    INSERT INTO dbo.auditory (name, number, corpus, category) VALUES
        (N'С-101', 101, N'Корпус С',  N'Лекционная'),
        (N'С-102', 102, N'Корпус С',  N'Лаборатория'),
        (N'С-201', 201, N'Корпус С',  N'Лекционная'),
        (N'С-202', 202, N'Корпус С',  N'Компьютерный класс'),
        (N'С-301', 301, N'Корпус С',  N'Лекционная'),
        (N'А-101', 101, N'Корпус А', N'Лекционная'),
        (N'А-201', 201, N'Корпус А', N'Компьютерный класс'),
        (N'А-202', 202, N'Корпус А', N'Лаборатория'),
        (N'Б-101', 101, N'Корпус Б', N'Лекционная'),
        (N'Б-301', 301, N'Корпус Б', N'Лаборатория');
END
GO

-- Занятия по расписанию: timeStatus = 0 (постоянная занятость).
-- dayOfWeek: 1 = понедельник ... 7 = воскресенье.
IF NOT EXISTS (SELECT 1 FROM dbo.auditory_journal)
BEGIN
    INSERT INTO dbo.auditory_journal (aud_id, dayOfWeek, startTime, endTime, duration, timeStatus)
    SELECT a.id, d.dayOfWeek, d.startTime, d.endTime, d.duration, 0
    FROM dbo.auditory a
    JOIN (VALUES
        (N'С-101', 1, CAST('09:00' AS TIME), CAST('10:30' AS TIME), 90),
        (N'С-101', 1, CAST('11:00' AS TIME), CAST('12:30' AS TIME), 90),
        (N'С-102', 1, CAST('09:00' AS TIME), CAST('12:30' AS TIME), 210),
        (N'С-201', 2, CAST('14:00' AS TIME), CAST('15:30' AS TIME), 90),
        (N'А-101', 1, CAST('08:00' AS TIME), CAST('09:30' AS TIME), 90),
        (N'Б-101', 3, CAST('10:00' AS TIME), CAST('11:30' AS TIME), 90)
    ) AS d(name, dayOfWeek, startTime, endTime, duration)
      ON a.name = d.name;
END
GO

-- Камеры: свободны (is_busy = 0). Часть кабинетов намеренно без камеры —
-- алгоритм C++ трактует отсутствие записи как "камера не мешает".
IF NOT EXISTS (SELECT 1 FROM dbo.camera_cab_journal)
BEGIN
    INSERT INTO dbo.camera_cab_journal (camera_ip, id_cab, login_camera, password_camera, port_camera, is_busy)
    SELECT N'192.168.1.' + CAST(100 + a.id AS NVARCHAR(8)), a.id, N'admin', N'camera_pass', N'554', 0
    FROM dbo.auditory a
    WHERE a.name IN (N'С-101', N'С-201', N'А-101', N'А-201', N'Б-101');
END
GO

SELECT 'auditory' AS tbl, COUNT(*) AS cnt FROM dbo.auditory
UNION ALL SELECT 'auditory_journal', COUNT(*) FROM dbo.auditory_journal
UNION ALL SELECT 'camera_cab_journal', COUNT(*) FROM dbo.camera_cab_journal;
GO
