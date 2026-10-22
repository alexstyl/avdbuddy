package com.avdbuddy

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.avdbuddy.emulator.protocol.Image
import com.avdbuddy.emulator.protocol.ImageFormat
import com.avdbuddy.emulator.protocol.Touch
import com.avdbuddy.emulator.protocol.TouchEvent
import com.avdbuddy.emulator.protocol.ParameterValue
import com.avdbuddy.emulator.protocol.PhysicalModelValue
import com.google.protobuf.Empty
import io.grpc.ManagedChannel
import io.grpc.CallOptions
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.protobuf.ProtoUtils
import io.grpc.stub.ClientCalls
import io.grpc.stub.MetadataUtils
import io.grpc.stub.StreamObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// The emulator publishes these files itself. Never persist or log its bearer token.
internal class EmulatorEndpoint(val port: Int, val token: String) {
    companion object {
        fun discover(serial: String, directories: List<File> = discoveryDirectories()): EmulatorEndpoint? =
            directories.asSequence().flatMap { it.listFiles().orEmpty().asSequence() }
                .filter { it.name.matches(Regex("pid_\\d+(_info)?\\.ini")) }
                .mapNotNull { file -> runCatching {
                    val values = readIni(file)
                    if ("emulator-${values["port.serial"]}" != serial) return@runCatching null
                    val port = values["grpc.port"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return@runCatching null
                    val token = values["grpc.token"]?.takeIf { it.isNotBlank() } ?: return@runCatching null
                    EmulatorEndpoint(port, token)
                }.getOrNull() }.firstOrNull()
    }
}

private fun discoveryDirectories(): List<File> = when {
    System.getProperty("os.name").contains("Mac") -> listOf(File(userHome, "Library/Caches/TemporaryItems/avd/running"))
    System.getProperty("os.name").startsWith("Windows") -> listOf(File(System.getenv("LOCALAPPDATA") ?: userHome.path, "Temp/avd/running"))
    else -> listOfNotNull(System.getenv("XDG_RUNTIME_DIR")?.let { File(it, "avd/running") }, File(userHome, ".android/avd/running"))
}

internal val screenshotStreamMethod: MethodDescriptor<ImageFormat, Image> = MethodDescriptor.newBuilder<ImageFormat, Image>()
    .setType(MethodDescriptor.MethodType.SERVER_STREAMING)
    .setFullMethodName("android.emulation.control.EmulatorController/streamScreenshot")
    .setRequestMarshaller(ProtoUtils.marshaller(ImageFormat.getDefaultInstance()))
    .setResponseMarshaller(ProtoUtils.marshaller(Image.getDefaultInstance()))
    .build()

internal val touchMethod: MethodDescriptor<TouchEvent, Empty> = MethodDescriptor.newBuilder<TouchEvent, Empty>()
    .setType(MethodDescriptor.MethodType.UNARY)
    .setFullMethodName("android.emulation.control.EmulatorController/sendTouch")
    .setRequestMarshaller(ProtoUtils.marshaller(TouchEvent.getDefaultInstance()))
    .setResponseMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
    .build()

internal val physicalModelMethod: MethodDescriptor<PhysicalModelValue, Empty> = MethodDescriptor.newBuilder<PhysicalModelValue, Empty>()
    .setType(MethodDescriptor.MethodType.UNARY)
    .setFullMethodName("android.emulation.control.EmulatorController/setPhysicalModel")
    .setRequestMarshaller(ProtoUtils.marshaller(PhysicalModelValue.getDefaultInstance()))
    .setResponseMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
    .build()

internal class EmulatorScreenStream(private val discover: (String) -> EmulatorEndpoint? = { EmulatorEndpoint.discover(it) }) {
    private val connections = ConcurrentHashMap<String, ManagedChannel>()
    private val heldTouches = ConcurrentHashMap<String, Pair<Int, Int>>()

