package chain.data

/**
 * Created by Mihael Valentin Berčič
 * on 24/10/2021 at 01:36
 * using IntelliJ IDEA
 */
@Deprecated("Deprecated due to simpler implementation.", replaceWith = ReplaceWith("Block"))
data class NewBlock(val isFromSync: Boolean, val block: Block)
