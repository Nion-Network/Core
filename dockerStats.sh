#!/bin/bash
pkill -f dockerStats
while true; do
  OUTPUT=$(docker stats --format "{{.ID}} {{.Name}} {{.CPUPerc}} {{.MemPerc}} {{.PIDs}}" --no-stream);
  curl -d "$OUTPUT" -H 'Content-Type: application/json' http://localhost:5000/dockerStats
done