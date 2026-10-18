package app.yougram.plugin

/** Скользящее окно: не больше [limit] вызовов за [windowMs] миллисекунд на каждый плагин. */
class PluginRateLimiter(private val limit: Int, private val windowMs: Long) {
    private val hits = HashMap<String, ArrayDeque<Long>>()

    @Synchronized
    fun tryAcquire(pluginId: String, now: Long = System.currentTimeMillis()): Boolean {
        val queue = hits.getOrPut(pluginId) { ArrayDeque() }
        while (queue.isNotEmpty() && now - queue.first() >= windowMs) queue.removeFirst()
        if (queue.size >= limit) return false
        queue.addLast(now)
        return true
    }

    @Synchronized
    fun reset(pluginId: String) {
        hits.remove(pluginId)
    }

    @Synchronized
    fun clear() = hits.clear()
}
