#!/usr/bin/env bash
sleep 10 #wait for service to start
curl --fail --silent localhost:8080/health | grep B || exit 1