#!/bin/bash
CONTAINER=$1
CHECKPOINT_NAME=$2
docker checkpoint create --checkpoint-dir='/tmp' --leave-running=true $CONTAINER $CHECKPOINT_NAME
# Make sure to have unique snapshot names... ^ one of the above is checkpoint name