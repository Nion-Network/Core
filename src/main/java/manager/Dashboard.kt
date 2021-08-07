package manager

import data.*
import logging.Logger
import org.apache.commons.codec.digest.DigestUtils
import org.influxdb.InfluxDB
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Point
import org.influxdb.dto.Query
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class Dashboard(private val configuration: Configuration) {

    private val queue = LinkedBlockingQueue<Point>()
    private lateinit var mysql: Connection
    private lateinit var influxDB: InfluxDB
    private fun formatTime(millis: Long): String {
        val timeDifference = millis / 1000
        val h = timeDifference / (3600)
        val m = (timeDifference - (h * 3600)) / 60
        val s = timeDifference - (h * 3600) - m * 60

        return String.format("%02d:%02d:%02d", h, m, s)
    }

    init {
        if (configuration.dashboardEnabled) {
            influxDB = InfluxDBFactory.connect(configuration.influxUrl, configuration.influxUsername, configuration.influxPassword)
            influxDB.query(Query("CREATE DATABASE PROD"))
            influxDB.setDatabase("PROD")
            //influxDB.setLogLevel(InfluxDB.LogLevel.FULL)
            influxDB.enableBatch(2000, 500, TimeUnit.MILLISECONDS)
            Thread {
                while (true) queue.take().apply {
                    influxDB.write(this)
                }
            }.start()
            if (influxDB.ping().isGood) Logger.info("InfluxDB connection successful")


            //mysql
            mysql = DriverManager.getConnection(
                "jdbc:mysql://sensors.innorenew.eu:3306/grafana",
                configuration.mysqlUser,
                configuration.mysqlPassword
            )
            val statement: Statement = mysql.createStatement()
            statement.executeUpdate("TRUNCATE network")

        } else Logger.info("Dashboard disabled")

    }

    /**
     * Reports each containers' statistics back to our Grafana dashboard.
     *
     * @param statistics Docker statistics that are reported by all representers of clusters.
     */
    fun reportStatistics(statistics: Collection<DockerStatistics>, slot: Int) {
        try {
            for ((index, measurement) in statistics.iterator().withIndex()) {
                val publicKey = DigestUtils.sha256Hex(measurement.publicKey)
                Logger.info("$publicKey has ${measurement.containers.size} containers running...")
                for (container in measurement.containers) {
                    val point = Point.measurement("containers").apply {
                        time(System.currentTimeMillis() + index, TimeUnit.MILLISECONDS)
                        addField("nodeId", publicKey)
                        addField("containerId", container.id)
                        addField("cpu", container.cpuUsage)
                        addField("memory", container.memoryUsage)
                        addField("slot", slot)
                    }.build()
                    queue.add(point)
                }
            }
        } catch (e: Exception) {
            reportException(e)
        }
    }

    fun newBlockProduced(blockData: Block, knownNodesSize: Int, validatorSize: Int) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("block").apply {
            addField("created", formatTime(blockData.timestamp))
            addField("knownSize", knownNodesSize)
            addField("validatorSet", validatorSize)
            addField("slot", blockData.slot)
            addField("difficulty", blockData.difficulty)
            addField("timestamp", blockData.timestamp)
            addField("committeeIndex", blockData.committeeIndex)
            addField("blockProducer", blockData.blockProducer)
            addField("previousHash", blockData.precedentHash)
            addField("hash", blockData.hash)
            addField("votes", blockData.votes)
        }.build()
        queue.add(point)
    }

    fun newVote(vote: BlockVote, publicKey: String) {
        if (!configuration.dashboardEnabled) return
        // Logger.debug("Sending attestation: ${DigestUtils.sha256Hex(vote.signature)} to Influx")
        val point = Point.measurement("attestations").apply {
            addField("blockHash", vote.blockHash)
            addField("signature", DigestUtils.sha256Hex(vote.signature))
            addField("committeeMember", publicKey)
        }.build()

        queue.add(point)
    }

    fun logQueue(queueSize: Int, publicKey: String) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("queueSize").apply {
            addField("nodeId", publicKey)
            addField("queueSize", queueSize)
        }.build()
        queue.add(point)
    }

    fun newMigration(receiver: String, publicKey: String, containerId: String, duration: Long, slot: Int) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("migration").apply {
            addField("from", publicKey)
            addField("to", receiver)
            addField("slot", slot)
            addField("containerId", containerId)
            addField("duration", duration)
        }.build()
        queue.add(point)
    }

    fun reportException(e: Exception) {
        val point = Point.measurement("exceptions")
            .addField("cause", e.toString())
            .addField("message", e.message ?: "No message...")
            .addField("trace", e.stackTrace.joinToString("\n"))
            .build()
        queue.add(point)
    }

    fun requestedInclusion(from: String, slot: Int) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("inclusion")
            .addField("from", from)
            .addField("slot", slot).build()
        queue.add(point)
    }

    fun sentMessage(id: String, endpoint: Endpoint, sender: String, receiver: String, messageSize: Int, delay: Long) {
        val point = Point.measurement("message")
            .addField("id", id)
            .addField("endpoint", endpoint.name)
            .addField("source", DigestUtils.sha256Hex(sender))
            .addField("target", DigestUtils.sha256Hex(receiver))
            .addField("size", messageSize)
            .addField("delay", delay)
            .build()
        queue.add(point)
    }

    fun logMessageSize(protoBuf: Int, json: Int) {
        val point = Point.measurement("message_size")
            .addField("json", json)
            .addField("protobuf", protoBuf)
            .build()
        queue.add(point)
    }


    fun logCluster(block: Block, nextTask: ChainTask, clusters: Map<String, List<String>>) {
        var index = 0
        queue.add(clusterNodePoint(block, nextTask, nextTask.blockProducer, nextTask.blockProducer, index++))
        clusters.forEach { (representative, nodes) ->
            queue.add(clusterNodePoint(block, nextTask, nextTask.blockProducer, representative, index++))
            nodes.forEach { node ->
                queue.add(clusterNodePoint(block, nextTask, representative, node, index++))
            }
        }
    }

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
            .addField("representative", DigestUtils.sha256Hex(representative))
            .addField("node", DigestUtils.sha256Hex(node))
            .build()
    }
}