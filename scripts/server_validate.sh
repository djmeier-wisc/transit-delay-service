#!/usr/bin/env bash
sleep 15 #wait for service to start
curl http://localhost:8080/health --fail --silent | grep OK || exit 1