#!/bin/bash
if [ "$#" -ne 1 ]; then
echo "Sleeping..."
sleep $(shuf -i 1-120 -n 1)
fi

# dockerd --experimental &
# while [ ! -f /var/run/docker.pid ]
# do
# echo "Waiting for docker daemon to initialize!"
# sleep 5
# done
java -jar Node.jar $1