# Decentralized Orchestration for Edge computing


## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. See deployment for notes on how to deploy the project on a live system.

### Prerequisites

* Maven
* Docker
* Java 8 or greater

### Installing


### Building
After building the executable jar with maven, a docker container must be built with Dockerfile (also provided in the project root):
```dockerfile
FROM ubuntu:18.04
RUN apt-get update
RUN apt-get install default-jre -y
RUN apt-get install docker.io -y

WORKDIR /
ADD vdf-cli-new /usr/bin/vdf-cli
ADD target/decentralized-orchestration-for-edge-computing-1.0-SNAPSHOT.jar Node.jar
ADD config.json config.json

EXPOSE 5000
CMD java -jar Node.jar
```

Note that testing using docker containers requires the bootstrap node to be booted first. Make sure that both trustedNodeIP, and trustedNodePort are set to the boostrap node.
To check the IP of the bootstrap node you can use
```
docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' container_name_or_id
```

## Running a node
```
docker run -it node
```


## Dependancies

* [GSON](https://github.com/google/gson) - Google's json library for Java
* [Docker](https://www.docker.com/) - Container framework

## Authors

* **Aleksandar Tošič** - *Initial work* - [Dormage](https://gitlab.com/Dormage)
* **Benjamin Božič** - [Ethirallan](https://gitlab.com/Ethirallan)
* **Mihael Berčič** -[MihaelBercic](https://gitlab.com/MihaelBercic)
* **dr. Michael Mrissa** 
* **dr. Jernej Vičič** 

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details

## Acknowledgments



