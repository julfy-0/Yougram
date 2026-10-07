package app.yougram.plugin

import java.lang.reflect.Array
import java.lang.reflect.Modifier
import java.util.IdentityHashMap

/**
 * Converts generated TDLib DTOs to a safe plain-data tree (maps, lists, primitives) without hard-coding
 * every MessageContent subclass. This means new TDLib message types are exposed
 * automatically as well.
 */
object TdObjectMapper {
    fun toMap(value: Any?): Any? = convert(value, 0, IdentityHashMap())

    private fun convert(value: Any?, depth: Int, seen: IdentityHashMap<Any, Boolean>): Any? {
        if (value == null) return null
        if (depth > 8) return value.toString()
        when (value) {
            is String, is Number, is Boolean -> return value
            is Enum<*> -> return value.name
            is ByteArray -> return "<bytes:${value.size}>"
            is Iterable<*> -> return value.map { convert(it, depth + 1, seen) }
            is Map<*, *> -> return value.entries.associate { it.key.toString() to convert(it.value, depth + 1, seen) }
        }
        if (seen.put(value, true) != null) return "<cycle>"
        return try {
            val fields = LinkedHashMap<String, Any?>()
            var type: Class<*>? = value.javaClass
            while (type != null && type != Any::class.java) {
                for (field in type.declaredFields) {
                    if (Modifier.isStatic(field.modifiers) || field.isSynthetic) continue
                    field.isAccessible = true
                    fields.putIfAbsent(field.name, convert(field.get(value), depth + 1, seen))
                }
                type = type.superclass
            }
            fields["_type"] = value.javaClass.simpleName.removePrefix("Message")
            fields
        } catch (_: Throwable) {
            value.toString()
        } finally {
            seen.remove(value)
        }
    }
}
