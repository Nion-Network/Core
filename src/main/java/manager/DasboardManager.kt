package manager

import blockchain.Block
import configuration.Configuration
import org.influxdb.InfluxDB
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Point
import org.influxdb.dto.Query
import logging.Logger

private lateinit var influxDB: InfluxDB
class DasboardManager(private val applicationManager: ApplicationManager, val configuration: Configuration = applicationManager.configuration) {

    init{
        influxDB = InfluxDBFactory.connect(configuration.influxUrl,configuration.influxUsername,configuration.influxPassword)
        influxDB.query(Query("CREATE DATABASE PROD"));
        influxDB.setDatabase("PROD")
        if(influxDB.ping().isGood) println("InfluxDB connection successful")
    }

    fun newBlockProduced(blockData: Block){
        Logger.debug(blockData.slot)
        val point:Point = Point.measurementByPOJO(blockData.javaClass).addFieldsFromPOJO(blockData).build();
        influxDB.write(point)
    }
}


