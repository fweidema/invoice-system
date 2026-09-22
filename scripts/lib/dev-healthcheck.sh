#!/usr/bin/env bash

dev_curl_local_healthcheck() {
  curl --noproxy '127.0.0.1,localhost,::1' -fsS "$1" >/dev/null
}
