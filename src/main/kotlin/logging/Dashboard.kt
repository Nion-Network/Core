package logging

import Configuration
import chain.data.Block
import chain.data.ChainTask
import chain.data.SlotDuty
import chain.data.Vote
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.InfluxDBClientOptions
import com.influxdb.client.WriteOptions
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.write.Point
import docker.DockerStatistics
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import network.data.Endpoint
import network.data.clusters.Cluster
import utils.asHex
import utils.sha256
import java.io.File
import java.time.Instant
import java.util.concurrent.LinkedBlockingQueue

// To be removed once testing is complete, so code here is FAR from desired / optimal.
object Dashboard {

    private val configurationJson = File("./config.json").readText()
    private val configuration: Configuration = Json.decodeFromString(configurationJson)
    private val queue = LinkedBlockingQueue<Point>()

    var myInfo: String = "UNSET"

    private fun formatTime(millis: Long): String {
        val timeDifference = millis / 1000
        val h = timeDifference / (3600)
        val m = (timeDifference - (h * 3600)) / 60
        val s = timeDifference - (h * 3600) - m * 60

        return String.format("%02d:%02d:%02d", h, m, s)
    }

    init {
        try {

            if (configuration.dashboardEnabled) {
                val options = InfluxDBClientOptions.builder()
                    .url(configuration.influxUrl)
                    .authenticateToken(configuration.influxToken.toCharArray())
                    .org("innorenew")
                    // .logLevel(LogLevel.BASIC)
                    .bucket("PROD")
                    .build()

                val influxDB = InfluxDBClientFactory.create(options)
                val writeApi = influxDB.makeWriteApi(WriteOptions.builder().batchSize(2000).flushInterval(1000).build())
                Thread { while (true) writeApi.writePoint(queue.take()) }.start()
            } else Logger.info("Dashboard is disabled.")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun reportDHTQuery(identifier: String, seekerIp: String, seeker: String, hops: Int, revives: Int, duration: Long) {
        val point = Point.measurement("dht")
            .addField("hops", hops)
            .addField("ip", seekerIp)
            .addField("duration", duration)
            .addField("revives", revives)
            .addField("seeker", seeker)
            .addField("identifier", identifier)
        queue.put(point)
    }

    /**
     * Reports each containers' statistics back to our Grafana dashboard.
     *
     * @param statistics Docker statistics that are reported by all representers of clusters.
     */
    fun reportStatistics(statistics: Collection<DockerStatistics>, slot: Long) {
        var total = 0L
        for (measurement in statistics) {
            val publicKey = sha256(measurement.publicKey).asHex
            Logger.info("$publicKey has ${measurement.containers.size} containers running...")
            measurement.containers.onEach { container ->
                val point = Point.measurement("containers").apply {
                    time(Instant.now().plusMillis(total), WritePrecision.NS)
                    addField("nodeId", publicKey)
                    addField("containerId", container.id)
                    addField("cpu", container.averageCpuUsage)
                    addField("memory", container.averageMemoryUsage)
                    addField("slot", slot)
                }
                total++
                queue.put(point)
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
            addField(
                "blockProducer",
                if (blockData.blockProducer == "SKIPBLOCK") blockData.blockProducer else sha256(blockData.blockProducer).asHex
            ) // TODO: Add sha256 encoding after skip block implementation.
            addField(
                "previousHash",
                blockData.precedentHash.asHex
            )
            addField("hash", blockData.hash.asHex)
            addField("votes", blockData.votes)
        }
        queue.put(point)
    }

    /** Reports to the dashboard that a new vote arrived. */
    fun newVote(vote: Vote, publicKey: String) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("attestations").apply {
            // addField("blockHash", vote.blockHash)
            addField("committeeMember", publicKey)
        }

        queue.put(point)
    }

    // TODO: remove
    fun logPacket(endpoint: Endpoint, sender: String, missing: Int) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("queueSize")
            .addField("from", sender)
            .addField("endpoint", "$endpoint")
            .addField("missing", missing)
        queue.put(point)
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
            time(Instant.now(), WritePrecision.NS)
            addField("from", sender)
            addField("to", receiver)
            addField("slot", slot)
            addField("containerId", containerId)
            addField("duration", duration)
            addField("saveDuration", savingDuration)
            addField("transmitDuration", transmitDuration)
            addField("size", totalSize)
            addField("resumeDuration", resumeDuration)
        }
        queue.put(point)
    }

    /** Reports that an exception was caught */
    fun reportException(e: Throwable, additionalInfo: String = "") {
        Logger.reportException(e)
        val point = Point.measurement("exceptions")
            .time(Instant.now(), WritePrecision.NS)
            .addField("cause", "$myInfo | $additionalInfo ... $e ... ${e.cause}")
            .addField("message", e.message ?: "No message...")
            .addField("trace", e.stackTrace.joinToString("\n"))

        queue.put(point)
    }

    /** Reports that the localNode has requested inclusion into the validator set. */
    fun requestedInclusion(from: String, slot: Long) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("inclusion")
            .addField("from", from)
            .addField("slot", slot)
        queue.put(point)
    }

    /** Reports that a message with [id] has been sent. */
    fun sentMessage(id: String, endpoint: Endpoint, sender: String, receiver: String, messageSize: Int, delay: Long) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("message")
            .time(Instant.now(), WritePrecision.NS)
            .addField("id", id)
            .addField("endpoint", endpoint.name)
            .addField("source", sha256(sender).asHex)
            .addField("target", sha256(receiver).asHex)
            .addField("size", messageSize)
            .addField("delay", delay)

        queue.put(point)
    }

    // TODO: remove
    fun vdfInformation(computation: String) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("join")
            .addField("computation", computation)

        queue.put(point)
    }

    /** Sends message sizes computed by ProtoBuf and Json which is used for comparison. */
    fun logMessageSize(protoBuf: Int, json: Int) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("message_size")
            .addField("json", json)
            .addField("protobuf", protoBuf)

        queue.put(point)
    }

    /** Reports clusters and their representatives. */
    fun logCluster(block: Block, nextTask: ChainTask, clusters: Map<String, Cluster<String>>) {
        if (!configuration.dashboardEnabled) return
        var index = 0
        queue.put(clusterNodePoint(block, nextTask, nextTask.blockProducer, nextTask.blockProducer, index++))
        clusters.forEach { (publicKey, cluster) ->
            queue.put(clusterNodePoint(block, nextTask, cluster.centroid, publicKey, index++))
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
            .time(Instant.now(), WritePrecision.NS)
            .addField("duty", slotDuty.name)
            .addField("slot", block.slot)
            .addField("centroid", sha256(representative).asHex)
            .addField("node", sha256(node).asHex)
    }

    fun statisticSent(senderPublicKey: String, dockerStatistics: DockerStatistics, receiverPublicKey: String, slot: Long) {
        val point = Point.measurement("statistics-data")
            .time(Instant.now(), WritePrecision.NS)
            .addField("sender", sha256(senderPublicKey).asHex)
            .addField("statistics", sha256(dockerStatistics.toString()).asHex)
            .addField("sentTo", sha256(receiverPublicKey).asHex)
            .addField("slot", slot)

        queue.put(point)
    }

    fun log(type: DebugType, message: Any) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("logging")
            .time(Instant.now(), WritePrecision.NS)
            .addField("ip", myInfo)
            .addField("type", "${type.ordinal}")
            .addField("log", "$message")

        queue.put(point)
    }
}