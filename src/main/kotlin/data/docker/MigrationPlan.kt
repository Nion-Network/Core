package data.docker

import kotlinx.serialization.Serializable

/**
 * Created by Mihael Valentin Berčič
 * on 07/10/2021 at 00:55
 * using IntelliJ IDEA
 *
 * Holds information about the future migration plan which gets stored in a block.
 */
@Serializable
data class MigrationPlan(val from: String, val to: String, val container: String)