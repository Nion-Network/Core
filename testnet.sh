#!/bin/bash
docker build --tag node .
echo "Starting $1 nodes!"
for i in $(eval echo {1..$1}); do 
(docker run -d -v /var/run/docker.sock:/var/run/docker.sock node)
sleep 2
done;

