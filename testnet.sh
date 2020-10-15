#!/bin/bash
for i in {1..10}; do (docker run -i --cpus=0.5 -d node)
done;

