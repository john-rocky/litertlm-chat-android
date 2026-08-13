package com.litertlm.chat

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Real LiteRT-LM engine behind the same facade the UI used in the mock phase.
 *
 * Initialization follows the cascading fallback required by
 * references/inference_implementation.md §2C:
 *   1. multi-modal on the selected backend (audio strictly CPU)
 *   2. multi-modal strictly on CPU
 *   3. text-only strictly on CPU
 * [effectiveBackend] reports what actually loaded so the UI can re-toggle the
 * selector; [supportsVision] reflects whether a vision-capable config loaded.
 */
class ChatEngine private constructor(
    private val engine: Engine,
    private val conversation: Conversation,
    val effectiveBackend: String,
    val supportsVision: Boolean,
) {

    /** Streams the full-text-so-far via [onUpdate]; returns true on success. */
    suspend fun generate(prompt: String, image: File?, onUpdate: (String) -> Unit): Boolean {
        val parts = mutableListOf<Content>()
        // Content order rule: Text BEFORE media (inference_implementation.md §5).
        if (prompt.isNotEmpty()) parts.add(Content.Text(prompt))
        if (image != null) parts.add(Content.ImageFile(image.absolutePath))
        if (parts.isEmpty()) return false
        val contents = Contents.of(parts)

        return suspendCancellableCoroutine { cont ->
            val acc = StringBuilder()
            try {
                conversation.sendMessageAsync(contents, object : MessageCallback {
                    override fun onMessage(message: Message) {
                        val text = message.contents.contents
                            .filterIsInstance<Content.Text>()
                            .joinToString("") { it.text }
                        acc.append(text)
                        onUpdate(acc.toString())
                    }

                    override fun onDone() {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(t: Throwable) {
                        Log.e("ChatEngine", "generation failed", t)
                        if (cont.isActive) cont.resume(false)
                    }
                })
            } catch (t: Throwable) {
                Log.e("ChatEngine", "sendMessageAsync threw", t)
                if (cont.isActive) cont.resume(false)
            }
            cont.invokeOnCancellation { runCatching { conversation.cancelProcess() } }
        }
    }

    fun close() {
        runCatching { conversation.close() }
        runCatching { engine.close() }
    }

    companion object {
        private const val TAG = "ChatEngine"

        private fun backendOf(name: String): Backend =
            if (name == "gpu") Backend.GPU() else Backend.CPU()

        /**
         * Cascading create. Returns null only if all three stages fail.
         * Runs blocking native init — call from Dispatchers.IO.
         */
        fun create(model: File, requestedBackend: String, cacheDir: File): ChatEngine? {
            // Stage 1: multi-modal on the selected backend, audio strictly CPU.
            try {
                val config = EngineConfig(
                    modelPath = model.absolutePath,
                    backend = backendOf(requestedBackend),
                    visionBackend = backendOf(requestedBackend),
                    // audioBackend deliberately omitted: setting it makes the engine
                    // require a TF_LITE_AUDIO_ENCODER section, which vision-only
                    // bundles (e.g. LFM2.5-VL) do not carry.
                    cacheDir = cacheDir.absolutePath,
                )
                val engine = Engine(config)
                engine.initialize()
                return ChatEngine(engine, engine.createConversation(), requestedBackend, true)
            } catch (t: Throwable) {
                Log.w(TAG, "multi-modal on $requestedBackend failed; trying text-$requestedBackend + vision-CPU", t)
            }
            // Stage 1.5: text on the selected backend, vision on CPU. On Android
            // the LFM2.5-VL text graph delegates fully to OpenCL while the
            // SigLIP2 vision encoder does not compile on the GPU delegate —
            // vision is a one-shot per image, so CPU vision + GPU text is the
            // right split for this family.
            if (requestedBackend == "gpu") {
                try {
                    val config = EngineConfig(
                        modelPath = model.absolutePath,
                        backend = Backend.GPU(),
                        visionBackend = Backend.CPU(),
                        cacheDir = cacheDir.absolutePath,
                    )
                    val engine = Engine(config)
                    engine.initialize()
                    return ChatEngine(engine, engine.createConversation(), "gpu", true)
                } catch (t: Throwable) {
                    Log.w(TAG, "text-gpu + vision-cpu failed; falling back to multi-modal CPU", t)
                }
            }
            // Stage 2: multi-modal strictly on CPU.
            try {
                val config = EngineConfig(
                    modelPath = model.absolutePath,
                    backend = Backend.CPU(),
                    visionBackend = Backend.CPU(),
                    cacheDir = cacheDir.absolutePath,
                )
                val engine = Engine(config)
                engine.initialize()
                return ChatEngine(engine, engine.createConversation(), "cpu", true)
            } catch (t: Throwable) {
                Log.w(TAG, "multi-modal on CPU failed; falling back to text-only CPU", t)
            }
            // Stage 3: text-only strictly on CPU.
            return try {
                val config = EngineConfig(
                    modelPath = model.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = cacheDir.absolutePath,
                )
                val engine = Engine(config)
                engine.initialize()
                ChatEngine(engine, engine.createConversation(), "cpu", false)
            } catch (t: Throwable) {
                Log.e(TAG, "text-only CPU also failed", t)
                null
            }
        }
    }
}
