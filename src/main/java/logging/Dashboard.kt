package logging

import abstraction.Message
import common.BlockData
import configuration.Configuration
import org.apache.commons.codec.digest.DigestUtils
import org.influxdb.InfluxDB
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Point
import org.influxdb.dto.Query

import utils.Crypto
import java.lang.Exception
import java.util.concurrent.TimeUnit

enum class DashboardNamespace { NewBlock, ForgedBlock, Ticket, Message, Fork, Proof }

object Dashboard {
    private lateinit var influxDB:InfluxDB
    fun init(configuration: Configuration){
        influxDB = InfluxDBFactory.connect(configuration.influxUrl,configuration.influxUsername,configuration.influxPassword)
        influxDB.query(Query("CREATE DATABASE PROD"));
        influxDB.setDatabase("PROD")
        if(influxDB.ping().isGood) Logger.info("InfluxDB connection successful")
    }
    fun newBlockAccepted(blockData: BlockData, crypto: Crypto){
        try {
            val point: Point = Point.measurement("newBlock").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .addField("nodeId", DigestUtils.sha256Hex(crypto.publicKey))
                    .addField("height", blockData.height)
                    .addField("blockProducer", DigestUtils.sha256Hex(blockData.blockProducer))
                    .addField("hash", blockData.hash)
                    .addField("previousBlockHash", blockData.previousBlockHash)
                    .addField("ticket", blockData.ticket)
                    .addField("VdfProof", blockData.vdfProof)
                    .addField("difficulty", blockData.difficulty)
                    .build()
            influxDB.write(point)
        }catch (e:Exception){
            Logger.error(e.localizedMessage)
        }
        }
    fun <T> newMessage(message: Message<T>, crypto: Crypto){
        val point:Point = Point.measurement("message").time(System.currentTimeMillis(),TimeUnit.MILLISECONDS)
                .addField("hash", message.signature)
                .build()
        influxDB.write(point)
    }
    fun newLottery(ticket: Int, crypto: Crypto, height:Int){
        try {
            val point: Point = Point.measurement("lottery").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
                    .addField("nodeId", DigestUtils.sha256Hex(crypto.publicKey))
                    .addField("height", height)
                    .addField("ticket", ticket)
                    .build()
            influxDB.write(point)
        }catch (e:Exception){
            Logger.error(e.localizedMessage)
        }
    }
}

