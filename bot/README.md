# Telegram BOT -> Java API (HTTP/JSON)

## Архитектура
Бот реализован на `aiogram v3` и работает асинхронно (`asyncio`). Telegram-часть отвечает только за UX (команды, FSM-диалог, inline-кнопки), валидацию пользовательского ввода и форматирование ответа. Бизнес-решение о доступности кабинетов остается на стороне Java/C++.

Интеграция с Java вынесена в `JavaClient` на `httpx` с таймаутами, ретраями и backoff. Настройки и последние запросы пользователей хранятся в JSON storage, а логи пишутся в structured JSON формат (stdout + `logs/bot.log`). Это позволяет быстро запускать проект локально и удобно наблюдать интеграционные ошибки.

## Структура проекта
```text
.
├── app
│   ├── __init__.py
│   ├── bot.py
│   ├── config.py
│   ├── models.py
│   ├── handlers
│   │   ├── __init__.py
│   │   ├── admin.py
│   │   ├── common.py
│   │   └── find.py
│   ├── keyboards
│   │   ├── __init__.py
│   │   └── menus.py
│   ├── services
│   │   ├── __init__.py
│   │   ├── formatter.py
│   │   └── java_client.py
│   ├── storage
│   │   ├── __init__.py
│   │   └── user_storage.py
│   └── utils
│       ├── __init__.py
│       ├── logging.py
│       └── validation.py
├── data
├── logs
├── .env.example
├── Dockerfile
├── main.py
└── requirements.txt
```

## Требования
- Python `3.11+`
- Telegram Bot Token
- Доступный Java API

## Настройка и запуск локально
1. Установите Python 3.11+.
2. Создайте виртуальное окружение:
   ```bash
   python -m venv .venv
   ```
3. Активируйте окружение:
   - Windows PowerShell:
     ```powershell
     .\.venv\Scripts\Activate.ps1
     ```
   - Linux/macOS:
     ```bash
     source .venv/bin/activate
     ```
4. Установите зависимости:
   ```bash
   pip install -r requirements.txt
   ```
5. Создайте `.env` из `.env.example` и заполните значения:
   ```bash
   cp .env.example .env
   ```
6. Запустите бота:
   ```bash
   python main.py
   ```

## Пример минимального `.env`
Ниже пример с валидным JSON (можно взять за основу):

```env
TELEGRAM_BOT_TOKEN=123456789:AAAA_BBBBB_CCCCC_DDDDD
JAVA_BASE_URL=http://localhost:8080
JAVA_API_PATHS={"free_rooms":"/api/free-rooms","room_status":"/api/room-status","health":"/api/health"}
JAVA_AUTH_SCHEME=api_key
JAVA_AUTH_SECRET=super-secret-key
JAVA_API_KEY_HEADER=X-API-Key
REQUEST_JSON_SCHEMA={"location_id":"string","start_at":"ISO-8601","duration_minutes":"int","filters":{"min_capacity":"int","need_projector":"bool"}}
RESPONSE_JSON_SCHEMA={"free_rooms":"array","alternatives":"array","reason":"string","camera_status":"string"}
LOCATIONS_LIST=[{"id":"corp_a","name":"Корпус A","floors":[1,2,3,4]},{"id":"corp_b","name":"Корпус B","floors":[1,2,3]}]
SEARCH_FILTERS={"duration_options":[30,60,90,120],"common_times":["09:00","10:30","12:00","14:00","16:00"]}
ADMIN_TELEGRAM_IDS=111111111,222222222
REQUEST_TIMEOUT_SECONDS=8
MAX_RETRIES=3
RETRY_BACKOFF_BASE=0.6
STORAGE_PATH=data/users.json
LOG_PATH=logs/bot.log
LOG_LEVEL=INFO
```

## Реализованные команды
- `/start` - приветствие и быстрый старт.
- `/help` - список команд и примеры.
- `/find` - FSM-диалог поиска (локация -> этаж -> дата -> время -> длительность -> фильтры).
- `/setdefault` - сохранение локации по умолчанию пользователя.
- `/status` - health-check Java API (только для админов).
- `/logs [N]` - последние N строк логов (только для админов).
- `/cancel` - отменить текущий сценарий `/find`.

Кнопки в результате:
- `🔄 Обновить` - повторяет последний запрос пользователя к Java API.
- `ℹ️ <кабинет>` - показывает детали выбранного кабинета.

## Формат запроса к Java (mock)
Бот отправляет `POST` на `JAVA_API_PATHS.free_rooms`:

```json
{
  "location_id": "corp_a",
  "floor": 2,
  "start_at": "2026-02-17T14:30:00",
  "duration_minutes": 60,
  "requested_by": {
    "telegram_user_id": 123456789
  },
  "filters": {
    "min_capacity": 20,
    "need_projector": true
  }
}
```

## Формат ответа от Java (mock)
```json
{
  "free_rooms": [
    {
      "id": "A-204",
      "name": "A-204",
      "location_id": "corp_a",
      "floor": 2,
      "capacity": 25,
      "schedule_free": true,
      "camera_free": true,
      "camera_status": "online",
      "access_code": "KEY-204"
    }
  ],
  "alternatives": [
    {
      "id": "A-210",
      "name": "A-210",
      "location_id": "corp_a",
      "floor": 2,
      "capacity": 18,
      "schedule_free": true,
      "camera_status": "camera_unavailable"
    }
  ],
  "reason": ""
}
```

Если поле по камере отсутствует или камера недоступна, бот показывает статус камеры отдельно и не скрывает результат по расписанию.

## Проверка, что бот видит Java (`/health`)
1. Убедитесь, что Java API запущен.
2. Отправьте админом команду:
   ```text
   /status
   ```
3. Если всё в порядке, бот вернет содержимое `JAVA_API_PATHS.health`.
4. Если Java недоступен, бот вернет ошибку и запишет детали в `logs/bot.log`.

## Логирование и ошибки
- Structured JSON logs в stdout и файл `logs/bot.log`.
- Для API-ошибок Java пишутся отдельные события:
  - `java_api_error`
  - `java_request_retry_network`
  - `java_request_retry_status`
  - `java_unavailable_after_retries`

## Docker (опционально)
```bash
docker build -t telegram-room-bot .
docker run --env-file .env telegram-room-bot
```

## systemd unit (опционально)
```ini
[Unit]
Description=Telegram Room Finder Bot
After=network.target

[Service]
Type=simple
WorkingDirectory=/opt/telegramBOT
EnvironmentFile=/opt/telegramBOT/.env
ExecStart=/opt/telegramBOT/.venv/bin/python /opt/telegramBOT/main.py
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

