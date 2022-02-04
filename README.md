<div align="center">
    <h1>
        <img alt="Nion Logo" src="https://nion.network/logo.png" style="vertical-align: middle" width ="100"> Nion Network Core
    </h1>
    <a href="https://medium.com/@nionnetwork">
        <img alt="Medium Badge" src="https://img.shields.io/badge/Medium-12100E?logo=medium&logoColor=white"/>
    </a>
    <a href="https://t.me/NionNetwork">
        <img alt="Telegram Badge" src="https://img.shields.io/badge/Telegram-2CA5E0?logo=telegram&logoColor=white"/>
    </a>
    <a href="https://twitter.com/NetworkNion">
        <img alt="Twitter Badge" src="https://img.shields.io/badge/Twitter-1DA1F2?logo=twitter&logoColor=white"/>
    </a>
    <br>
    <a href="#">
        <img alt="Commit Activity" src="https://img.shields.io/github/commit-activity/m/Nion-Network/Core?color=e"/>
    </a>
    <br>
    <a href="#">
        <img alt="PR" src="https://img.shields.io/github/issues-pr/Nion-Network/Core?color=%235352ed"/>
    </a>
    <br>
    <a href="#">
        <img alt="Issues" src="https://img.shields.io/github/issues/Nion-Network/Core?color=violet"/>
    </a>
    <br>
    <a href="#">
        <img alt="MIT License" src="https://img.shields.io/badge/license-MIT-green"/>
    </a>
    <br>
    <a href="#">
        <img alt="Website" src="https://img.shields.io/website?url=https%3A%2F%2Fnion.network"/>
    </a>
    <br>
    <a href="#">
        <img alt="LOC" src="https://img.shields.io/tokei/lines/github/nion-network/core?label=LOC"/>
    </a>
    <br>
    <a href="#">
        <img alt="Stars" src="https://img.shields.io/github/stars/Nion-Network/Core?color=%23a29bfe"/>
    </a>
</div>


[kotlin-badge]: https://img.shields.io/badge/Kotlin-0095D5?logo=kotlin&logoColor=white
[gradle-badge]: https://img.shields.io/badge/gradle-02303A?logo=gradle&logoColor=white
[docker-badge]: https://img.shields.io/badge/Docker-2CA5E0?logo=docker&logoColor=white

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
```json
{
  "trustedNodeIP": "172.17.0.2",
  "trustedNodePort": 5000,
  "keystorePath": ".",
  "maxNodes": 10000,
  "slotDuration": 10000,
  "initialDifficulty": 1000,
  "broadcastSpreadPercentage": 5,
  "committeeSize": 256,
  "influxUrl": "REDACTED",
  "influxUsername": "REDACTED",
  "influxPassword": "REDACTED",
  "dashboardEnabled": true,
  "loggingEnabled": false,
  "trustedLoggingEnabled": false,
  "historyMinuteClearance": 10,
  "historyCleaningFrequency": 5,
  "nodesPerCluster": 30,
  "maxIterations": 5,
  "packetSplitSize": 60000,
  "useCriu": false
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
* [InfluxDb](https://www.influxdata.com/) - InfluxDB

## Authors

* **Aleksandar Tošič** - *Initial Work, Research, Development* - [Dormage](https://github.com/Dormage)
* **Mihael Berčič** - *Development* - [MihaelBercic](https://github.com/MihaelBercic)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details

## Acknowledgments
We would like to acknowledge the collegue dr. Jernej Vičič from the University of Primorska
Faculty of Mathematics, Natural Sciences and Information Technologies, and prof. dr. Mihael Mrissa from Innorenew CoE for the theoretical fundations, which are the basis for this implementation.


