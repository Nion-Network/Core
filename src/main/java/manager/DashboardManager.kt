package manager

import data.Block
import data.BlockVote
import data.Configuration
import logging.Logger
import org.apache.commons.codec.digest.DigestUtils
import org.influxdb.InfluxDB
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Point
import org.influxdb.dto.Query

private lateinit var influxDB: InfluxDB

class DashboardManager(private val configuration: Configuration) {

    init {
        if (configuration.dashboardEnabled) {
            influxDB = InfluxDBFactory.connect(configuration.influxUrl, configuration.influxUsername, configuration.influxPassword)
            influxDB.query(Query("CREATE DATABASE PROD"));
            influxDB.setDatabase("PROD")
            //influxDB.setLogLevel(InfluxDB.LogLevel.FULL)
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
}


