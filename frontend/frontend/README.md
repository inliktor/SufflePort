# Фронтенд (HTML + Tailwind + JS)

## Запуск разработки локально
```bash
cd frontend
npm install
npm run dev   # watch: пересобирает styles.css
```

## Сборка и копирование в backend (ручной)
```bash
npm run build
```
Скрипт перенесёт dist/assets/styles.css и src/index.html в src/main/resources/static.

## Docker (frontend + backend + postgres)
```bash
docker compose build
docker compose up -d
```
Доступ:
- Frontend: http://localhost:8080
- Backend API: http://localhost:8081/api/...
- Postgres: localhost:5432

## Структура
- src/css/input.css: точка входа Tailwind
- src/css/phone.css / desktop.css: адаптивные добавочные стили
- src/js/api.js: вызовы REST backend (`/api/...` проксируется nginx)
- src/js/ui.js: инициализация интерфейса
- src/index.html: страница панели

## Расширение
Добавляйте новые секции как `<section id="...">` и соответствующие init функции в ui.js.

## Proxy
Nginx проксирует `/api/` на контейнер backend (порт 8081). Из JS просто используйте `fetch('/api/...')`.
