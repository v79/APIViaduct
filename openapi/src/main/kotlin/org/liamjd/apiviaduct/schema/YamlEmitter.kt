package org.liamjd.apiviaduct.schema

/**
 * A tiny, dependency-free YAML serializer for the plain trees [OpenApiGenerator] builds. It handles
 * exactly the value types that appear in those trees — [Map] (block mappings), [List] (block
 * sequences), and the scalars [String], [Number] and [Boolean] — which is all the targeted OpenAPI
 * subset needs. It is intentionally not a general-purpose YAML library.
 *
 * Maps preserve insertion order (use [LinkedHashMap]); `null` values and empty maps/lists are
 * omitted so the output stays clean.
 */
object YamlEmitter {

    fun emit(root: Map<String, Any?>): String {
        val sb = StringBuilder()
        writeMapEntries(sb, root, indent = 0)
        return sb.toString()
    }

    private fun writeMapEntries(sb: StringBuilder, map: Map<*, *>, indent: Int) {
        for ((rawKey, value) in map) {
            if (isEmpty(value)) continue
            val key = scalar(rawKey.toString())
            when (value) {
                is Map<*, *> -> {
                    sb.pad(indent).append(key).append(":\n")
                    writeMapEntries(sb, value, indent + 1)
                }

                is List<*> -> {
                    sb.pad(indent).append(key).append(":\n")
                    writeList(sb, value, indent)
                }

                else -> sb.pad(indent).append(key).append(": ").append(scalar(value)).append('\n')
            }
        }
    }

    private fun writeList(sb: StringBuilder, list: List<*>, indent: Int) {
        for (item in list) {
            when (item) {
                is Map<*, *> -> {
                    // Emit the first entry on the "- " line, the rest aligned under it.
                    val entries = item.entries.filterNot { isEmpty(it.value) }
                    if (entries.isEmpty()) continue
                    sb.pad(indent).append("- ")
                    entries.forEachIndexed { i, (k, v) ->
                        if (i > 0) sb.pad(indent + 1)
                        val key = scalar(k.toString())
                        when (v) {
                            is Map<*, *> -> {
                                sb.append(key).append(":\n"); writeMapEntries(sb, v, indent + 2)
                            }

                            is List<*> -> {
                                sb.append(key).append(":\n"); writeList(sb, v, indent + 1)
                            }

                            else -> sb.append(key).append(": ").append(scalar(v)).append('\n')
                        }
                    }
                }

                else -> sb.pad(indent).append("- ").append(scalar(item)).append('\n')
            }
        }
    }

    private fun isEmpty(value: Any?): Boolean = when (value) {
        null -> true
        is Map<*, *> -> value.all { isEmpty(it.value) }
        is List<*> -> value.isEmpty()
        else -> false
    }

    /** Quote a scalar only when YAML would otherwise misread it. */
    private fun scalar(value: Any?): String {
        if (value is Number || value is Boolean) return value.toString()
        val s = value.toString()
        val needsQuoting = s.isEmpty() ||
            s.first().isWhitespace() || s.last().isWhitespace() ||
            s.first() in "!&*?|>%@`\"'#-[]{},:" ||
            s.contains(": ") || s.contains(" #") ||
            s in setOf("true", "false", "null", "~") ||
            s.toDoubleOrNull() != null
        return if (needsQuoting) "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" else s
    }

    private fun StringBuilder.pad(indent: Int): StringBuilder = append("  ".repeat(indent))
}
