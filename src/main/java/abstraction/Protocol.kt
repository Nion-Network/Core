package abstraction

/**
 * Created by Mihael Valentin Berčič
 * on 27/03/2020 at 12:11
 * using IntelliJ IDEA
 */
interface Protocol {
    fun send(message: Message)
    fun digest(message: Message)
    fun broadcast(message: Message)
}

data class Message(
        val publicKey: String,
        val signature: String,
        val body: String
)