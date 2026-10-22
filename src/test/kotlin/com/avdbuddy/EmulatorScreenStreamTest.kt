package com.avdbuddy

import com.avdbuddy.emulator.protocol.Image
import com.avdbuddy.emulator.protocol.ImageFormat
import com.avdbuddy.emulator.protocol.TouchEvent
import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import io.grpc.Metadata
import io.grpc.ServerInterceptors
import io.grpc.ServerServiceDefinition
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.stub.ServerCallStreamObserver
import io.grpc.stub.ServerCalls
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.*

// Protocol/codec tests only: no app window, emulator, Compose driver, or UI input.
class EmulatorScreenStreamTest {
    @TempDir lateinit var root: File

    private fun frame() = Image.newBuilder().setFormat(ImageFormat.newBuilder()
        .setFormat(ImageFormat.ImgFormat.RGBA8888).setWidth(2).setHeight(2))
        .setImage(ByteString.copyFrom(byteArrayOf(
            -1, 0, 0, -1, 0, -1, 0, -1,
            0, 0, -1, -1, -1, -1, -1, -1,
        ))).build()

    @Test fun `raw frame preserves pixel colors and rows without PNG decoding`() {
        val bitmap = decodeEmulatorFrame(frame())!!
        val pixels = IntArray(4)
        bitmap.readPixels(pixels)
        assertContentEquals(intArrayOf(0xffff0000.toInt(), 0xff00ff00.toInt(), 0xff0000ff.toInt(), -1), pixels)
        assertNull(decodeEmulatorFrame(Image.getDefaultInstance()))
        assertFailsWith<IllegalArgumentException> { decodeEmulatorFrame(frame().toBuilder().setImage(ByteString.EMPTY).build()) }
        assertFailsWith<IllegalArgumentException> { decodeEmulatorFrame(frame().toBuilder().setFormat(frame().format.toBuilder().setWidth(Int.MAX_VALUE)).build()) }
    }

    @Test fun `discovery matches serial and requires a valid authenticated endpoint`() {
        val ini = File(root, "pid_123.ini")
        ini.writeText("port.serial=5554\ngrpc.port=8554\ngrpc.token=private-token=with-padding\n")
        val endpoint = EmulatorEndpoint.discover("emulator-5554", listOf(root))!!
        assertEquals(8554, endpoint.port)
        assertEquals("private-token=with-padding", endpoint.token)
        assertNull(EmulatorEndpoint.discover("emulator-5556", listOf(root)))
        ini.writeText("port.serial=5554\ngrpc.port=8554\n")
        assertNull(EmulatorEndpoint.discover("emulator-5554", listOf(root)))
        ini.writeText("port.serial=5554\ngrpc.port=99999\ngrpc.token=token\n")
        assertNull(EmulatorEndpoint.discover("emulator-5554", listOf(root)))
    }

    @Test fun `stream authenticates requests raw pixels and cancels when collection ends`() = runBlocking {
        val receivedHeaders = CompletableFuture<String>()
        val cancelled = CompletableFuture<Boolean>()
        val released = CompletableFuture<Boolean>()
        val touches = java.util.Collections.synchronizedList(mutableListOf<TouchEvent>())
        val service = ServerServiceDefinition.builder("android.emulation.control.EmulatorController")
            .addMethod(screenshotStreamMethod, ServerCalls.asyncServerStreamingCall<ImageFormat, Image> { request, observer ->
                assertEquals(ImageFormat.ImgFormat.RGBA8888, request.format)
                (observer as ServerCallStreamObserver<Image>).setOnCancelHandler { cancelled.complete(true) }
                observer.onNext(frame())
            })
            .addMethod(touchMethod, ServerCalls.asyncUnaryCall<TouchEvent, Empty> { request, observer ->
                touches.add(request)
                if (request.touchesList.single().pressure == 0) released.complete(true)
                observer.onNext(Empty.getDefaultInstance())
                observer.onCompleted()
            }).build()
        val server = NettyServerBuilder.forAddress(InetSocketAddress("localhost", 0))
            .addService(ServerInterceptors.intercept(service, object : io.grpc.ServerInterceptor {
                override fun <ReqT : Any, RespT : Any> interceptCall(call: io.grpc.ServerCall<ReqT, RespT>, headers: Metadata,
                    next: io.grpc.ServerCallHandler<ReqT, RespT>): io.grpc.ServerCall.Listener<ReqT> {
                    receivedHeaders.complete(headers.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)))
                    return next.startCall(call, headers)
                }
            })).build().start()
        try {
            val device = FakeAndroid(root).device("Stream")
            val stream = EmulatorScreenStream { EmulatorEndpoint(server.port, "test-token") }
            val firstFrame = CompletableDeferred<androidx.compose.ui.graphics.ImageBitmap>()
            val collection = launch { stream.frames(device, "emulator-5554").collect { firstFrame.complete(it) } }
            try {
                assertEquals(2, withTimeout(5000) { firstFrame.await() }.width)
                assertEquals("Bearer test-token", receivedHeaders.get(5, TimeUnit.SECONDS))
                withContext(Dispatchers.IO) { stream.touch("emulator-5554", 123, 456, true) }
            } finally { collection.cancelAndJoin() }
            assertTrue(cancelled.get(5, TimeUnit.SECONDS))
            assertTrue(released.get(5, TimeUnit.SECONDS))
            assertEquals(listOf(1024, 0), touches.map { it.touchesList.single().pressure })
            assertTrue(touches.all { it.touchesList.single().let { touch -> touch.x == 123 && touch.y == 456 } })
        } finally {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS)
        }
    }
}
