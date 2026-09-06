# PayFlow

[![ci](https://github.com/tabyreto4k/payflow/actions/workflows/ci.yml/badge.svg)](https://github.com/tabyreto4k/payflow/actions/workflows/ci.yml)
[![codeql](https://github.com/tabyreto4k/payflow/actions/workflows/codeql.yml/badge.svg)](https://github.com/tabyreto4k/payflow/actions/workflows/codeql.yml)
[![release](https://github.com/tabyreto4k/payflow/actions/workflows/release.yml/badge.svg)](https://github.com/tabyreto4k/payflow/actions/workflows/release.yml)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Упрощённый биллинг маркетплейса на микросервисах: заказ создаётся, деньги списываются со
счёта, заказ переходит в `PAID`, уходит уведомление. Обмен асинхронный, ни одно событие
не теряется, повторная доставка не списывает деньги дважды.

> **Статус:** работают сага через Kafka и внешний периметр — JWT, gateway, nginx.
> В работе: уведомления и наблюдаемость. Сценарий проверки: [docs/requests.http](docs/requests.http).

```
                          ┌─→ order-service ──┐
клиент → nginx → gateway ─┤                   ├─→ Kafka ─→ notification-service
                          └─→ payment-service ┘
```

У каждого сервиса своя база. Общей БД нет.

## Стек

Java 21 · Spring Boot 3.5 · Spring Cloud Gateway · PostgreSQL 16 + Liquibase · Kafka ·
Redis · nginx · Prometheus + Grafana · JUnit 5 + Testcontainers · Gradle (Kotlin DSL) ·
Docker · GitHub Actions

## Модули

| Модуль | Роль |
|---|---|
| `gateway` | маршрутизация, JWT-фильтр, rate limiting через Redis |
| `order-service` | заказы, статусная модель, transactional outbox |
| `payment-service` | счета, списание, идемпотентность по ключу запроса |
| `notification-service` | консьюмер Kafka, уведомления |
| `events-contract` | records событий — единственный общий модуль |

## Запуск

```bash
cp .env.example .env      # пароли и JWT_SECRET надо заполнить: пустые не примут
./scripts/gen-certs.sh    # self-signed сертификат для nginx
docker compose up -d --wait
./scripts/e2e-saga.sh     # весь сценарий через периметр
```

Наружу торчит только nginx: `https://localhost` (сертификат self-signed, поэтому
`curl -k`). Порты `gateway`, `order-service` и `payment-service` доступны лишь внутри
сети compose — доверие к заголовкам `X-Customer-Id`/`X-Roles` держится ровно на этом.

Рядом поднимается инфраструктура: postgres (`5432`), kafka (`29092` с хоста,
`kafka:9092` внутри сети), redis (`6379`), mailhog (`8025` — веб-интерфейс),
prometheus (`9090`), grafana (`3000`).

Образы каждой ревизии `main` — в GHCR: `ghcr.io/tabyreto4k/payflow/<сервис>:main`.

## Разработка

```bash
./gradlew build              # компиляция, spotless, checkstyle, unit-тесты, jacoco
./gradlew integrationTest    # Testcontainers, нужен запущенный Docker
./gradlew spotlessApply
```

JDK 21 на машине иметь не обязательно: тулчейн скачает нужный сам.

## Периметр

```
клиент → nginx (TLS, gzip, limit_req) → gateway (JWT, rate limit) → сервисы
```

Токены выпускает `order-service`: auth — его фича, отдельного сервиса нет. Пользователи
уже требуют JPA, Postgres и Liquibase, а gateway реактивный — держать их там значило бы
тащить туда R2DBC или блокирующий JPA в event-loop.

Gateway токены только проверяет. Валидный access превращается в `X-Customer-Id` и
`X-Roles`; клиентские заголовки с этими именами срезаются всегда, включая открытые пути.
Сервисы за периметром этим заголовкам доверяют и своей аутентификации не делают.

nginx перед gateway не дублирует его: терминация TLS, gzip, грубый лимит по адресу и
защита от медленных клиентов — работа периметра, а не маршрутизатора.

## Архитектурные решения

Раздел собирается по мере готовности: почему Kafka, а не RabbitMQ; почему outbox;
почему пессимистичная блокировка при списании; когда кэш баланса вреден.

## Лицензия

[MIT](LICENSE)
