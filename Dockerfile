FROM docker:dind
# RUN sed 's/http:\/\/fr\./http:\/\//' /etc/apt/sources.list

WORKDIR /root

RUN apk update
RUN apk add make \
    bash \
    openjdk11-jre \
    curl \
    openssl-dev \
    python3-dev \
    gmp-dev

RUN apk add --repository http://dl-cdn.alpinelinux.org/alpine/edge/testing criu-dev
RUN apk add tar

ADD nion-1.0-SNAPSHOT.jar Node.jar
ADD config.json config.json
ADD vdf-cli vdf-cli
ADD start.sh start.sh
ADD SaveContainer.sh SaveContainer.sh
ADD RunContainer.sh RunContainer.sh

RUN chmod 777 start.sh
RUN chmod 777 vdf-cli
RUN mv vdf-cli /usr/bin/vdf-cli

ENTRYPOINT dockerd --experimental & bash start.sh