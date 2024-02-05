#!/usr/bin/env bash
cd /home/ec2-user/server
sudo yum install java-17-amazon-corretto
sudo java -jar target/*.jar > logs.txt 2> logs.txt < logs.txt &