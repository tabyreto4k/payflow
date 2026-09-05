#!/usr/bin/env bash
# E2E саги против поднятого compose.
#
# Почему не SagaE2EIT на Testcontainers: тесту нужны оба Spring-контекста в одной JVM, а это
# зависимость order-service от payment-service хотя бы в тестовом source set. Архитектура
# запрещает сервисам знать друг о друге — общий модуль ровно один, events-contract. План такую
# замену предусматривал: «если тяжело — compose-based smoke».
#
#   docker compose up -d --wait && scripts/e2e-saga.sh
set -euo pipefail

ORDERS=${ORDERS_URL:-http://localhost:8080}
PAYMENTS=${PAYMENTS_URL:-http://localhost:8081}
DEADLINE=${E2E_TIMEOUT_SECONDS:-60}

uuid() { cat /proc/sys/kernel/random/uuid; }
fail() { echo "E2E: $*" >&2; exit 1; }

open_account() {
    curl -sf -X POST "$PAYMENTS/api/v1/accounts" \
        -H 'Content-Type: application/json' \
        -d "{\"customerId\":\"$1\",\"initialBalance\":$2}" | jq -r .id
}

create_order() {
    curl -sf -X POST "$ORDERS/api/v1/orders" \
        -H 'Content-Type: application/json' \
        -H "X-Customer-Id: $1" \
        -d "{\"items\":[{\"productId\":\"$(uuid)\",\"quantity\":1,\"price\":$2}]}"
}

order_status() {
    curl -sf "$ORDERS/api/v1/orders/$2" -H "X-Customer-Id: $1" | jq -r .status
}

# Заказ двигается не ответом HTTP, а сагой через Kafka: статус только опрашивается.
await_status() {
    local customer=$1 order=$2 want=$3 seen deadline
    deadline=$(( $(date +%s) + DEADLINE ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        seen=$(order_status "$customer" "$order")
        if [ "$seen" = "$want" ]; then
            echo "  заказ $order → $want"
            return 0
        fi
        sleep 1
    done
    fail "заказ $order застрял в статусе $seen, ожидался $want"
}

assert_balance() {
    local actual
    actual=$(curl -sf "$PAYMENTS/api/v1/accounts/$1" | jq -r .balance)
    [ "$actual" = "$2" ] || fail "баланс $actual, ожидался $2"
    echo "  баланс $actual"
}

customer=$(uuid)
account=$(open_account "$customer" 100.00)
echo "Счёт $account на 100.00"

echo "Сценарий 1: денег хватает → PAID"
paid=$(create_order "$customer" 40.00)
[ "$(jq -r .status <<<"$paid")" = "AWAITING_PAYMENT" ] || fail "заказ создан не в AWAITING_PAYMENT"
await_status "$customer" "$(jq -r .id <<<"$paid")" PAID
assert_balance "$account" 60.00

echo "Сценарий 2: денег не хватает → CANCELLED"
cancelled=$(create_order "$customer" 1000.00)
await_status "$customer" "$(jq -r .id <<<"$cancelled")" CANCELLED
assert_balance "$account" 60.00

echo "E2E: сага работает в обе стороны"
