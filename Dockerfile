FROM ubuntu:18.04
RUN apt-get update
RUN apt-get install default-jre -y
RUN apt-get install docker.io -y

WORKDIR /
ADD vdf-cli /usr/bin/vdf-cli
ADD vdf-cli vdf-cli
ADD build/libs/decentralized-orchestration-for-edge-computing-1.0-SNAPSHOT.jar Node.jar
ADD config.json config.json
# Copy the current directory contents into the container at /app
#COPY . /app
RUN chmod +x vdf-cli
RUN chmod +x /usr/bin/vdf-cli
EXPOSE 5000
CMD java -jar Node.jar
