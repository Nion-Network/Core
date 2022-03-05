#!/bin/bash
COUNT=200
echo "Running stress test of $COUNT apps..."
for i in {1..COUNT}; do
   echo "Welcome $i times"
done