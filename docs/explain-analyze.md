# Витрина заказов: составной индекс

Замер на живом стенде, Postgres 16. Таблица `orders` наполнена синтетикой: 200 000 заказов
на 1000 покупателей, по 200 заказов на каждого. После замеров данные удалены.

Запрос — тот самый, который отдаёт страницу витрины (`OrderRepository.findIdsByCustomerId`
с сортировкой по умолчанию из контроллера):

```sql
SELECT o.id FROM orders o
WHERE o.customer_id = '00000000-0000-0000-0000-000000000042'
ORDER BY o.created_at DESC
LIMIT 20 OFFSET 0;
```

## До: одиночный индекс `ix_orders_customer_id`

```
 Limit (actual time=1.796..1.802 rows=20 loops=1)
   Buffers: shared hit=205
   ->  Sort (actual time=1.793..1.796 rows=20 loops=1)
         Sort Key: created_at DESC
         Sort Method: top-N heapsort  Memory: 26kB
         Buffers: shared hit=205
         ->  Bitmap Heap Scan on orders o (actual time=0.095..1.701 rows=200 loops=1)
               Recheck Cond: (customer_id = '...'::uuid)
               Heap Blocks: exact=200
               Buffers: shared hit=202
               ->  Bitmap Index Scan on ix_orders_customer_id (actual time=0.050..0.051 rows=200 loops=1)
                     Index Cond: (customer_id = '...'::uuid)
                     Buffers: shared hit=2
 Execution Time: 1.883 ms
```

Индекс отдаёт только фильтр. Чтобы вернуть двадцать строк, база поднимает **все двести**
заказов покупателя из кучи и сортирует их — на каждый запрос.

## После: составной `ix_orders_customer_created (customer_id, created_at DESC)`

```
 Limit (actual time=0.040..0.064 rows=20 loops=1)
   Buffers: shared hit=20 read=3
   ->  Index Scan using ix_orders_customer_created on orders o (actual time=0.039..0.061 rows=20 loops=1)
         Index Cond: (customer_id = '...'::uuid)
         Buffers: shared hit=20 read=3
 Execution Time: 0.099 ms
```

Узла `Sort` больше нет: индекс уже хранит заказы покупателя в нужном порядке, и `LIMIT 20`
останавливает чтение на двадцатой строке.

| | Время | Буферов |
|---|---|---|
| одиночный индекс | 1.883 мс | 205 |
| составной | 0.099 мс | 23 |

Разница растёт вместе с числом заказов у покупателя: сортировка масштабируется от их
количества, чтение из индекса — от размера страницы.

## Что ещё изменилось

- Одиночный `ix_orders_customer_id` удалён: `customer_id` — префикс составного индекса,
  поиск только по покупателю идёт туда же. Лишний индекс на таблице, в которую пишут,
  замедляет вставки и ничего не ускоряет.
- Порядок страницы задан явно (`@PageableDefault(sort = "createdAt", direction = DESC)`).
  До этого сортировки не было вовсе, и `LIMIT/OFFSET` без `ORDER BY` мог показать на второй
  странице то же, что на первой: порядок строк ничем не гарантирован.
