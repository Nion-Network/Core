package manager

import data.Block
import data.BlockVote
import org.apache.commons.codec.digest.DigestUtils
import org.influxdb.InfluxDB
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Point
import org.influxdb.dto.Query

private lateinit var influxDB: InfluxDB
class DasboardManager(private val applicationManager: ApplicationManager, val configuration: data.Configuration = applicationManager.configuration) {

    init{
        influxDB = InfluxDBFactory.connect(configuration.influxUrl,configuration.influxUsername,configuration.influxPassword)
        influxDB.query(Query("CREATE DATABASE PROD"));
        influxDB.setDatabase("PROD")
        if(influxDB.ping().isGood) println("InfluxDB connection successful")
    }

    fun newBlockProduced(blockData: Block){
        val point:Point = Point.measurementByPOJO(blockData.javaClass).addFieldsFromPOJO(blockData).build()
        influxDB.write(point)
    }

    fun newVote(vote: BlockVote, publicKey: String){
        val point = Point.measurement("vote").addField("blockHash", vote.blockHash)
                .addField("signature", DigestUtils.sha256Hex(vote.signature))
                .addField("validator", DigestUtils.sha256Hex(publicKey)).build()
        influxDB.write(point)
    }
}


