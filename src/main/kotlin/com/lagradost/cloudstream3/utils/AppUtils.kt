package com.lagradost.cloudstream3.utils

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.json
import com.lagradost.cloudstream3.mapper
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import kotlin.reflect.KClass

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
object AppUtils {
    /** Any object as a JSON string */
    fun Any.toJson(): String {
        if (this is String) return this
        return toJsonLiteral()
    }

    fun Any.toJsonLiteral(): String {
        val serializer =
            this::class.serializerOrNull() ?: json.serializersModule.getContextual(this::class)
        return if (serializer != null) {
            try {
                @Suppress("UNCHECKED_CAST")
                json.encodeToString(serializer as KSerializer<Any>, this)
            } catch (e: SerializationException) {
                mapper.writeValueAsString(this)
            }
        } else {
            mapper.writeValueAsString(this)
        }
    }

    fun <T : Any> parseJson(value: String, kClass: KClass<T>): T {
        val serializer = kClass.serializerOrNull() ?: json.serializersModule.getContextual(kClass)
        if (serializer != null) {
            try {
                return json.decodeFromString(serializer, value)
            } catch (e: SerializationException) {
                // fall through
            }
        }

        return mapper.readValue(value, kClass.java)
    }

    inline fun <reified T : Any> parseJson(value: String): T {
        val serializer = runCatching { serializer<T>() }
            .recoverCatching { json.serializersModule.getContextual(T::class) }
            .getOrNull()

        if (serializer != null) {
            try {
                return json.decodeFromString(serializer, value)
            } catch (e: SerializationException) {
                // fall through
            } catch (_: Throwable) {
                // fall through
            }
        }

        return mapper.readValue(value)
    }

    inline fun <reified T> parseJson(reader: java.io.Reader, valueType: Class<T>): T {
        return mapper.readValue(reader, valueType)
    }

    inline fun <reified T : Any> tryParseJson(value: String?): T? {
        return try {
            parseJson(value ?: return null)
        } catch (_: Exception) {
            null
        }
    }
}
