#!/bin/bash
ID=$(docker create $2)
cp -r /tmp/$2 /var/lib/docker/containers/$ID/checkpoints/
docker start --checkpoint=$1 $ID