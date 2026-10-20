## API Gateway, единая точка входа в систему. Через него внешние клиенты будут попадать в сервис FlowManager, 
## не зная внутренней топологии микросервисов.

## 📋 Задача 4: API Gateway
Создайте проект API Gateway.
Настройте его на работу с сервисом FlowManager: запросы клиента должны проходить через гейтвей и маршрутизироваться в FlowManager.

## 💡 Подсказка
Если гейтвей и сервисы уже зарегистрированы в Eureka, маршрут можно настроить на обращение по имени сервиса (например, lb://flow-manager), 
а не по жёстко прописанному адресу — тогда балансировка и переезды инстансов работают сами.

## 📋 Задача 5:  OAuth 2.0 и Keycloak

аутентификация и авторизация по протоколу OAuth 2.0 с помощью Keycloak. Защиту берёт на себя API Gateway: он будет и клиентом, и ресурсным сервером,
а сервисы за ним — доверять уже проверенным запросам.

## 📋 Задача
Добавьте аутентификацию и авторизацию пользователя по протоколу OAuth 2.0 с использованием Keycloak.
В роли ресурсного сервера и клиента должен выступать API Gateway.
Как это выглядит
Запрос клиента проходит через гейтвей; гейтвей проверяет токен в Keycloak и пропускает дальше во внутренние сервисы только аутентифицированные запросы:

## Схема
Клиент (браузер/фронтенд)
│  логин / токен
▼
┌──────────────────────┐        проверка/выдача токена
│     API Gateway      │ ◄───────────────────────────►  Keycloak (OAuth 2.0)
│  client + resource   │
│       server         │
└──────────────────────┘
│  пропускает только аутентифицированные запросы
▼
Внутренние микросервисы (FlowManager, конвертация, ...)

## 💡 Подсказка
Keycloak удобно поднять в Docker. Заведите realm и клиента для API Gateway и настройте его сразу как OAuth2-клиента (логин пользователя) и 
как resource server (проверка токена на входящих запросах).

Gateway выступает одновременно в трёх ролях:

 -OAuth2 Client — принимает логин пользователя через Keycloak.
 -OAuth2 Resource Server — валидирует JWT в заголовке 'Authorization: Bearer'.
 -Token Relay — передаёт access token во внутренние сервисы через
  фильтр 'TokenRelay='.

**Как это работает при браузерном запросе:**

1. Браузер обращается к защищённому эндпоинту Gateway.
2. Gateway, как OAuth2 Client, редиректит пользователя в Keycloak.
3. Пользователь вводит логин/пароль, Keycloak выдаёт authorization code.
4. Gateway обменивает код на access token и создаёт сессию.
5. При последующих запросах Gateway, как Resource Server, валидирует JWT из сессии и проверяет роль ('USER' или 'ADMIN').
6. Если роль подходит, через 'TokenRelay=' access token передаётся в FlowManager.
7. FlowManager получает уже аутентифицированный запрос с заголовком 'Authorization: Bearer ...'.


## Требования к настоящему приложению:
- JDK 17+
- IntelliJ IDEA (для запуска Spring Boot-приложений)

## Первый запуск
### 1. Создать общую Docker-сеть (один раз)

Все контейнеры (Keycloak, Kafka, MinIO, Postgres) подключаются к общей сети 'microservices-net':

    docker network create microservices-net

### 2. Поднять инфраструктуру

В репозитории 'api-gateway':

    cp .env.example .env  (при необходимости)
    docker compose up -d

В репозитории 'file-converter':

    docker compose up -d

Обе группы контейнеров окажутся в одной сети и смогут обращаться друг к другу по именам сервисов ('keycloak', 'kafka', 'minio', 'postgres').

### 3. Запуск Spring Boot-приложения

Порядок запуска:

1. discovery-server (порт 8761)
2. flow-manager (порт 8081)
3. file-converter (порт 8083)
4. api-gateway (порт 8082)

### 4. Проверка Инфраструктуры

- Eureka: http://localhost:8761
- Keycloak Admin: http://localhost:8080/admin (admin/admin)
- MinIO Console: http://localhost:9001

### Проверка OAuth2

## Проверка 1: OAuth2 + TokenRelay (браузер)

Откройте в браузере:


http://localhost:8082/api/v1/files/debug/headers


Вас перенаправит на страницу входа Keycloak. Введите:

Логин: user1 / password123.

В ответе будет 'authorization: Bearer eyJ...' — доказательство,
что Gateway провалидировал JWT и передал его в FlowManager через TokenRelay.

'debug/headers': это диагностический эндпоинт FlowManager,
 предназначенный только для локальной проверки передачи токена
 через Gateway. Он возвращает все заголовки запроса, включая
 'Authorization'. В production такой эндпоинт нужно выключать —
 например, помечать '@Profile("dev")' или удалять.
 Здесь он оставлен активным для удобства проверки.

## Проверка 2: реальный сценарий (upload - status)(PowerShell)

** Шаг 1. Создайте тестовый файл:

[System.IO.File]::WriteAllText("$PWD\test.txt", "Hello, FlowManager!", [System.Text.UTF8Encoding]::new($false))


** Шаг 2. Получите токен :

$token = (Invoke-RestMethod -Method Post -Uri "http://localhost:8080/realms/microservices-realm/protocol/openid-connect/token" -ContentType "application/x-www-form-urlencoded" -Body @{grant_type="password"; client_id="api-gateway-client"; client_secret="super-secret-key"; username="user1"; password="password123"}).access_token


** Шаг 3. Загрузите файл:

curl.exe -i -X POST -H "Authorization: Bearer $token" -F "file=@test.txt" http://localhost:8082/api/v1/files


** Шаг 4. Проверьте статус:

curl.exe -i -H "Authorization: Bearer $token" http://localhost:8082/api/v1/files/<Здесь нужно вставить UUID с 3 шага>/status

Статусы: PROCESSING -> SUCCESS / ERROR.

** Шаг 5. Скачайте готовый файл:

curl.exe -H "Authorization: Bearer $token" http://localhost:8082/api/v1/files/<Здесь нужно вставить UUID с 3 шага>/content -o result.pdf

## Учётные данные Keycloak

Пользователи realm microservices-realm:

- user1 / password123 — роль 'user'
- admin1 / admin123 — роль 'admin'

Администратор Keycloak (master realm):

- admin / admin

** Keycloak Admin Console:  http://localhost:8080/admin

## Порты

- Keycloak — 8080
- Eureka — 8761
- FlowManager — 8081
- API Gateway — 8082
- File Converter — 8083
- MinIO API — 9000
- MinIO Console — 9001
- Kafka (external) — 29092
- Postgres — 5432