#!/bin/bash
while [ ! -f /var/run/docker.pid ]
do
echo "Waiting for docker to initialize!"
sleep 5
done
java -jar Node.jar