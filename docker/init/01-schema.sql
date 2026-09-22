/*
  Схема БД проекта AI-Cam.

  Колонки выведены из фактических запросов обоих потребителей:
    - Java:  kvt/db/AuditoryRepository, AuditoryJournalRepository, CameraCabJournalRepository
    - C++:   DatabaseManager/Requests/AlgorithmRequests.h, DatabaseManager.cpp
*/

IF DB_ID('hacaton') IS NULL
    CREATE DATABASE hacaton;
GO

USE hacaton;
GO

-- ==================== dbo.auditory ====================
IF OBJECT_ID('dbo.auditory', 'U') IS NULL
CREATE TABLE dbo.auditory (
    id       INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    name     NVARCHAR(100) NULL,
    number   INT           NULL,
    corpus   NVARCHAR(100) NULL,
    category NVARCHAR(100) NULL
);
GO

-- ==================== dbo.auditory_journal ====================
-- timeStatus: 0/1/2 — участвуют в проверке занятости (см. AlgorithmRequests.h).
-- 1 — временная бронь, её чистит CleanTemporaryCabinetRequest.
IF OBJECT_ID('dbo.auditory_journal', 'U') IS NULL
CREATE TABLE dbo.auditory_journal (
    id         INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    aud_id     INT  NOT NULL,
    dayOfWeek  INT  NOT NULL,
    startTime  TIME NULL,
    endTime    TIME NULL,
    duration   INT  NULL,
    timeStatus INT  NULL,
    -- Кто забронировал. Заполняет Java после ответа C++: сам C++ про
    -- пользователей не знает и эту колонку не трогает.
    -- NULL — занятие по расписанию, а не пользовательская бронь.
    telegram_user_id BIGINT NULL,
    -- Момент создания: по нему снимаются неподтверждённые резервы.
    created_at DATETIME2 NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_auditory_journal_auditory
        FOREIGN KEY (aud_id) REFERENCES dbo.auditory(id)
);
GO

-- Миграция для баз, созданных до появления колонок.
IF COL_LENGTH('dbo.auditory_journal', 'telegram_user_id') IS NULL
    ALTER TABLE dbo.auditory_journal ADD telegram_user_id BIGINT NULL;
GO

-- Момент создания записи. Нужен, чтобы снимать по таймауту временные
-- резервы (timeStatus = 2), которые пользователь не подтвердил.
IF COL_LENGTH('dbo.auditory_journal', 'created_at') IS NULL
    ALTER TABLE dbo.auditory_journal ADD created_at DATETIME2 NULL
        CONSTRAINT DF_auditory_journal_created_at DEFAULT SYSDATETIME();
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_auditory_journal_user')
    CREATE INDEX IX_auditory_journal_user
        ON dbo.auditory_journal (telegram_user_id);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_auditory_journal_lookup')
    CREATE INDEX IX_auditory_journal_lookup
        ON dbo.auditory_journal (aud_id, dayOfWeek, startTime, endTime);
GO

-- ==================== dbo.camera_cab_journal ====================
-- is_busy читает только C++ (0 или NULL = кабинет считается свободным по камере).
IF OBJECT_ID('dbo.camera_cab_journal', 'U') IS NULL
CREATE TABLE dbo.camera_cab_journal (
    id              INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    camera_ip       NVARCHAR(64) NULL,
    id_cab          INT          NULL,
    login_camera    NVARCHAR(64) NULL,
    password_camera NVARCHAR(64) NULL,
    port_camera     NVARCHAR(16) NULL,
    is_busy         BIT          NULL DEFAULT 0,
    CONSTRAINT FK_camera_cab_journal_auditory
        FOREIGN KEY (id_cab) REFERENCES dbo.auditory(id)
);
GO

-- ==================== Учётка приложения ====================
-- Совпадает с дефолтами application.properties (DB_USERNAME / DB_PASSWORD).
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'app_user')
    CREATE LOGIN app_user WITH PASSWORD = 'StrongPass_123!', CHECK_POLICY = OFF;
GO

USE hacaton;
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'app_user')
    CREATE USER app_user FOR LOGIN app_user;
GO

ALTER ROLE db_owner ADD MEMBER app_user;
GO
