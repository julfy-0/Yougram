package app.yougram.plugin

import java.util.concurrent.CopyOnWriteArrayList

class PluginEventBus {
    private val listeners = java.util.concurrent.ConcurrentHashMap<String, CopyOnWriteArrayList<(Any?) -> Unit>>()

    fun on(event: String, listener: (Any?) -> Unit) {
        listeners.getOrPut(event) { CopyOnWriteArrayList() }.add(listener)
    }

    fun emit(event: String, payload: Any?) {
        listeners[event]?.forEach { listener ->
            try { listener(payload) } catch (_: Throwable) { }
        }
    }
}
