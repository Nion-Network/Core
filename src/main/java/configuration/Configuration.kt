package configuration

data class Configuration(
        val bootstrapNode: String,
        val trustedNodeIP: String,
        val trustedNodePort: Int,
        val listeningPort: Int,
        val maxNodes: Int,
        val keystorePath: String,
        val epochDuration: Long,
        val broadcastSpread: Int,
        val initialDifficulty: Int,
        val trustedHttpAddress: String = "http://$trustedNodeIP:$trustedNodePort"
)