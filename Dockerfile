FROM docker:dind
# RUN sed 's/http:\/\/fr\./http:\/\//' /etc/apt/sources.list

USER root

RUN apk update
RUN apk add make \
    bash \
    openjdk11-jre \
    curl \
    openssl-dev \
    python3-dev

RUN apk add --repository http://dl-cdn.alpinelinux.org/alpine/edge/testing criu-dev

ADD nion-1.0-SNAPSHOT.jar Node.jar
ADD config.json config.json
ADD dockerStats.sh dockerStats.sh
ADD LoadImage.sh LoadImage.sh
ADD vdf-cli vdf-cli
ADD Base Base
ADD restore.sh restore.sh
ADD start.sh start.sh

RUN chmod 777 start.sh
RUN chmod +x start.sh

RUN chmod 777 vdf-cli
RUN chmod +x vdf-cli
RUN mv vdf-cli /usr/bin/vdf-cli

ENTRYPOINT dockerd --experimental & bash start.sh