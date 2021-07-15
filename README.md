# Nion Network Core
<img src="https://nion.network/logo.png" width ="150">

## Getting Started

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. See deployment for notes on how to deploy the project on a live system.

### Prerequisites

* Gradle
* Docker
* Java 8 or greater

### Configuration
The build jar requiers a config.json in the working dir.
The following is an example config used for running test-nets on a local machine. 
```diff
{
  "trustedNodeIP": "172.17.0.2", 
  "trustedNodePort": 5000,
  "keystorePath": ".",
  "maxNodes": 10000,
  "slotDuration": 20000, //slot time in seconds
  "maxCommitteeMembers": 32, 
  "initialDifficulty": 1000, //VDF-difficulty
  "broadcastSpreadPercentage": 10, //fannout factor
  "validatorsCount": 32, //minimum number of validating nodes before starting block production
  "committeeSize": 32,
  "slotCount": 8, 
  "dashboardEnabled": true, //for testnets
  "influxUrl": "", 
  "influxUsername": "",
  "influxPassword": "",
  "loggingEnabled": false, //command line loggging for all nodes
  "trustedLoggingEnabled": false, //command line logging for trusted node
  "historyMinuteClearance": 10, //message cache cleanup
  "historyCleaningFrequency": 5,
  "clusterCount": 5,
  "maxIterations": 1,
  "packetSplitSize": 60000, //UDP packet size
  "useCriu": false //experimental
}

```

### Building
After building the executable jar with maven, build a docker container with the example Dockerfile.
```
docker build --tag node .
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
Note: depending on the operating system, and docker version you might need to pass a memmory limit flag to the container to avoid a problem where JVM assumes it has the entire system memory available. An expected memory usage should be between 300 and 500 MB.
## Dependancies

* [GSON](https://github.com/google/gson) - Google's json library for Java
* [Docker](https://www.docker.com/) - Container framework
* [slf4j](http://www.slf4j.org/index.html) - The Simple Logging Facade for Java (SLF4J)
* [Codec](https://commons.apache.org/proper/commons-codec/) - Apache Commons Codec
* [InfluxDb](https://www.influxdata.com/) - InfluxDB

## Authors

* **Aleksandar Tošič** - *Initial Work, Research* - [Dormage](https://github.com/Dormage)
* **Mihael Berčič** - *Development - [MihaelBercic](https://github.com/MihaelBercic)

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details

## Acknowledgments
We would like to acknowledge the collegue dr. Jernej Vičič from the University of Primorska
Faculty of Mathematics, Natural Sciences and Information Technologies, and prof. dr. Mihael Mrissa from Innorenew CoE for the theoretical fundations, which are the basis for this implementation.


