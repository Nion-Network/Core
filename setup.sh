clear
docker build --tag node .
clear
docker run -i -v /var/run/docker.sock:/var/run/docker.sock node
clear
