package com.devbeacon.app

import android.util.Base64
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Protocol {
    fun parseSignedMessage(payload: JSONObject, sharedSecret: String): NotifyMessage? {
        val message = payload.optJSONObject("payload") ?: return null
        val signature = payload.optString("signature", "")
        if (sharedSecret.isNotBlank() && !verify(message, signature, sharedSecret)) return null
        return NotifyMessage(
            id = message.optString("id"),
            timestamp = message.optLong("timestamp"),
            title = message.optString("title", "DevBeacon"),
            body = message.optString("body"),
            level = message.optString("level", "info"),
            source = message.optString("source", "pc"),
            ttlSeconds = message.optInt("ttlSeconds", 300),
            dedupeKey = message.optString("dedupeKey", message.optString("id")),
            eventType = message.optString("eventType").ifBlank { null },
            state = message.optString("state").ifBlank { null },
            runId = message.optString("runId")
        )
    }

    fun parseServerMessage(message: JSONObject): NotifyMessage {
        return NotifyMessage(
            id = message.optString("id"),
            timestamp = message.optLong("timestamp"),
            title = message.optString("title", "DevBeacon"),
            body = message.optString("body"),
            level = message.optString("level", "info"),
            source = message.optString("source", "pc"),
            ttlSeconds = message.optInt("ttlSeconds", 300),
            dedupeKey = message.optString("dedupeKey", message.optString("id")),
            eventType = message.optString("eventType").ifBlank { null },
            state = message.optString("state").ifBlank { null },
            runId = message.optString("runId")
        )
    }

    private fun verify(message: JSONObject, signature: String, sharedSecret: String): Boolean {
        val expected = sign(canonicalJson(message), sharedSecret)
        return expected == signature
    }

    private fun sign(canonical: String, sharedSecret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(sharedSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(canonical.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun canonicalJson(obj: JSONObject): String {
        val keys = obj.keys().asSequence().toList().sorted()
        return keys.joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
            val value = obj.get(key)
            val encodedValue = when (value) {
                is Number, is Boolean -> value.toString()
                else -> JSONObject.quote(value.toString())
            }
            "${JSONObject.quote(key)}:$encodedValue"
        }
    }
}
