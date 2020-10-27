package manager

import data.*
import logging.Logger
import org.apache.commons.codec.digest.DigestUtils
import org.influxdb.InfluxDB
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Point
import org.influxdb.dto.Query
import java.util.concurrent.TimeUnit

private lateinit var influxDB: InfluxDB

class DashboardManager(private val configuration: Configuration) {

    init {
        if (configuration.dashboardEnabled) {
            influxDB = InfluxDBFactory.connect(configuration.influxUrl, configuration.influxUsername, configuration.influxPassword)
            influxDB.query(Query("CREATE DATABASE PROD"));
            influxDB.setDatabase("PROD")
            //influxDB.setLogLevel(InfluxDB.LogLevel.FULL)
            influxDB.enableBatch(2000, 1000, TimeUnit.MILLISECONDS);
            if (influxDB.ping().isGood) Logger.info("InfluxDB connection successful")
        } else Logger.info("Dashboard disabled")
    }

    fun newBlockProduced(blockData: Block) {
        if (!configuration.dashboardEnabled) return
        val point: Point = Point.measurementByPOJO(blockData.javaClass).addFieldsFromPOJO(blockData).build()
        influxDB.write(point)
        influxDB.flush()
    }

    fun newVote(vote: BlockVote, publicKey: String) {
        if (!configuration.dashboardEnabled) return
        Logger.debug("Sending attestation: ${DigestUtils.sha256Hex(vote.signature)} to Influx")
        val point = Point.measurement("attestations")
                .addField("blockHash", vote.blockHash)
                .addField("signature", DigestUtils.sha256Hex(vote.signature))
                .addField("committeeMember", publicKey).build()
        influxDB.write(point)
        influxDB.flush()
    }
    fun newRole(chainTask: ChainTask, publicKey: String, currentState:State){
        if (!configuration.dashboardEnabled) return
        Logger.debug("Sending new chain task : ${chainTask.myTask}")
        val point = Point.measurement("chainTask")
                .addField("nodeId", publicKey)
                .addField("task", chainTask.myTask.toString())
                .addField("slot", currentState.currentSlot)
                .addField("epoch", currentState.currentEpoch).build()
        influxDB.write(point)
        influxDB.flush()
    }

    fun logQueue(queueSize:Int, publicKey: String){
        if (!configuration.dashboardEnabled) return
        val point = Point.measurement("queueSize")
                .addField("nodeId", publicKey)
                .addField("queueSize", queueSize).build()
        influxDB.write(point)
        influxDB.flush()
    }
}


