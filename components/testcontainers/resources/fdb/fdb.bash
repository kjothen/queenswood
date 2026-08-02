#!/bin/bash

set -eo pipefail

# The port fdbserver binds inside the container. Fixed, and the only
# port the image EXPOSEs, so Docker is free to map it to whatever host
# port is available.
LISTEN_PORT=${FDB_LISTEN_PORT:-4500}

# The port fdbserver *advertises*. A client that reaches a coordinator
# is handed the cluster controller's public address and re-dials it, so
# this has to be the host's port rather than the container's — and
# Docker only assigns that when the container starts. The host writes
# it in once it knows, which is why this script waits rather than
# taking it from the environment.
PORT_FILE=${FDB_PUBLIC_PORT_FILE:-/var/fdb/public_port}

CLUSTER_FILE=/usr/local/etc/foundationdb/fdb.cluster

echo "Awaiting public port"
for _ in $(seq 1 240); do
  if [ -s "$PORT_FILE" ]; then break; fi
  sleep 0.5
done

if [ ! -s "$PORT_FILE" ]; then
  echo "No public port was written to $PORT_FILE" >&2
  exit 1
fi

PUBLIC_PORT=$(tr -d '[:space:]' <"$PORT_FILE")

echo "fdb:fdb@127.0.0.1:${PUBLIC_PORT}" >"$CLUSTER_FILE"

# Everything inside the container reads the same cluster file, and so
# re-dials the public port too — including the fdbcli that bootstraps
# the database below. That port is the host's, and nothing listens on
# it in here, so bridge it back to the port fdbserver actually binds.
if [ "$PUBLIC_PORT" != "$LISTEN_PORT" ]; then
  echo "Bridging 127.0.0.1:${PUBLIC_PORT} to 127.0.0.1:${LISTEN_PORT}"
  socat "TCP-LISTEN:${PUBLIC_PORT},fork,reuseaddr,bind=127.0.0.1" \
    "TCP:127.0.0.1:${LISTEN_PORT}" &
fi

echo "Starting FDB server, listening on ${LISTEN_PORT}, public ${PUBLIC_PORT}"

fdbserver \
  --listen-address 0.0.0.0:${LISTEN_PORT} \
  --public-address 127.0.0.1:${PUBLIC_PORT} \
  --datadir /var/fdb/data \
  --logdir /var/fdb/logs \
  --cluster-file "$CLUSTER_FILE" \
  --locality-zoneid="$(hostname)" \
  --locality-machineid="$(hostname)" \
  --class "${FDB_PROCESS_CLASS:-unset}" \
  --knob_disable_posix_kernel_aio=1 &

FDB_PID=$!

for i in $(seq 1 30); do
  if fdbcli -C "$CLUSTER_FILE" --exec "configure new single memory"; then
    echo "FDBD joined cluster."
    break
  fi
  echo "Waiting for FDB server to be ready... (attempt $i/30)"
  sleep 1
done

wait $FDB_PID
