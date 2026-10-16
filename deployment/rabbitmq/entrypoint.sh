#!/bin/sh
# Loading definitions at boot (topology before any publisher) suppresses RabbitMQ's default-user creation, so the
# application user is created here from the environment once the node is up. The guest user stays loopback-only.
set -eu
docker-entrypoint.sh rabbitmq-server &
server=$!
until rabbitmqctl await_startup >/dev/null 2>&1; do sleep 1; done
rabbitmqctl add_user "$HARAMYAR_RABBITMQ_USER" "$HARAMYAR_RABBITMQ_PASSWORD" >/dev/null 2>&1 \
  || rabbitmqctl change_password "$HARAMYAR_RABBITMQ_USER" "$HARAMYAR_RABBITMQ_PASSWORD" >/dev/null
rabbitmqctl set_permissions -p / "$HARAMYAR_RABBITMQ_USER" ".*" ".*" ".*" >/dev/null
touch /tmp/haramyar-user-ready
wait "$server"
