package com.avdbuddy

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import java.net.http.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.*
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

internal const val LICENSE_ORIGIN = "https://licenses.avdbuddy.com"
internal const val LICENSE_PUBLIC_KEY = "0EBwSuYuIW0hRaBXAF/aQ6vc2usFCZYwEGiQhAJmZQc="

internal fun verifyLicense(receipt: JsonObject, deviceId: String, publicKey: String = LICENSE_PUBLIC_KEY): JsonObject {
    require(receipt["algorithm"]?.jsonPrimitive?.content == "Ed25519")
    require(receipt["key_id"]?.jsonPrimitive?.content == "primary")
    val payload = receipt.getValue("payload").jsonPrimitive.content
    // RFC 8410 SubjectPublicKeyInfo prefix for a raw 32-byte Ed25519 public key.
    val prefix = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
    val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(prefix + Base64.getDecoder().decode(publicKey)))
    val verifier = Signature.getInstance("Ed25519").apply { initVerify(key); update(payload.toByteArray(Charsets.UTF_8)) }
    require(verifier.verify(Base64.getDecoder().decode(receipt.getValue("signature").jsonPrimitive.content)))
    val claims = Json.parseToJsonElement(Base64.getUrlDecoder().decode(payload).toString(Charsets.UTF_8)).jsonObject
    val hash = MessageDigest.getInstance("SHA-256").digest(deviceId.toByteArray()).joinToString("") { "%02x".format(it) }
    require(claims["schema"]?.jsonPrimitive?.int == 1 && claims["issuer"]?.jsonPrimitive?.content == LICENSE_ORIGIN)
    require(claims["product"]?.jsonPrimitive?.content == "avdbuddy" && claims["device_hash"]?.jsonPrimitive?.content == hash)
    require(claims["perpetual"]?.jsonPrimitive?.boolean == true)
    require(claims.getValue("updates_until").jsonPrimitive.long > 0)
    return claims
}

internal fun atomicWrite(file: File, text: String) {
    Files.createDirectories(file.parentFile.toPath())
    val tmp = Files.createTempFile(file.parentFile.toPath(), ".avdbuddy-", ".tmp")
    try {
        Files.writeString(tmp, text)
        Files.move(tmp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally { Files.deleteIfExists(tmp) }
}

internal suspend fun licenseRequest(path: String, input: JsonObject): JsonObject = withContext(Dispatchers.IO) {
    val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    val request = HttpRequest.newBuilder(URI("$LICENSE_ORIGIN$path")).timeout(Duration.ofSeconds(30))
        .header("Content-Type", "application/json").header("User-Agent", "AvdBuddy/$APP_VERSION")
        .POST(HttpRequest.BodyPublishers.ofString(input.toString())).build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
    val bytes = response.body().use { it.readNBytes(65537) }
    check(bytes.size <= 65536) { "The server response was too large. Please try again later." }
    if (response.statusCode() !in 200..299) {
        val message = when (response.statusCode()) {
            403 -> "Could not verify your license. Check the key or manage it in the license portal."
            409 -> "All device slots are in use. Remove a device in the license portal, then try again."
            429 -> "Too many requests. Please wait a few minutes and try again."
            else -> "Could not reach the license service. Please try again later."
        }
        error(message)
    }
    Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
}

class AppLicense(private val scope: CoroutineScope, directory: File = HostPlatform().configDirectory) {
    private val deviceFile = File(directory, "license-device-id")
    private val receiptFile = File(directory, "license-receipt.json")
    private fun deviceId(): String = if (deviceFile.exists()) deviceFile.readText().trim().also { require(it.isNotBlank()) }
        else UUID.randomUUID().toString().also { atomicWrite(deviceFile, it) }
    internal var receipt by mutableStateOf(runCatching {
        Json.parseToJsonElement(receiptFile.readText()).jsonObject.also { verifyLicense(it, deviceId()) }
    }.getOrNull()); private set
    var visible by mutableStateOf(false)
    var working by mutableStateOf(false); private set
    var message by mutableStateOf(""); private set
    val licensed: Boolean get() = receipt != null
    val updatesThrough: String? get() = receipt?.let {
        Instant.ofEpochSecond(verifyLicense(it, deviceId()).getValue("updates_until").jsonPrimitive.long)
            .atZone(ZoneOffset.UTC).toLocalDate().toString()
    }

    fun activate(key: String) {
        if (working) return
        if (key.isBlank()) { message = "Enter your license key."; return }
        working = true; message = "Activating your license…"
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    val id = deviceId()
                    val response = licenseRequest("/api/v1/activate", buildJsonObject {
                        put("license_key", key.trim()); put("product", "avdbuddy"); put("device_id", id)
                        put("device_name", "${HostOs.detect().name.lowercase().replaceFirstChar(Char::uppercase)} computer")
                    }).getValue("receipt").jsonObject
                    verifyLicense(response, id)
                    atomicWrite(receiptFile, response.toString())
                    response
                }
                receipt = saved; message = "License activated. Thank you for supporting AvdBuddy."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message?.takeIf { e is IllegalStateException } ?: "Could not activate your license. Check your connection and try again." }
            finally { working = false }
        }
    }
}
