#!/bin/bash
while [ ! -f /var/run/docker.pid ]
do
echo "Waiting!"
sleep 5
done
java -jar Node.jar