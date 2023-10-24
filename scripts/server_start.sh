#!/usr/bin/env bash
cd /home/ec2-user/server
sudo java -jar -Dserver.port=80 \
    *.jar > /home/ec2-user/server/logs/logs.txt 2> /home/ec2-user/server/logs/logs.txt < /home/ec2-user/server/logs/logs.txt &