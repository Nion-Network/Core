#!/bin/bash
docker build --tag node .
clear
echo "Starting $1 nodes!"
for i in $(eval echo {1..$1}); do
	OUTPUT=$(docker run -d -v /var/run/docker.sock:/var/run/docker.sock --memory="700m" node)
	echo "$i. $OUTPUT"
done;

