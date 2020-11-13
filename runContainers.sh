#!/bin/bash
rm ./nohup.out
for x in {1..25}
do
 nohup docker run -i node &
 sleep 2
done
