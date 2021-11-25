package logging

import data.Configuration
import data.chain.Block
import data.chain.ChainTask
import data.chain.SlotDuty
import data.chain.Vote
import data.docker.DockerStatistics
import data.network.Endpoint
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import network.Cluster
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Point
import org.influxdb.dto.Query
import utils.Utils.Companion.asHex
import utils.Utils.Companion.sha256
import java.io.File
import java.net.InetAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

// To be removed once testing is complete, so code here is FAR from desired / optimal.
object Dashboard {

    private val configurationJson = File("./config.json").readText()
    private val configuration: Configuration = Json.decodeFromString(configurationJson)
    private val queue = LinkedBlockingQueue<Point>()
    private val localAddress = InetAddress.getLocalHost()

    private fun formatTime(millis: Long): String {
        val timeDifference = millis / 1000
        val h = timeDifference / (3600)
        val m = (timeDifference - (h * 3600)) / 60
        val s = timeDifference - (h * 3600) - m * 60

        return String.format("%02d:%02d:%02d", h, m, s)
    }

    init {
        if (configuration.dashboardEnabled) {
            InfluxDBFactory.connect(configuration.influxUrl, configuration.influxUsername, configuration.influxPassword).apply {
                query(Query("CREATE DATABASE PROD"))
                setDatabase("PROD")
                //setLogLevel(InfluxDB.LogLevel.FULL)
                enableBatch(2000, 500, TimeUnit.MILLISECONDS)

                Thread { while (true) write(queue.take()) }.start()
                if (ping().isGood) Logger.info("InfluxDB connection successful")
            }
        } else Logger.info("Dashboard is disabled.")

    }

    /**
     * Reports each containers' statistics back to our Grafana dashboard.
     *
     * @param statistics Docker statistics that are reported by all representers of clusters.
     */
    fun reportStatistics(statistics: Collection<DockerStatistics>, slot: Long) {
        var total = 0
        for (measurement in statistics) {
            val publicKey = sha256(measurement.publicKey).asHex
            Logger.info("$publicKey has ${measurement.containers.size} containers running...")
            measurement.containers.onEach { container ->
                val point = Point.measurement("containers").apply {
                    time(System.currentTimeMillis() + total++, TimeUnit.MILLISECONDS)
                    addField("nodeId", publicKey)
                    addField("containerId", container.id)
                    addField("cpu", container.cpuUsage)
                    addField("memory", container.memoryUsage)
                    addField("slot", slot)
                }.build()
                queue.add(point)
            }
        }
        vdfInformation("Count: $total ... Total: ${statistics.size}")
    }

    /** Sends the newly created block information to the dashboard. */
    fun newBlockProduced(blockData: Block, knownNodesSize: Int, validatorSize: Int, ip: String) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("block").apply {
            addField("created", formatTime(blockData.timestamp))
            addField("knownSize", knownNodesSize)
            addField("statistics", blockData.dockerStatistics.size)
            addField("validatorSet", validatorSize)
            addField("slot", blockData.slot)
            addField("difficulty", blockData.difficulty)
            addField("timestamp", blockData.timestamp)
            addField("ip", ip)
            addField("blockProducer", (blockData.blockProducer)) // TODO: Add sha256 encoding after skip block implementation.
            addField("previousHash", blockData.precedentHash)
            addField("hash", blockData.hash)
            addField("votes", blockData.votes)
        }.build()
        queue.add(point)
    }

    /** Reports to the dashboard that a new vote arrived. */
    fun newVote(vote: Vote, publicKey: String) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("attestations").apply {
            addField("blockHash", vote.blockHash)
            addField("committeeMember", publicKey)
        }.build()

        queue.add(point)
    }

    // TODO: remove
    fun logQueue(queueSize: Int, publicKey: String) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("queueSize").apply {
            addField("nodeId", publicKey)
            addField("queueSize", queueSize)
        }.build()
        queue.add(point)
    }

    /** Reports that a migration has been executed. */
    fun newMigration(
        receiver: String,
        sender: String,
        containerId: String,
        duration: Long,
        savingDuration: Long,
        transmitDuration: Long,
        resumeDuration: Long,
        totalSize: Long,
        slot: Long
    ) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("migration").apply {
            time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            addField("from", sender)
            addField("to", receiver)
            addField("slot", slot)
            addField("containerId", containerId)
            addField("duration", duration)
            addField("saveDuration", savingDuration)
            addField("transmitDuration", transmitDuration)
            addField("size", totalSize)
            addField("resumeDuration", resumeDuration)
        }.build()
        queue.add(point)
    }

    /** Reports that an exception was caught */
    fun reportException(e: Exception) {
        val point = Point.measurement("exceptions")
            .addField("cause", "${localAddress.hostAddress} ... $e ... ${e.cause}")
            .addField("message", e.message ?: "No message...")
            .addField("trace", e.stackTrace.joinToString("\n"))
            .build()
        queue.add(point)
    }

    /** Reports that the localNode has requested inclusion into the validator set. */
    fun requestedInclusion(from: String, slot: Long) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("inclusion")
            .addField("from", from)
            .addField("slot", slot).build()
        queue.add(point)
    }

    /** Reports that a message with [id] has been sent. */
    fun sentMessage(id: String, endpoint: Endpoint, sender: String, receiver: String, messageSize: Int, delay: Long) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("message")
            .addField("id", id)
            .addField("endpoint", endpoint.name)
            .addField("source", sha256(sender).asHex)
            .addField("target", sha256(receiver).asHex)
            .addField("size", messageSize)
            .addField("delay", delay)
            .build()
        queue.add(point)
    }

    // TODO: remove
    fun vdfInformation(computation: String) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("join")
            .addField("computation", computation)
            .build()
        queue.add(point)
    }

    /** Sends message sizes computed by ProtoBuf and Json which is used for comparison. */
    fun logMessageSize(protoBuf: Int, json: Int) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("message_size")
            .addField("json", json)
            .addField("protobuf", protoBuf)
            .build()
        queue.add(point)
    }

    /** Reports clusters and their representatives. */
    fun logCluster(block: Block, nextTask: ChainTask, clusters: List<Cluster>) {
        if (!configuration.dashboardEnabled) return
        var index = 0
        queue.add(clusterNodePoint(block, nextTask, nextTask.blockProducer, nextTask.blockProducer, index++))
        clusters.forEach { cluster ->
            queue.add(clusterNodePoint(block, nextTask, nextTask.blockProducer, cluster.representative, index++))
            cluster.nodes.forEach { node ->
                queue.add(clusterNodePoint(block, nextTask, cluster.representative, node, index++))
            }
        }
    }

    /** Computes [Point] which is used in [logCluster]. */
    private fun clusterNodePoint(block: Block, task: ChainTask, representative: String, node: String, index: Int): Point {
        val slotDuty = when {
            task.blockProducer == node -> SlotDuty.PRODUCER
            task.committee.contains(node) -> SlotDuty.COMMITTEE
            else -> SlotDuty.VALIDATOR
        }
        return Point.measurement("cluster")
            .time(System.currentTimeMillis() + index, TimeUnit.MILLISECONDS)
            .addField("duty", slotDuty.name)
            .addField("slot", block.slot)
            .addField("representative", sha256(representative).asHex)
            .addField("node", sha256(node).asHex)
            .build()
    }
}