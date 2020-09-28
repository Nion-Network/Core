package manager

import java.util.*
import kotlin.concurrent.schedule

/**
 * Created by Mihael Valentin Berčič
 * on 28/09/2020 at 15:04
 * using IntelliJ IDEA
 */
class TimeManager {

    /**
     * Runs the provided timer task after a specific amount of milliseconds ran.
     *
     * @param delay Time to delay in milliseconds.
     * @param block Lambda to execute after the delay.
     */
    fun runAfter(delay: Long, block: TimerTask.() -> Unit) = Timer(true).schedule(delay, block)

}