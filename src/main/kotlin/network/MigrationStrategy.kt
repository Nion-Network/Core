package network

import data.Configuration
import java.util.concurrent.ConcurrentHashMap

/**
 * Created by Mihael Valentin Berčič
 * on 18/11/2021 at 12:31
 * using IntelliJ IDEA
 */
abstract class MigrationStrategy(configuration: Configuration) : DistributedHashTable(configuration) {

    protected val networkMappings = ConcurrentHashMap<String, String>()
    protected val imageMappings = ConcurrentHashMap<String, String>()

}