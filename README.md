<div style="text-align: center">
    <h1>
        <img alt="Nion Logo" src="https://nion.network/logo.png" style="vertical-align: middle" width ="100"> Nion Network Core
    </h1>
    <a href="https://medium.com/@nionnetwork">
        <img alt="Mediumm Badge" src="https://img.shields.io/badge/Medium-12100E?style=for-the-badge&logo=medium&logoColor=white"/>
    </a>
    <a href="https://t.me/NionNetwork">
        <img alt="Telegram Badge" src="https://img.shields.io/badge/Telegram-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white"/>
    </a>
    <a href="https://twitter.com/NetworkNion">
        <img alt="Twitter Badge" src="https://img.shields.io/badge/Twitter-1DA1F2?style=for-the-badge&logo=twitter&logoColor=white"/>
    </a>
    <br>
    <a href="https://github.com/Nion-Network/Core/LICENSE">
        <img alt="Apache License" src=" https://img.shields.io/badge/license-APACHE2-blue.svg"/>
    </a>
</div>


[kotlin-badge]: https://img.shields.io/badge/Kotlin-0095D5?&style=for-the-badge&logo=kotlin&logoColor=white
[gradle-badge]: https://img.shields.io/badge/gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white
[docker-badge]: https://img.shields.io/badge/Docker-2CA5E0?style=for-the-badge&logo=docker&logoColor=white

## Getting Started

![kotlin-badge]
![gradle-badge]
![docker-badge]

These instructions will get you a copy of the project up and running on your local machine for development and testing purposes. See deployment for notes on how to deploy the project on a live system.

### Prerequisites

* Gradle
* Docker
* Java 9 or greater

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
## Dependencies

* [Kotlinx Serialization (json + protobuf)](https://github.com/Kotlin/kotlinx.serialization) - Kotlin multiplatform / multi-format reflectionless serialization
* [Docker](https://www.docker.com/) - Container framework
* [slf4j](http://www.slf4j.org/index.html) - The Simple Logging Facade for Java (SLF4J)
* [InfluxDb](https://www.influxdata.com/) - InfluxDB

## Authors

* **Aleksandar Tošič** - *Initial Work, Research, Development* - [Dormage](https://github.com/Dormage)
* **Mihael Berčič** - *Development* - [MihaelBercic](https://github.com/MihaelBercic)

## License

This project is licensed under the MIT License - see the [LICENSE.md](LICENSE.md) file for details

## Acknowledgments
We would like to acknowledge the collegue dr. Jernej Vičič from the University of Primorska
Faculty of Mathematics, Natural Sciences and Information Technologies, and prof. dr. Mihael Mrissa from Innorenew CoE for the theoretical fundations, which are the basis for this implementation.


