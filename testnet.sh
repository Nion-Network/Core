#!/bin/bash
for i in {1..12}; do (docker run -d -v /var/run/docker.sock:/var/run/docker.sock node)
done;

