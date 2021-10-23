package data.chain

import data.chain.Block

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 01:36
 * using IntelliJ IDEA
 */
data class NewBlock(val isFromSync: Boolean, val block: Block)
