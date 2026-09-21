#!/usr/bin/env bash
# Применяет схему и демо-данные к контейнеру aicam-mssql.
# Запускать после `docker compose -f docker/docker-compose.yml up -d`.
set -euo pipefail

CONTAINER=aicam-mssql
SA_PASSWORD=${MSSQL_SA_PASSWORD:-Str0ng_SA_Passw0rd!}
SQLCMD=/opt/mssql-tools18/bin/sqlcmd

cd "$(dirname "$0")"

echo "Жду готовности SQL Server..."
for i in $(seq 1 60); do
    if docker exec "$CONTAINER" "$SQLCMD" -S localhost -U sa -P "$SA_PASSWORD" -C -Q "SELECT 1" >/dev/null 2>&1; then
        echo "SQL Server готов."
        break
    fi
    if [ "$i" = 60 ]; then
        echo "SQL Server не поднялся за 120 секунд." >&2
        exit 1
    fi
    sleep 2
done

for f in init/*.sql; do
    echo "Применяю $f"
    docker exec -i "$CONTAINER" "$SQLCMD" -S localhost -U sa -P "$SA_PASSWORD" -C -b < "$f"
done

echo "Готово."
