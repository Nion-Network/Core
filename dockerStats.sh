#!/bin/bash
while true; do
  OUTPUT=$(docker stats --format "{{.ID}} {{.Name}} {{.CPUPerc}} {{.MemUsage}} {{.PIDs}}" --no-stream);
  curl -d "$OUTPUT" -H 'Content-Type: application/json' -X POST http://localhost:5000/dockerStats
done