#!/usr/bin/env bash
curl --fail --silent localhost:8080/health | grep B || exit 1