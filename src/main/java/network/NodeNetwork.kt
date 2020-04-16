package network

<<<<<<< HEAD
=======
import Main
import abstraction.Node
import io.javalin.http.Context

/**
 * Created by Mihael Valentin Berčič
 * on 16/04/2020 at 13:45
 * using IntelliJ IDEA
 */
class NodeNetwork(val maxNodes: Int) {

    private val nodeMap: HashMap<String, Node> = hashMapOf()


    fun joinRequest(context: Context) {
        if (nodeMap.size < maxNodes) {
            val node = Main.gson.fromJson(context.body(), Node::class.java)

        } else pickRandomNodes(5).forEach {  }
    }

    fun pickRandomNodes(amount: Int) = nodeMap.values.shuffled().take(amount)
}