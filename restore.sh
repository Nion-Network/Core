#!/bin/bash
docker build --tag base -f Base .
docker create --name $1 base
sudo cp -r /tmp/$2 /var/lib/docker/containers/$(docker ps -aq --no-trunc --filter name=$1)/checkpoints/
docker start --checkpoint=$2 $1