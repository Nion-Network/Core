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
import java.sql.PreparedStatement
import java.sql.Statement
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private lateinit var influxDB: InfluxDB
private lateinit var mysql: Connection;

class DashboardManager(private val configuration: Configuration) {

    private val queue = LinkedBlockingQueue<Point>()


    init {
        if (configuration.dashboardEnabled) {
            influxDB = InfluxDBFactory.connect(configuration.influxUrl, configuration.influxUsername, configuration.influxPassword)
            influxDB.query(Query("CREATE DATABASE PROD"));
            influxDB.setDatabase("PROD")
            //influxDB.setLogLevel(InfluxDB.LogLevel.FULL)
            influxDB.enableBatch(2000, 10000, TimeUnit.MILLISECONDS);
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
                    configuration.mysqlPassword);
            val statement: Statement = mysql.createStatement();
            statement.executeUpdate("TRUNCATE network")

        } else Logger.info("Dashboard disabled")

    }

    fun newBlockProduced(blockData: Block) {
        if (!configuration.dashboardEnabled) return
        val point: Point = Point.measurementByPOJO(blockData.javaClass).addFieldsFromPOJO(blockData).build()
        queue.add(point)
    }

    fun newVote(vote: BlockVote, publicKey: String) {
        if (!configuration.dashboardEnabled) return
        // Logger.debug("Sending attestation: ${DigestUtils.sha256Hex(vote.signature)} to Influx")
        val point = Point.measurement("attestations")
                .addField("blockHash", vote.blockHash)
                .addField("signature", DigestUtils.sha256Hex(vote.signature))
                .addField("committeeMember", publicKey).build()
        queue.add(point)
    }

    fun newRole(chainTask: ChainTask, publicKey: String, currentState: State) {
        if (!configuration.dashboardEnabled) return
        // Logger.debug("Sending new chain task : ${chainTask.myTask}")
        val point = Point.measurement("chainTask")
                .addField("nodeId", publicKey)
                .addField("task", chainTask.myTask.toString())
                .addField("slot", currentState.currentSlot)
                .addField("epoch", currentState.currentEpoch).build()
        queue.add(point)
    }

    fun logQueue(queueSize: Int, publicKey: String) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("queueSize")
                .addField("nodeId", publicKey)
                .addField("queueSize", queueSize).build()
        queue.add(point)
    }

    fun newMigration(receiver: String, publicKey: String, containerId :String, duration: Long) {
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("migration")
                .addField("from", publicKey)
                .addField("to", receiver)
                .addField("containerId", containerId)
                .addField("duration", duration)
                .build()
        queue.add(point)
    }
    /*
        fun logCluster(epoch: Int, publicKey: String, clusterRepresentative: String) {
            if (!configuration.dashboardEnabled) return
            // Logger.info("${DigestUtils.sha256Hex(publicKey)} -> ${DigestUtils.sha256Hex(clusterRepresentative)}")
            val point = Point.measurement("networkClusters")
                    .addField("epoch", epoch)
                    .addField("nodeId", DigestUtils.sha256Hex(publicKey))
                    .addField("clusterRepresentative", DigestUtils.sha256Hex(clusterRepresentative)).build()
            queue.add(point)
        }
    */
    fun logCluster(epoch: Int, publicKey: String, clusterRepresentative: String) {
        val statement: PreparedStatement = mysql.prepareStatement("INSERT INTO network (source, target) values (?,?) ON DUPLICATE KEY UPDATE target = VALUES(target)");
        statement.setString(1, DigestUtils.sha256Hex(publicKey));
        statement.setString(2, DigestUtils.sha256Hex(clusterRepresentative))
        statement.executeUpdate();
    }
}


