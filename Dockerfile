FROM docker:dind
# RUN sed 's/http:\/\/fr\./http:\/\//' /etc/apt/sources.list

WORKDIR /root

RUN apk update
RUN apk add make \
    bash \
    openjdk21-jdk \
    curl \
    openssl-dev \
    python3-dev \
    gmp-dev

RUN apk add --repository http://dl-cdn.alpinelinux.org/alpine/edge/testing/x86_64/criu-dev
RUN apk add tar

COPY . .

#ADD /
#ADD *.jar Node.jar
#ADD config.json config.json
#ADD vdf-cli vdf-cli
#ADD Start.sh Start.sh
#ADD SaveContainer.sh SaveContainer.sh
#ADD RunContainer.sh RunContainer.sh

# ADD stress.sh stress.sh

# COPY stress.tar stress.tar

RUN ./gradlew assemble jar
RUN chmod 777 Start.sh
RUN chmod 777 vdf-cli
RUN mv vdf-cli /usr/bin/vdf-cli
RUN chmod +x Start.sh
ENTRYPOINT ["./Start.sh"]