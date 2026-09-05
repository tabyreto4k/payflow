# PayFlow

[![ci](https://github.com/tabyreto4k/payflow/actions/workflows/ci.yml/badge.svg)](https://github.com/tabyreto4k/payflow/actions/workflows/ci.yml)
[![codeql](https://github.com/tabyreto4k/payflow/actions/workflows/codeql.yml/badge.svg)](https://github.com/tabyreto4k/payflow/actions/workflows/codeql.yml)
[![release](https://github.com/tabyreto4k/payflow/actions/workflows/release.yml/badge.svg)](https://github.com/tabyreto4k/payflow/actions/workflows/release.yml)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Упрощённый биллинг маркетплейса на микросервисах: заказ создаётся, деньги списываются со
счёта, заказ переходит в `PAID`, уходит уведомление. Обмен асинхронный, ни одно событие
не теряется, повторная доставка не списывает деньги дважды.

> **Статус:** работает синхронное ядро — счета и заказы в `order-service`.
> Распил на сервисы, Kafka и сага — в работе. Сценарий проверки: [docs/requests.http](docs/requests.http).

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
cp .env.example .env   # пароли надо заполнить: пустой postgres не примет
docker compose up -d --wait
```

Поднимается инфраструктура: postgres (`5432`), kafka (`29092` с хоста, `kafka:9092`
внутри сети), redis (`6379`), mailhog (`8025` — веб-интерфейс), prometheus (`9090`),
grafana (`3000`). Сами сервисы приезжают в compose по мере готовности.

Образы каждой ревизии `main` — в GHCR: `ghcr.io/tabyreto4k/payflow/<сервис>:main`.

## Разработка

```bash
./gradlew build              # компиляция, spotless, checkstyle, unit-тесты, jacoco
./gradlew integrationTest    # Testcontainers, нужен запущенный Docker
./gradlew spotlessApply
```

JDK 21 на машине иметь не обязательно: тулчейн скачает нужный сам.

## Архитектурные решения

Раздел собирается по мере готовности: почему Kafka, а не RabbitMQ; почему outbox;
почему пессимистичная блокировка при списании; когда кэш баланса вреден.

## Лицензия

[MIT](LICENSE)