    fun rotate(serial: String, rotation: Int) {
        val channel = connections[serial] ?: error("The live screen is reconnecting. Try rotating again in a moment.")
        // Match the emulator's coarse orientation poses, including its 30-degree
        // tilt. Screenshot orientation follows this physical model, not wm's lock.
        val pose = when (rotation) {
            0 -> listOf(-30f, 0f, 0f)
            1 -> listOf(0f, 30f, 90f)
            2 -> listOf(30f, 0f, 180f)
            3 -> listOf(0f, -30f, -90f)
            else -> error("Invalid device rotation: $rotation")
        }
        val request = PhysicalModelValue.newBuilder()
            .setTarget(PhysicalModelValue.PhysicalType.ROTATION)
            .setValue(ParameterValue.newBuilder().addAllData(pose))
            .setInterpolation(PhysicalModelValue.Interpolation.STEP).build()
        ClientCalls.blockingUnaryCall(channel, physicalModelMethod,
            CallOptions.DEFAULT.withDeadlineAfter(2, TimeUnit.SECONDS), request)
    }

    fun touch(serial: String, x: Int, y: Int, pressed: Boolean) {
        val channel = connections[serial] ?: return
        if (pressed) heldTouches[serial] = x to y else heldTouches.remove(serial)
        sendTouch(channel, x, y, pressed, 2000)
    }

    private fun sendTouch(channel: ManagedChannel, x: Int, y: Int, pressed: Boolean, timeoutMs: Long) {
        val event = TouchEvent.newBuilder().addTouches(Touch.newBuilder().setX(x).setY(y).setPressure(if (pressed) 1024 else 0)).build()
        ClientCalls.blockingUnaryCall(channel, touchMethod, CallOptions.DEFAULT.withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS), event)
    }

    fun frames(device: Device, serial: String): Flow<EmulatorFrame> = callbackFlow {
        val endpoint = discover(serial)
            ?: error("Restart ${device.displayName} in AvdBuddy to connect the live screen.")
        val headers = Metadata().apply {
            put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer ${endpoint.token}")
        }
        val channel = NettyChannelBuilder.forAddress("localhost", endpoint.port)
            .usePlaintext().maxInboundMessageSize(32 * 1024 * 1024)
            .intercept(MetadataUtils.newAttachHeadersInterceptor(headers)).build()
        val call = channel.newCall(screenshotStreamMethod, CallOptions.DEFAULT)
        connections[serial] = channel
        try {
            ClientCalls.asyncServerStreamingCall(call,
                ImageFormat.newBuilder().setFormat(ImageFormat.ImgFormat.RGBA8888).build(),
                object : StreamObserver<Image> {
                    override fun onNext(value: Image) { trySend(value) }
                    override fun onError(t: Throwable) { close(IllegalStateException("Lost the live screen connection. Reconnecting…", t)) }
                    override fun onCompleted() { close() }
                })
            awaitClose { call.cancel("Preview closed", null) }
        } finally {
            connections.remove(serial, channel)
            heldTouches.remove(serial)?.let { (x, y) -> runCatching { sendTouch(channel, x, y, false, 250) } }
            call.cancel("Preview closed", null)
            channel.shutdownNow()
        }
    }.buffer(Channel.CONFLATED).mapNotNull { frame ->
        decodeEmulatorFrame(frame)?.let { EmulatorFrame(it, frame.format.rotation.rotationValue) }
    }.flowOn(Dispatchers.IO)
}

// Raw pixels go straight to Skia: no PNG encoder/decoder, subprocess or disk access per frame.
internal fun decodeEmulatorFrame(frame: Image): ImageBitmap? {
    val width = frame.format.width.takeIf { it > 0 } ?: frame.width
    val height = frame.format.height.takeIf { it > 0 } ?: frame.height
    if (width == 0 || height == 0) return null // The emulator display is asleep.
    require(width in 1..8192 && height in 1..8192 && width.toLong() * height * 4 == frame.image.size().toLong()) {
        "The emulator returned an invalid screen frame."
    }
    require(frame.format.format == ImageFormat.ImgFormat.RGBA8888) { "Unsupported emulator screen format." }
    val bitmap = Bitmap()
    try {
        bitmap.allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.OPAQUE))
        check(bitmap.installPixels(frame.image.toByteArray())) { "Could not read the emulator screen." }
        bitmap.setImmutable()
        return bitmap.asComposeImageBitmap()
    } catch (t: Throwable) {
        bitmap.close()
        throw t
    }
}
