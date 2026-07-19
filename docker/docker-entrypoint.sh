#!/usr/bin/env sh
set -eu

if [ "$#" -eq 0 ]; then
  set -- process --profile production --config /config/application.properties
fi

case "$1" in
  process|watch|serve|help)
    exec java -jar /app/invoice-worker.jar "$@"
    ;;
  *)
    exec "$@"
    ;;
esac
