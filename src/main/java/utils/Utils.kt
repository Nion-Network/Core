package utils

import logging.Logger
import java.io.File

/**
 * Created by Mihael Berčič
 * on 26/03/2020 14:45
 * using IntelliJ IDEA
 */

class Utils {

    /*
    Since we'll be using Utils class from Java classes as well, we have to use
    a companion object (static).

    Usage:
        Java   -> Utils.Companion.readFile(...)
        Kotlin -> Utils.readFile(...)
    */

    companion object {

        /**
         * Returns the file's contents specified with path
         *
         * @param path
         * @return File's contents
         */
        fun readFile(path: String): String = File(path).readText()

    }

}