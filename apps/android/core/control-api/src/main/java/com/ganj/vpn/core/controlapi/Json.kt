package com.ganj.vpn.core.controlapi

internal sealed interface JsonValue {
    data class ObjectValue(val values: Map<String, JsonValue>) : JsonValue
    data class ArrayValue(val values: List<JsonValue>) : JsonValue
    data class StringValue(val value: String) : JsonValue
    data class NumberValue(val raw: String) : JsonValue
    data class BooleanValue(val value: Boolean) : JsonValue
    data object NullValue : JsonValue
}

internal class JsonProtocolException(message: String) : Exception(message)

internal class StrictJsonParser(
    private val source: String,
    private val maxDepth: Int = 32,
    private val maxStringLength: Int = 262_144,
) {
    private var position = 0

    fun parseObject(): JsonValue.ObjectValue {
        val parsed = parseValue(0)
        skipWhitespace()
        if (position != source.length) fail("Trailing JSON content")
        return parsed as? JsonValue.ObjectValue ?: fail("Root must be an object")
    }

    private fun parseValue(depth: Int): JsonValue {
        if (depth > maxDepth) fail("JSON nesting limit exceeded")
        skipWhitespace()
        return when (peek()) {
            '{' -> parseObjectValue(depth)
            '[' -> parseArrayValue(depth)
            '"' -> JsonValue.StringValue(parseString())
            't' -> parseLiteral("true", JsonValue.BooleanValue(true))
            'f' -> parseLiteral("false", JsonValue.BooleanValue(false))
            'n' -> parseLiteral("null", JsonValue.NullValue)
            '-', in '0'..'9' -> parseNumber()
            else -> fail("Unexpected JSON token")
        }
    }

    private fun parseObjectValue(depth: Int): JsonValue.ObjectValue {
        expect('{')
        skipWhitespace()
        if (consumeIf('}')) return JsonValue.ObjectValue(emptyMap())
        val values = linkedMapOf<String, JsonValue>()
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail("Object key must be a string")
            val key = parseString()
            if (values.containsKey(key)) fail("Duplicate object key: $key")
            skipWhitespace()
            expect(':')
            values[key] = parseValue(depth + 1)
            skipWhitespace()
            if (consumeIf('}')) break
            expect(',')
        }
        return JsonValue.ObjectValue(values)
    }

    private fun parseArrayValue(depth: Int): JsonValue.ArrayValue {
        expect('[')
        skipWhitespace()
        if (consumeIf(']')) return JsonValue.ArrayValue(emptyList())
        val values = mutableListOf<JsonValue>()
        while (true) {
            values += parseValue(depth + 1)
            skipWhitespace()
            if (consumeIf(']')) break
            expect(',')
        }
        return JsonValue.ArrayValue(values)
    }

    private fun parseString(): String {
        expect('"')
        val result = StringBuilder()
        while (position < source.length) {
            val character = source[position++]
            when {
                character == '"' -> return result.toString()
                character == '\\' -> {
                    val escaped = next()
                    result.append(
                        when (escaped) {
                            '"', '\\', '/' -> escaped
                            'b' -> '\b'
                            'f' -> '\u000C'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'u' -> parseUnicodeEscape()
                            else -> fail("Invalid string escape")
                        },
                    )
                }
                character.code < 0x20 -> fail("Unescaped control character")
                else -> result.append(character)
            }
            if (result.length > maxStringLength) fail("JSON string limit exceeded")
        }
        fail("Unterminated JSON string")
    }

    private fun parseUnicodeEscape(): Char {
        if (position + 4 > source.length) fail("Incomplete unicode escape")
        val hex = source.substring(position, position + 4)
        position += 4
        return hex.toIntOrNull(16)?.toChar() ?: fail("Invalid unicode escape")
    }

    private fun parseNumber(): JsonValue.NumberValue {
        val start = position
        consumeIf('-')
        if (consumeIf('0')) {
            if (peekOrNull()?.isDigit() == true) fail("Leading zero in number")
        } else {
            requireDigits()
        }
        if (consumeIf('.')) requireDigits()
        val exponent = peekOrNull()
        if (exponent == 'e' || exponent == 'E') {
            position++
            if (peekOrNull() == '+' || peekOrNull() == '-') position++
            requireDigits()
        }
        return JsonValue.NumberValue(source.substring(start, position))
    }

    private fun requireDigits() {
        val start = position
        while (peekOrNull()?.isDigit() == true) position++
        if (start == position) fail("Expected number digit")
    }

    private fun <T : JsonValue> parseLiteral(literal: String, value: T): T {
        if (!source.regionMatches(position, literal, 0, literal.length)) fail("Invalid literal")
        position += literal.length
        return value
    }

    private fun skipWhitespace() {
        while (true) {
            when (peekOrNull()) {
                ' ', '\n', '\r', '\t' -> position++
                else -> return
            }
        }
    }

    private fun expect(expected: Char) {
        if (next() != expected) fail("Expected '$expected'")
    }

    private fun consumeIf(expected: Char): Boolean {
        if (peekOrNull() == expected) {
            position++
            return true
        }
        return false
    }

    private fun peek(): Char = peekOrNull() ?: fail("Unexpected end of JSON")
    private fun peekOrNull(): Char? = source.getOrNull(position)
    private fun next(): Char = source.getOrNull(position++) ?: fail("Unexpected end of JSON")
    private fun fail(message: String): Nothing = throw JsonProtocolException("$message at offset $position")
}

