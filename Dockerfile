FROM jgeusebroek/minimal-jre-8
#UN apt-get update
#RUN apt-get install default-jre -y
#RUN apt-get install docker.io -y

WORKDIR /

ADD build/libs/decentralized-orchestration-for-edge-computing-1.0-SNAPSHOT.jar Node.jar
ADD config.json config.json
#COPY . /app
ADD vdf-cli vdf-cli
RUN chmod 777 vdf-cli
RUN chmod +x vdf-cli
RUN mv vdf-cli /usr/bin/vdf-cli
EXPOSE 5000
CMD java -jar Node.jar
