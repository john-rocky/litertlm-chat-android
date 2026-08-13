# Inference Compliance Review — litert_lm_android_sample_app (step 5.4 audit, 2026-08-13)

Copied from `references/compliance_checklist_inference.md`. `K` = `src/main/java/com/litertlm/chat/ChatEngine.kt`, `MA` = `src/main/java/com/litertlm/chat/MainActivity.kt`, `M` = `src/main/AndroidManifest.xml`. Real-device evidence from a Pixel 8a run (2026-08-13, litertlm-android 0.16.0 AAR).

| Requirement | Status | Evidence |
| :--- | :--- | :--- |
| **Setup & Build** | | |
| GPU OpenCL Permissions (`AndroidManifest.xml`) | Pass | M:10 `<uses-native-library android:name="libOpenCL.so" android:required="false" />` inside `<application>` |
| APK Verification (`zipinfo`) | Pass | `zipinfo bazel-bin/litertlm_chat.apk 'lib/*'` → `lib/arm64-v8a/liblitertlm_jni.so` (21,529,648 bytes) |
| **Inference API & Optimization** | | |
| Data Classes vs Builders Used | Pass | K:90–96,105–110,120–123 `EngineConfig(modelPath=…, backend=…, visionBackend=…, cacheDir=…)` named-arg constructors; no `.builder()` anywhere (`grep -n builder` = empty) |
| Explicit `initialize()` Called | Pass | K:98,113,126 `engine.initialize()` before every `createConversation()` |
| Conversation Class Used (not Session) | Pass | K:99,114,127 `engine.createConversation()`; K:46 `conversation.sendMessageAsync(...)`; no `createSession` in app code |
| MTP Enabled for GPU | Skipped | No MTP parameter exists in litertlm-android 0.16.0 `EngineConfig` (javap: 7 params — modelPath, backend, visionBackend, audioBackend, maxNumTokens, maxNumImages, cacheDir); nothing to enable at this API level |
| **Multi-modal API** | | |
| Initialize Fallback Try-Catch Block | Pass | K:88–131 cascading try-catch: stage 1 mm-on-selected → stage 1.5 text-GPU+vision-CPU (device-measured addition, see note) → stage 2 mm-CPU → stage 3 text-only CPU; UI backend re-toggle MA:173–177 (`rbCpu.isChecked/rbGpu.isChecked` per `effectiveBackend`) |
| Multi-modal Executors (CPU Audio) | Skipped (measured deviation) | Setting `audioBackend=Backend.CPU()` on an audio-less bundle fails conversation creation with `NOT_FOUND: TF_LITE_AUDIO_ENCODER_HW not found in the model` (Pixel 8a logcat 08-13 14:38:06, LiteRtLmJniException) — the checklist assumes audio-capable bundles. `audioBackend` is therefore deliberately omitted for vision-only bundles (K comment at stage 1); `visionBackend` IS explicitly initialized (K:93,108) |
| Content Order (Text before Media) | Pass | K:37–39 `Content.Text` appended before `Content.ImageFile`, with the rule cited in a comment |
| Real Inference Used | Pass | No mock code remains (`grep -n mock K` = doc comment only); real on-device answer: user sent baseball.jpg + "Describe this photo in one sentence." → model streamed "A baseball player is getting ready to hit a ball with a bat." (docs_working_screenshot.png; engine loaded "on **gpu**. This model accepts images.") |

Device note (beyond the skill's headless assumption): full flow verified on a physical Pixel 8a — model picked via DocumentsUI, image via Google Photos photopicker, engine = LFM2.5-VL-450M_int4.litertlm with text graph on OpenCL GPU and vision encoder on CPU. The SigLIP2 vision encoder does not compile on the Pixel GPU delegate (`vision_litert_compiled_model_executor.cc:247` → `litert_compiled_model.h:1895`), which is why the cascade carries the text-GPU + vision-CPU stage.