internal fun JsonValue.ObjectValue.requiredObject(name: String): JsonValue.ObjectValue =
    values[name] as? JsonValue.ObjectValue ?: throw JsonProtocolException("Missing object '$name'")

internal fun JsonValue.ObjectValue.optionalObject(name: String): JsonValue.ObjectValue? = when (val value = values[name]) {
    null, JsonValue.NullValue -> null
    is JsonValue.ObjectValue -> value
    else -> throw JsonProtocolException("'$name' must be an object or null")
}

internal fun JsonValue.ObjectValue.requiredArray(name: String): List<JsonValue> =
    (values[name] as? JsonValue.ArrayValue)?.values ?: throw JsonProtocolException("Missing array '$name'")

internal fun JsonValue.ObjectValue.requiredString(name: String): String =
    (values[name] as? JsonValue.StringValue)?.value ?: throw JsonProtocolException("Missing string '$name'")

internal fun JsonValue.ObjectValue.optionalString(name: String): String? = when (val value = values[name]) {
    null, JsonValue.NullValue -> null
    is JsonValue.StringValue -> value.value
    else -> throw JsonProtocolException("'$name' must be a string or null")
}

internal fun JsonValue.ObjectValue.requiredLong(name: String): Long =
    (values[name] as? JsonValue.NumberValue)?.raw?.toLongOrNull()
        ?: throw JsonProtocolException("Missing integer '$name'")

internal fun JsonValue.ObjectValue.optionalLong(name: String): Long? = when (val value = values[name]) {
    null, JsonValue.NullValue -> null
    is JsonValue.NumberValue -> value.raw.toLongOrNull()
        ?: throw JsonProtocolException("'$name' must be an integer")
    else -> throw JsonProtocolException("'$name' must be an integer or null")
}

internal fun JsonValue.ObjectValue.optionalBoolean(name: String, default: Boolean = false): Boolean = when (val value = values[name]) {
    null -> default
    is JsonValue.BooleanValue -> value.value
    else -> throw JsonProtocolException("'$name' must be a boolean")
}

internal fun JsonValue.asObject(): JsonValue.ObjectValue =
    this as? JsonValue.ObjectValue ?: throw JsonProtocolException("Array item must be an object")

internal fun JsonValue.asString(): String =
    (this as? JsonValue.StringValue)?.value ?: throw JsonProtocolException("Array item must be a string")

internal object JsonEncoder {
    fun objectValue(vararg fields: Pair<String, Any?>): String = fields.joinToString(
        prefix = "{",
        postfix = "}",
    ) { (key, value) -> "${quote(key)}:${encode(value)}" }

    private fun encode(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Boolean, is Number -> value.toString()
        else -> throw IllegalArgumentException("Unsupported JSON value")
    }

    private fun quote(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
}

internal object StrictBase64 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun decode(value: String, maxDecodedBytes: Int): ByteArray {
        if (value.isEmpty() || value.length % 4 != 0) throw JsonProtocolException("Invalid base64 length")
        if (value.length > ((maxDecodedBytes + 2) / 3) * 4) throw JsonProtocolException("Base64 payload too large")
        val padding = when {
            value.endsWith("==") -> 2
            value.endsWith("=") -> 1
            else -> 0
        }
        if (value.dropLast(padding).any { ALPHABET.indexOf(it) < 0 }) throw JsonProtocolException("Invalid base64 character")
        if (padding > 0 && value.dropLast(padding).contains('=')) throw JsonProtocolException("Invalid base64 padding")
        if (padding == 2 && (index(value[value.length - 3]) and 0x0F) != 0) {
            throw JsonProtocolException("Non-canonical base64 padding")
        }
        if (padding == 1 && (index(value[value.length - 2]) and 0x03) != 0) {
            throw JsonProtocolException("Non-canonical base64 padding")
        }
        val output = ByteArray(value.length / 4 * 3 - padding)
        var inputIndex = 0
        var outputIndex = 0
        while (inputIndex < value.length) {
            val a = index(value[inputIndex++])
            val b = index(value[inputIndex++])
            val c = value[inputIndex++].let { if (it == '=') 0 else index(it) }
            val d = value[inputIndex++].let { if (it == '=') 0 else index(it) }
            val bits = (a shl 18) or (b shl 12) or (c shl 6) or d
            if (outputIndex < output.size) output[outputIndex++] = (bits shr 16).toByte()
            if (outputIndex < output.size) output[outputIndex++] = (bits shr 8).toByte()
            if (outputIndex < output.size) output[outputIndex++] = bits.toByte()
        }
        return output
    }

    private fun index(value: Char): Int = ALPHABET.indexOf(value).also {
        if (it < 0) throw JsonProtocolException("Invalid base64 character")
    }
}
