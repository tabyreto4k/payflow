#!/usr/bin/env bash
# E2E всего периметра против поднятого compose: register → login → счёт → заказ → PAID → письмо,
# компенсация при нехватке денег, а также отказы — без токена, чужой заказ, подделка личности
# и превышение лимита.
#
# Почему не тест на Testcontainers: сценарию нужны оба Spring-контекста в одной JVM, а это
# зависимость order-service от payment-service хотя бы в тестовом source set. Архитектура
# запрещает сервисам знать друг о друге — общий модуль ровно один, events-contract.
#
#   ./scripts/gen-certs.sh && docker compose up -d --wait && scripts/e2e-saga.sh
set -euo pipefail

# Сертификат self-signed, поэтому -k. Наружу торчит только nginx.
BASE=${GATEWAY_URL:-https://localhost}
# MailHog — единственное, куда ходим мимо периметра: это не API продукта, а почтовый ящик стенда.
MAILHOG=${MAILHOG_URL:-http://localhost:8025}
CURL=(curl -sk)
DEADLINE=${E2E_TIMEOUT_SECONDS:-60}

uuid() { cat /proc/sys/kernel/random/uuid; }
fail() { echo "E2E: $*" >&2; exit 1; }

status_of() { "${CURL[@]}" -o /dev/null -w '%{http_code}' "$@"; }

register_and_login() {
    local email=$1 password=$2
    "${CURL[@]}" -X POST "$BASE/api/v1/auth/register" \
        -H 'Content-Type: application/json' \
        -d "{\"email\":\"$email\",\"password\":\"$password\"}" >/dev/null
    "${CURL[@]}" -X POST "$BASE/api/v1/auth/login" \
        -H 'Content-Type: application/json' \
        -d "{\"email\":\"$email\",\"password\":\"$password\"}" | jq -r .accessToken
}

open_account() {
    "${CURL[@]}" -X POST "$BASE/api/v1/accounts" \
        -H "Authorization: Bearer $1" \
        -H 'Content-Type: application/json' \
        -d "{\"initialBalance\":$2}" | jq -r .id
}

create_order() {
    "${CURL[@]}" -X POST "$BASE/api/v1/orders" \
        -H "Authorization: Bearer $1" \
        -H 'Content-Type: application/json' \
        -d "{\"items\":[{\"productId\":\"$(uuid)\",\"quantity\":1,\"price\":$2}]}"
}

order_status() {
    "${CURL[@]}" "$BASE/api/v1/orders/$2" -H "Authorization: Bearer $1" | jq -r .status
}

# Заказ двигается не ответом HTTP, а сагой через Kafka: статус только опрашивается.
await_status() {
    local token=$1 order=$2 want=$3 seen deadline
    deadline=$(( $(date +%s) + DEADLINE ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        seen=$(order_status "$token" "$order")
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
    actual=$("${CURL[@]}" "$BASE/api/v1/accounts/$2" -H "Authorization: Bearer $1" | jq -r .balance)
    [ "$actual" = "$3" ] || fail "баланс $actual, ожидался $3"
    echo "  баланс $actual"
}

# Письмо приезжает не ответом HTTP, а третьим сервисом по событию: его тоже только опрашиваем.
letters_to() {
    curl -s "$MAILHOG/api/v2/messages?limit=200" \
        | jq --arg to "$1" '[.items[] | select(any(.To[]; .Mailbox + "@" + .Domain == $to))] | length'
}

await_letters() {
    local address=$1 want=$2 seen deadline
    deadline=$(( $(date +%s) + DEADLINE ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        seen=$(letters_to "$address")
        if [ "$seen" = "$want" ]; then
            echo "  писем на $address: $seen"
            return 0
        fi
        sleep 1
    done
    fail "писем на $address: $seen, ожидалось $want"
}

assert_status() {
    local want=$1 got
    shift
    got=$(status_of "$@")
    [ "$got" = "$want" ] || fail "ожидался HTTP $want, пришёл $got: $*"
    echo "  HTTP $got"
}

password='correct horse battery'
email="user-$(uuid)@payflow.ru"
token=$(register_and_login "$email" "$password")
[ -n "$token" ] && [ "$token" != null ] || fail "логин не выдал access-токен"
echo "Регистрация и логин через периметр: токен получен"

echo "Сценарий 1: денег хватает → PAID"
account=$(open_account "$token" 100.00)
paid=$(create_order "$token" 40.00)
[ "$(jq -r .status <<<"$paid")" = "AWAITING_PAYMENT" ] || fail "заказ создан не в AWAITING_PAYMENT"
await_status "$token" "$(jq -r .id <<<"$paid")" PAID
assert_balance "$token" "$account" 60.00
await_letters "$email" 1

echo "Сценарий 2: денег не хватает → CANCELLED"
cancelled=$(create_order "$token" 1000.00)
await_status "$token" "$(jq -r .id <<<"$cancelled")" CANCELLED
assert_balance "$token" "$account" 60.00
# Второе письмо — об отказе: адрес тот же, а исход саги другой.
await_letters "$email" 2

echo "Сценарий 3: без токена внутрь не пускают"
assert_status 401 "$BASE/api/v1/orders"

echo "Сценарий 4: подделка X-Customer-Id не работает — gateway срезает заголовок"
assert_status 401 "$BASE/api/v1/orders" -H "X-Customer-Id: $(uuid)"

echo "Сценарий 5: чужой заказ не виден — 404, а не 403"
stranger=$(register_and_login "user-$(uuid)@payflow.ru" "$password")
assert_status 404 "$BASE/api/v1/orders/$(jq -r .id <<<"$paid")" -H "Authorization: Bearer $stranger"

echo "Сценарий 6: превышение лимита → 429"
burst=$(seq 150 | xargs -P 25 -I{} "${CURL[@]}" -o /dev/null -w '%{http_code}\n' \
    "$BASE/api/v1/orders" -H "Authorization: Bearer $token" | sort | uniq -c)
echo "$burst" | sed 's/^/  /'
grep -q ' 429$' <<<"$burst" || fail "ограничитель не сработал"

echo "E2E: периметр держит, сага работает в обе стороны, письма доходят"
