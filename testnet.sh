#!/bin/bash
docker build --tag node .
for i in {1..9}; do 
(docker run -d -v /var/run/docker.sock:/var/run/docker.sock node)
sleep 2
done;

