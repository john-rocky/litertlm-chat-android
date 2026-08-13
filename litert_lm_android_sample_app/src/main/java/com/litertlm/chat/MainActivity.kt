package com.litertlm.chat

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.noties.markwon.Markwon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : Activity() {

    private companion object {
        const val REQ_PICK_MODEL = 1
        const val REQ_PICK_IMAGE = 2
        const val REQ_PICK_AUDIO = 3
    }

    private enum class UiState { INITIAL, MODEL_COPYING, BACKEND_WAIT, ENGINE_LOADING, READY, INFERRING }

    private lateinit var btnPickModel: Button
    private lateinit var tvModelName: TextView
    private lateinit var rbCpu: RadioButton
    private lateinit var rbGpu: RadioButton
    private lateinit var svConversation: ScrollView
    private lateinit var llConversation: LinearLayout
    private lateinit var btnImage: Button
    private lateinit var btnAudio: Button
    private lateinit var etInput: EditText
    private lateinit var btnSend: Button
    private lateinit var markwon: Markwon

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var state = UiState.INITIAL
    private var modelFile: File? = null
    private var pendingImageFile: File? = null
    private var loadingJob: Job? = null
    private var loadingView: TextView? = null
    private var modelIsMultimodal = false

    // Engine facade — mock in the UI phase; real LiteRT-LM wiring lands in step 5.
    private var engine: ChatEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<LinearLayout>(R.id.root_layout)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left + 24, bars.top + 24, bars.right + 24, bars.bottom + 24)
            insets
        }

        btnPickModel = findViewById(R.id.btn_pick_model)
        tvModelName = findViewById(R.id.tv_model_name)
        rbCpu = findViewById(R.id.rb_cpu)
        rbGpu = findViewById(R.id.rb_gpu)
        svConversation = findViewById(R.id.sv_conversation)
        llConversation = findViewById(R.id.ll_conversation)
        btnImage = findViewById(R.id.btn_image)
        btnAudio = findViewById(R.id.btn_audio)
        etInput = findViewById(R.id.et_input)
        btnSend = findViewById(R.id.btn_send)
        markwon = Markwon.create(this)

        btnPickModel.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, REQ_PICK_MODEL)
        }
        btnImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, REQ_PICK_IMAGE)
        }
        btnAudio.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "audio/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, REQ_PICK_AUDIO)
        }
        rbCpu.setOnClickListener { onBackendChosen("cpu") }
        rbGpu.setOnClickListener { onBackendChosen("gpu") }
        btnSend.setOnClickListener { onSend() }

        applyState(UiState.INITIAL)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQ_PICK_MODEL -> onModelPicked(uri)
            REQ_PICK_IMAGE -> onImagePicked(uri)
            REQ_PICK_AUDIO -> onAudioPicked(uri)
        }
    }

    // ---------- state machine ----------

    private fun applyState(next: UiState) {
        state = next
        val ready = state == UiState.READY
        btnPickModel.isEnabled = state == UiState.INITIAL
        rbCpu.isEnabled = state == UiState.BACKEND_WAIT
        rbGpu.isEnabled = state == UiState.BACKEND_WAIT
        btnImage.isEnabled = ready && modelIsMultimodal
        btnAudio.isEnabled = false // enabled per-model in step 5 when the family supports audio
        etInput.isEnabled = ready
        btnSend.isEnabled = ready
    }

    private fun onModelPicked(uri: Uri) {
        applyState(UiState.MODEL_COPYING)
        startLoadingAnimation()
        scope.launch {
            val copied = withContext(Dispatchers.IO) { copyToCache(uri) }
            stopLoadingAnimation()
            if (copied == null) {
                addSystemNote("Could not read the selected model file.")
                applyState(UiState.INITIAL)
                return@launch
            }
            modelFile = copied
            tvModelName.text = copied.name
            addSystemNote("Model: `${copied.name}` (${copied.length() / (1024 * 1024)} MB). Choose a backend.")
            applyState(UiState.BACKEND_WAIT)
        }
    }

    private fun onBackendChosen(backend: String) {
        val model = modelFile ?: return
        applyState(UiState.ENGINE_LOADING)
        startLoadingAnimation()
        scope.launch {
            val created = withContext(Dispatchers.IO) { ChatEngine.create(model, backend, cacheDir) }
            stopLoadingAnimation()
            if (created == null) {
                addSystemNote("Engine failed to load `${model.name}` on $backend (all fallbacks exhausted).")
                applyState(UiState.BACKEND_WAIT)
                return@launch
            }
            engine = created
            modelIsMultimodal = created.supportsVision
            if (created.effectiveBackend != backend) {
                // Cascading fallback downgraded the backend; reflect it in the UI.
                rbCpu.isChecked = created.effectiveBackend == "cpu"
                rbGpu.isChecked = created.effectiveBackend == "gpu"
                addSystemNote("Requested **$backend** was not available; running on **${created.effectiveBackend}**.")
            }
            addSystemNote(
                "Loaded on **${created.effectiveBackend}**." +
                    if (modelIsMultimodal) " This model accepts images." else " Text-only model."
            )
            applyState(UiState.READY)
        }
    }

    private fun onSend() {
        val text = etInput.text.toString().trim()
        val image = pendingImageFile
        if (text.isEmpty() && image == null) return
        etInput.setText("")
        pendingImageFile = null
        // The image was already rendered in the conversation at selection time;
        // don't render it a second time in the user bubble.
        addUserBubble(text, null)
        applyState(UiState.INFERRING)
        startLoadingAnimation()
        val answerView = TextView(this).apply {
            setTextColor(0xFF30323A.toInt())
            textSize = 15f
            setPadding(8, 10, 8, 14)
        }
        scope.launch {
            var first = true
            val ok = engine?.generate(text, image) { fullText ->
                scope.launch(Dispatchers.Main) {
                    if (first) {
                        first = false
                        stopLoadingAnimation()
                        llConversation.addView(answerView)
                    }
                    answerView.text = fullText
                    scrollDown()
                }
            } ?: false
            withContext(Dispatchers.Main) {
                if (first) stopLoadingAnimation()
                if (!ok) addSystemNote("Generation failed.") else {
                    markwon.setMarkdown(answerView, answerView.text.toString())
                }
                applyState(UiState.READY)
            }
        }
    }

    private fun onImagePicked(uri: Uri) {
        scope.launch {
            val copied = withContext(Dispatchers.IO) { copyToCache(uri) }
            if (copied == null) {
                addSystemNote("Could not read the selected image.")
                return@launch
            }
            pendingImageFile = copied
            // Render the picked image in the conversation immediately.
            val iv = ImageView(this@MainActivity).apply {
                adjustViewBounds = true
                setImageBitmap(BitmapFactory.decodeFile(copied.absolutePath))
                setPadding(8, 10, 8, 4)
            }
            llConversation.addView(iv)
            addSystemNote("Image attached: `${copied.name}` — it will be sent with your next message.")
        }
    }

    private fun onAudioPicked(uri: Uri) {
        scope.launch {
            val copied = withContext(Dispatchers.IO) { copyToCache(uri) }
            if (copied == null) {
                addSystemNote("Could not read the selected audio file.")
                return@launch
            }
            addSystemNote("Audio attached: 🎵 `${copied.name}` (this model family does not consume audio; kept for display only).")
        }
    }

    // ---------- conversation display ----------

    private fun addUserBubble(text: String, image: File?) {
        if (image != null) {
            val iv = ImageView(this).apply {
                adjustViewBounds = true
                setImageBitmap(BitmapFactory.decodeFile(image.absolutePath))
                setPadding(8, 10, 8, 4)
            }
            llConversation.addView(iv)
        }
        if (text.isNotEmpty()) {
            val tv = TextView(this).apply {
                setTextColor(0xFF5B4FC4.toInt())
                textSize = 15f
                setPadding(8, 8, 8, 10)
                this.text = "You: $text"
            }
            llConversation.addView(tv)
        }
        scrollDown()
    }

    private fun addSystemNote(md: String) {
        val tv = TextView(this).apply {
            setTextColor(0xFF6A6E7A.toInt())
            textSize = 13f
            setPadding(8, 6, 8, 6)
        }
        markwon.setMarkdown(tv, md)
        llConversation.addView(tv)
        scrollDown()
    }

    private fun scrollDown() {
        svConversation.post { svConversation.fullScroll(View.FOCUS_DOWN) }
    }

    // Shared loading animation: "loading." -> "loading.." -> "loading..." at 500 ms/dot,
    // rendered inside the conversation display (single helper for every loading state).
    private fun startLoadingAnimation() {
        if (loadingView != null) return
        val tv = TextView(this).apply {
            setTextColor(0xFF8A8D98.toInt())
            textSize = 14f
            setPadding(8, 6, 8, 6)
            text = "loading."
        }
        loadingView = tv
        llConversation.addView(tv)
        scrollDown()
        loadingJob = scope.launch {
            var dots = 1
            while (isActive) {
                withContext(Dispatchers.Main) { tv.text = "loading" + ".".repeat(dots) }
                delay(500)
                dots = if (dots >= 3) 1 else dots + 1
            }
        }
    }

    private fun stopLoadingAnimation() {
        loadingJob?.cancel()
        loadingJob = null
        loadingView?.let { llConversation.removeView(it) }
        loadingView = null
    }

    // ---------- file plumbing ----------

    private fun copyToCache(uri: Uri): File? {
        return try {
            var name = "picked.bin"
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
            }
            val dst = File(cacheDir, name)
            if (dst.exists() && dst.length() > 0) return dst
            contentResolver.openInputStream(uri)?.use { input ->
                dst.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            dst
        } catch (e: Exception) {
            null
        }
    }
}
