-- Выполняется один раз при инициализации кластера (entrypoint postgres).
-- Имена и пароли приходят из окружения контейнера: держать их в файле репозитория нельзя.
\getenv orders_db POSTGRES_ORDERS_DB
\getenv orders_user POSTGRES_ORDERS_USER
\getenv orders_password POSTGRES_ORDERS_PASSWORD
\getenv payments_db POSTGRES_PAYMENTS_DB
\getenv payments_user POSTGRES_PAYMENTS_USER
\getenv payments_password POSTGRES_PAYMENTS_PASSWORD

CREATE ROLE :"orders_user" WITH LOGIN PASSWORD :'orders_password';
CREATE ROLE :"payments_user" WITH LOGIN PASSWORD :'payments_password';

CREATE DATABASE :"orders_db" OWNER :"orders_user";
CREATE DATABASE :"payments_db" OWNER :"payments_user";

-- Изоляция сервисов держится на правах, а не на договорённости [Р2]: по умолчанию
-- CONNECT есть у PUBLIC, поэтому его снимаем и выдаём каждому только его базу.
REVOKE CONNECT ON DATABASE :"orders_db" FROM PUBLIC;
REVOKE CONNECT ON DATABASE :"payments_db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"orders_db" TO :"orders_user";
GRANT CONNECT ON DATABASE :"payments_db" TO :"payments_user";
