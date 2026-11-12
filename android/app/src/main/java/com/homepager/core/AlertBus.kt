package com.homepager.core

import android.os.Handler
import android.os.Looper

/** Tiny in-process pub/sub so the UI can show live status lines ("Mom is coming"). */
object AlertBus {
    private val main = Handler(Looper.getMainLooper())
    private val listeners = LinkedHashSet<(String) -> Unit>()
    @Volatile var last: String = ""

    fun post(line: String) {
        last = line
        main.post { listeners.toList().forEach { it(line) } }
    }
    fun listen(l: (String) -> Unit) { listeners += l }
    fun unlisten(l: (String) -> Unit) { listeners -= l }
}
