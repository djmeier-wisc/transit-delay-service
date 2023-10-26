#!/usr/bin/env bash
sleep 10 #wait for service to start
curl http://localhost:80/health --fail --silent| grep OK || exit 1