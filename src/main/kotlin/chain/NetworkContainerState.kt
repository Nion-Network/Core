package chain

import docker.MigrationPlan
import kotlinx.serialization.Serializable

class NetworkContainerState {

    /**
     * Container map consists of keys which are node identifiers and a value of container proofs that the
     */
    private val containerMap: HashMap<String, HashMap<String, DockerContainerNetworkInformation>> = hashMapOf()

    /**
     * Returns all known [DockerContainerNetworkInformation] of the node with the identifier [ofNode].
     *
     * @param ofNode Node's public identifier.
     */
    fun getContainers(ofNode: String) = containerMap[ofNode] ?: emptyMap()

    fun removeContainer(nodeIdentifier: String, containerName: String): DockerContainerNetworkInformation? {
        return containerMap[nodeIdentifier]?.remove(containerName)
    }

    fun addContainer(nodeIdentifier: String, containerInformation: DockerContainerNetworkInformation) {
        val containerList = containerMap.getOrPut(nodeIdentifier) { hashMapOf() }
        containerList[containerInformation.containerName] = containerInformation
    }

    // ToDo: Same container names in the network?
    // ToDo: Two same name containers happen to be on the same machine and have the same network identifier (but different local identifier).

    /**
     * Modifies the [DockerContainerNetworkInformation] of the specified container.
     *
     * @param nodeIdentifier Node's public identifier
     * @param containerIdentifier Container's name
     * @param block Block of modifications specified for [DockerContainerNetworkInformation]
     */
    fun modifyContainer(nodeIdentifier: String, containerIdentifier: String, block: DockerContainerNetworkInformation.() -> Unit) {
        val containers = containerMap[nodeIdentifier] ?: throw Exception("No such Node found.")
        val container = containers[containerIdentifier] ?: throw Exception("No such Docker Container found.")
        container.apply(block)
    }

    /**
     * Moves container information between different nodes.
     *
     * @param migrationPlan [MigrationPlan] that is planned / was executed provided in that block.
     */
    fun migrationChange(migrationPlan: MigrationPlan) {
        val (from, to, container) = migrationPlan
        val removedContainer = removeContainer(from, container)
        if (removedContainer != null) addContainer(to, removedContainer)
    }

}

/**
 * Holds information about a running container and it's latest snapshot information.
 *
 * @property containerName Container name that is running on that specific node.
 * @property snapshot Hash of the latest snapshot of the container.
 */
@Serializable
data class DockerContainerNetworkInformation(val containerName: String, var snapshot: String? = null)