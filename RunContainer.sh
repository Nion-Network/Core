#!/bin/bash
if [ $1 == "-c" ]; then
    MIGRATED_CONTAINER=$2
    IMAGE=$3
    CONTAINER=`docker create $IMAGE 2> /dev/null | tail -n 1`
    tar -xf "/tmp/${MIGRATED_CONTAINER}.tar" -C "/var/lib/docker/containers/${CONTAINER}/checkpoints/"
    docker start --checkpoint=$MIGRATED_CONTAINER $CONTAINER
    echo $CONTAINER
    else
    MIGRATED=$1
    CONTAINER=`docker load -i "/tmp/${MIGRATED}.tar" | sed 's/:/\n/g' | tail -n 1`
    docker run -d $CONTAINER
fi