package manager

import io.javalin.http.Context
import messages.VdfProofBody
import org.apache.commons.codec.digest.DigestUtils
import utils.Utils
import utils.getMessage
import java.math.BigInteger
import kotlin.random.Random


/**
 * Created by Mihael Valentin Berčič
 * on 25/09/2020 at 14:25
 * using IntelliJ IDEA
 */
class VDFManager(private val applicationManager: ApplicationManager) {


    private val networkManager by lazy { applicationManager.networkManager }
    private val configuration = applicationManager.configuration
    private val crypto by lazy { applicationManager.crypto }
    private val chainManager by lazy { applicationManager.chainManager }

    fun receivedVdf(context: Context) {

        val ip = context.ip()
        val isLocal = ip == "127.0.0.1"
        val message = if (isLocal) null else context.getMessage<VdfProofBody>()
        val body = message?.body ?: Utils.gson.fromJson<VdfProofBody>(context.body(), VdfProofBody::class.java)

        val proof = body.proof
        val epoch = body.block
        if (isLocal || chainManager.isVDFCorrect(proof)) {
            val hex = DigestUtils.sha256Hex(proof)
            val seed = BigInteger(hex, 16)
                    .remainder(Long.MAX_VALUE.toBigInteger())
                    .toLong()
            val random = Random(seed)
            val ourKey = crypto.publicKey

            for (slot in 0..configuration.slotCount) {
                val validatorSetCopy = applicationManager.currentValidators.toMutableList()
                val blockProducerNode = validatorSetCopy.shuffled(random)[0]
                validatorSetCopy.remove(blockProducerNode)

                val committee = validatorSetCopy.shuffled(random).take(configuration.committeeSize)
                validatorSetCopy.removeAll(committee)

                val weProduce = blockProducerNode == ourKey
                val weCommittee = committee.contains(ourKey)

                // println("Info for slot [$slot]:\tWe produce: $weProduce\tWe committee: $weCommittee")

                chainManager.mySlotDuties[slot] = when {
                    weProduce -> Doodie.PRODUCER
                    weCommittee -> Doodie.COMMITTEE
                    else -> Doodie.VALIDATOR
                }

            }
            chainManager.startTheTimer()
        }


    }

}