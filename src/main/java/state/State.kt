package state

/**
 * Created by Mihael Valentin Berčič
 * on 23/09/2020 at 19:01
 * using IntelliJ IDEA
 */


data class State(var currentEpoch: Int, var ourSlot: Int, var committeeIndex: Int, var currentDifficulty: Int, val currentValidators: MutableList<String> = mutableListOf())