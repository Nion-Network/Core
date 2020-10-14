package state

/**
 * Created by Mihael Valentin Berčič
 * on 23/09/2020 at 19:01
 * using IntelliJ IDEA
 */


enum class SlotDuty { PRODUCER, COMMITTEE, VALIDATOR }

data class State(var currentEpoch: Int, var currentSlot: Int, var committeeIndex: Int, var currentDifficulty: Int)
data class ChainTask(val myTask: SlotDuty, val committee: List<String> = emptyList())