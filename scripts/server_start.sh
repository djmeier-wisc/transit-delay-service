#!/usr/bin/env bash
cd /home/ec2-user/server
sudo java -jar target/*.jar > /home/ec2-user/logs/logs.txt 2> /home/ec2-user/logs/logs.txt < /home/ec2-user/logs/logs.txt &