package docker

import kotlinx.serialization.Serializable

/**
 * Created by Mihael Valentin Berčič
 * on 07/10/2021 at 00:54
 * using IntelliJ IDEA
 */
@Serializable
class ContainerMigration(
    val networkContainer: String,
    val localContainerIdentifier: String,
    val slot: Long,
    val start: Long,
    val savedAt: Long,
    val transmitAt: Long = savedAt,
    val image: String = "dormage/java-stress"
)