#!/bin/bash
if [ $1 == "-c" ]; then
    CONTAINER=$2
    docker checkpoint create --checkpoint-dir='/tmp' $CONTAINER $CONTAINER
    tar -C /tmp -cf /tmp/${CONTAINER}.tar $CONTAINER
    rm -R /tmp/${CONTAINER}
    else
    CONTAINER=$1
    docker stop $CONTAINER
    COMMIT=`docker commit $CONTAINER | cut -c 8-`
    docker save $COMMIT > "/tmp/${CONTAINER}.tar"
fi