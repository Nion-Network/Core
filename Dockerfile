FROM ubuntu:18.04
RUN apt-get update
RUN apt-get install default-jre -y
RUN apt-get install libssl-dev -y
RUN apt-get install libssl1.0.0 libssl-dev -y
RUN apt-get update
RUN apt-get install curl -y
RUN apt-get install docker.io -y

WORKDIR /

ADD build/libs/decentralized-orchestration-for-edge-computing-1.0-SNAPSHOT.jar Node.jar
ADD config.json config.json
# Copy the current directory contents into the container at /app
COPY . /app
ADD DockerStats.sh dockerStats.sh
ADD LoadImage.sh loadImage.sh
ADD vdf-cli vdf-cli
RUN chmod 777 vdf-cli
RUN chmod +x vdf-cli
RUN mv vdf-cli /usr/bin/vdf-cli
# EXPOSE 5000
CMD java -jar Node.jar



# Stage 1 (to create a "build" image, ~140MB)
#FROM gradle:jdk10 as builder
#COPY --chown=gradle:gradle . /home/gradle/src
#WORKDIR /home/gradle/src
#RUN gradle build
# Stage 2 for slim jre
#FROM openjdk:8-jre
#EXPOSE 5000
#COPY --from=builder /home/gradle/src/build/libs/decentralized-orchestration-for-edge-computing-1.0-SNAPSHOT.jar ./app.jar
#COPY --from=builder /home/gradle/src/vdf-cli-new ./vdf-cli-new
#COPY --from=builder /home/gradle/src/config.json ./config.json
#RUN mv vdf-cli-new /usr/local/bin
#ENTRYPOINT ["java", "-jar", "./app.jar"]
