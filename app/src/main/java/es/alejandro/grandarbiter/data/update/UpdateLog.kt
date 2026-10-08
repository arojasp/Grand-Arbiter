package es.alejandro.grandarbiter.data.update

import android.util.Log

/** Logging for the updaters, injectable so their logic runs in plain JVM unit tests. */
interface UpdateLog {
    fun info(message: String)
    fun warn(message: String, error: Throwable? = null)
}

class AndroidUpdateLog(private val tag: String) : UpdateLog {
    override fun info(message: String) { Log.i(tag, message) }
    override fun warn(message: String, error: Throwable?) { Log.w(tag, message, error) }
}
