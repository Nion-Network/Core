#!/bin/bash
count=200
echo "Running stress test of $count apps..."

for i in $(seq $count); do
   docker run dormage/java-stress
   echo "Started $i-th app."
   sleep 30
done