package com.cady.cadymarketing.domain.engine

/**
 * A tiny JSON reader/writer, enough for the gateway protocol. Written by hand so the app needs no JSON
 * library (and so it runs in plain JVM unit tests, where Android's org.json is only a stub).
 * Parsed values are: Map<String, Any?>, List<Any?>, String, Double, Boolean, or null.
 */
object MiniJson {
    class ParseException(message: String) : Exception(message)

    fun parse(text: String): Any? {
        val parser = Parser(text)
        val value = parser.readValue()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw ParseException("unexpected trailing characters")
        return value
    }

    /** A JSON string literal, with quotes. */
    fun quote(value: String): String {
        val out = StringBuilder(value.length + 2)
        out.append('"')
        for (c in value) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c.code < 0x20 -> out.append(String.format("\\u%04x", c.code))
                else -> out.append(c)
            }
        }
        out.append('"')
        return out.toString()
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun atEnd(): Boolean = index >= source.length

        fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        fun readValue(): Any? {
            skipWhitespace()
            if (atEnd()) throw ParseException("unexpected end")
            val c = source[index]
            return when {
                c == '{' -> readObject()
                c == '[' -> readArray()
                c == '"' -> readString()
                c == 't' -> readLiteral("true", true)
                c == 'f' -> readLiteral("false", false)
                c == 'n' -> readLiteral("null", null)
                c == '-' || c.isDigit() -> readNumber()
                else -> throw ParseException("unexpected character at $index")
            }
        }

        private fun readLiteral(word: String, value: Any?): Any? {
            if (!source.startsWith(word, index)) throw ParseException("bad literal at $index")
            index += word.length
            return value
        }

        private fun readNumber(): Double {
            val start = index
            while (index < source.length && source[index] in "+-0123456789.eE") index++
            return source.substring(start, index).toDoubleOrNull() ?: throw ParseException("bad number at $start")
        }

        private fun readString(): String {
            index++ // opening quote
            val out = StringBuilder()
            while (true) {
                if (atEnd()) throw ParseException("unterminated string")
                val c = source[index++]
                when (c) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd()) throw ParseException("unterminated escape")
                        when (val escaped = source[index++]) {
                            'n' -> out.append('\n')
                            't' -> out.append('\t')
                            'r' -> out.append('\r')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'u' -> {
                                if (index + 4 > source.length) throw ParseException("bad unicode escape")
                                val code = source.substring(index, index + 4).toIntOrNull(16)
                                    ?: throw ParseException("bad unicode escape")
                                out.append(code.toChar())
                                index += 4
                            }
                            else -> out.append(escaped)
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun readArray(): List<Any?> {
            index++ // [
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (!atEnd() && source[index] == ']') {
                index++
                return list
            }
            while (true) {
                list.add(readValue())
                skipWhitespace()
                if (atEnd()) throw ParseException("unterminated array")
                val c = source[index++]
                if (c == ']') return list
                if (c != ',') throw ParseException("expected , or ] at ${index - 1}")
            }
        }

        private fun readObject(): Map<String, Any?> {
            index++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (!atEnd() && source[index] == '}') {
                index++
                return map
            }
            while (true) {
                skipWhitespace()
                if (atEnd() || source[index] != '"') throw ParseException("expected a key at $index")
                val key = readString()
                skipWhitespace()
                if (atEnd() || source[index] != ':') throw ParseException("expected : at $index")
                index++
                map[key] = readValue()
                skipWhitespace()
                if (atEnd()) throw ParseException("unterminated object")
                val c = source[index++]
                if (c == '}') return map
                if (c != ',') throw ParseException("expected , or } at ${index - 1}")
            }
        }
    }
}
