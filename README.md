# AI-Cam — Schedule Server (Java)

Spring Boot сервер-мост между Telegram-ботом и C++ сервером распознавания.
Принимает запрос на поиск свободного кабинета, ходит в MS SQL за расписанием
и в C++ сервер по TCP за статусом камер, возвращает результат боту.

Остальные компоненты живут в своих репозиториях:
- Telegram-бот — [AI-Cam-Bot](https://github.com/qwerwettest/AI-Cam-Bot)
- C++ сервер и сайт — отдельные репозитории

## Стек

| | |
|---|---|
| Язык | Java 17 |
| Фреймворк | Spring Boot 3.2.2 (Web, JDBC) |
| БД | Microsoft SQL Server (mssql-jdbc 12.6.2) |
| Логирование | Log4j2 |
| Сборка | Maven |
| Тесты | JUnit 5 |

Подробная карта технологий по файлам — в [technologies.txt](technologies.txt).

## Требования

- JDK 17 или новее (проверено на JDK 17 и JDK 25)
- Maven 3.8+
- Microsoft SQL Server с базой `hacaton` — см. «Локальная БД в Docker» ниже
- Запущенный C++ сервер — нужен только для поиска через `/api/bridge`;
  health-check `GET /api/bridge` отвечает и без него

## Локальная БД в Docker

В каталоге [docker/](docker/) лежит готовое окружение: MS SQL 2022, схема и
демо-данные.

```bash
docker compose -f docker/docker-compose.yml up -d
```

```bash
./docker/init-db.sh
```

Скрипт дожидается готовности сервера и применяет [01-schema.sql](docker/init/01-schema.sql)
(три таблицы и пользователь `app_user`) и [02-seed.sql](docker/init/02-seed.sql)
(10 аудиторий, 6 занятий, 5 камер). Значения по умолчанию в
`application.properties` подходят к этому окружению без правок.

Схема выведена из фактических запросов Java и C++ — обе системы работают с этими
таблицами одновременно.

## Запуск локально

```bash
git clone https://github.com/qwerwettest/AI-Cam.git
```

```bash
cd AI-Cam && mvn -s settings-central.xml spring-boot:run
```

Сервер поднимется на `http://localhost:3333`.

### Про `-s settings-central.xml`

Флаг нужен, если в `~/.m2/settings.xml` прописан внутренний корпоративный Nexus —
он перекрывает Maven Central, и зависимости не скачиваются вне рабочей сети.
Если такого конфига нет, флаг можно опустить:

```bash
mvn spring-boot:run
```

### Сборка jar

```bash
mvn -s settings-central.xml clean package
```

```bash
java -jar target/schedule-server-1.0.0.jar
```

### Тесты

```bash
mvn -s settings-central.xml test
```

TCP-клиент тестируется против встроенного mock-сервера, внешние сервисы не нужны.

## Конфигурация

Все значения в [application.properties](src/main/resources/application.properties)
переопределяются переменными окружения — значения по умолчанию подходят для локальной разработки.

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `SERVER_PORT` | `3333` | порт HTTP-сервера |
| `DB_URL` | `jdbc:sqlserver://localhost:1433;databaseName=hacaton;...` | строка подключения к MS SQL |
| `DB_USERNAME` | `app_user` | пользователь БД |
| `DB_PASSWORD` | `StrongPass_123!` | пароль БД |
| `CPP_SERVER_HOST` | `192.168.7.14` | хост C++ сервера |
| `CPP_SERVER_PORT` | `2222` | TCP-порт C++ сервера |

Пример запуска с другими настройками:

```bash
DB_PASSWORD=secret CPP_SERVER_HOST=127.0.0.1 mvn -s settings-central.xml spring-boot:run
```

> Дефолтный пароль — от локальной хакатонной БД. Для любого не-локального
> развёртывания задавайте `DB_PASSWORD` через окружение и смените пароль в самой БД.

## API

### Мост для бота

| Метод | Путь | Описание |
|---|---|---|
| `POST` | `/api/bridge` | поиск свободных кабинетов: запрос уходит в C++ по TCP |
| `GET` | `/api/bridge` | health-check моста |

### Расписание

| Метод | Путь | Описание |
|---|---|---|
| `POST` | `/api/schedule/upload` | загрузка JSON-файла расписания (до 50 МБ) |
| `GET` | `/api/schedule/auditories` | список аудиторий |
| `GET` | `/api/schedule/journal` | журнал занятости |
| `GET` | `/api/schedule/journal/{audId}` | журнал по конкретной аудитории |

Быстрая проверка, что сервер жив:

```bash
curl http://localhost:3333/api/bridge
```

## Протокол обмена с C++ сервером

TCP, формат кадра: **4 байта big-endian длины + JSON в UTF-8**.
Запросы идут через FIFO-очередь — одновременно обрабатывается ровно один,
общий таймаут 30 секунд. Реализация — [CppTcpClient.java](src/main/java/com/schedule/server/tcp/CppTcpClient.java).

Запрос:

```json
{
  "id": 1,
  "start_time": "10:30",
  "duration": 90,
  "corpus": "Главный"
}
```

## Структура

```text
.
├── pom.xml
├── settings-central.xml        # Maven-конфиг с Maven Central (обход корпоративного Nexus)
├── technologies.txt
└── src
    ├── main
    │   ├── java
    │   │   ├── com/schedule/server
    │   │   │   ├── config      # CORS, обработка ошибок, логирование запросов
    │   │   │   ├── controller  # REST: ScheduleController, BotBridgeController
    │   │   │   ├── dto
    │   │   │   ├── service     # ScheduleService, BotBridgeService, ScheduleLookupService
    │   │   │   ├── tcp         # CppTcpClient — мост к C++ серверу
    │   │   │   └── util        # TimeUtil (Asia/Almaty)
    │   │   └── kvt             # доступ к БД: репозитории и модели
    │   └── resources           # application.properties, log4j2.xml
    └── test
        └── java/com/schedule/server/tcp
```

## Известные особенности

- Spring Data JPA подключён в `pom.xml`, но автоконфигурация отключена
  в `ScheduleServerApplication` — работа с БД идёт через `JdbcTemplate`.
- Версия Lombok переопределена на 1.18.42: версия из Spring Boot 3.2.2 (1.18.30)
  не работает на JDK 21+.
- В `pom.xml` явно задан `annotationProcessorPaths` для Lombok — начиная с JDK 23
  javac не ищет annotation-процессоры в classpath, и без этого блока
  `@Slf4j`/`@Data` молча не раскрываются.
