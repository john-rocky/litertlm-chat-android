# UI Compliance Review — litert_lm_android_sample_app (step 3.4 audit, 2026-08-13)

Copied from `references/compliance_checklist_ui.md`; audited against the build that produced `bazel-bin/litertlm_chat.apk` (appbuild9, EXIT=0, 16 actions).
Paths below: `K` = `litert_lm_android_sample_app/src/main/java/com/litertlm/chat/MainActivity.kt`, `X` = `litert_lm_android_sample_app/src/main/res/layout/activity_main.xml`, `M` = `litert_lm_android_sample_app/src/main/AndroidManifest.xml`, `B` = `litert_lm_android_sample_app/BUILD`, `MOD` = `litert_lm_android_sample_app/MODULE.bazel`.

| Requirement | Status | Evidence |
| :--- | :--- | :--- |
| **Setup & Build** | | |
| Workspace Directory Name | Pass | Workspace root `litert_lm_android_sample_app/` holds MODULE.bazel + BUILD (`ls`; task.md Environment section) |
| Strictly Bazel 9 (No Gradle files generated) | Pass | `.bazelversion` = `9.0.0`; no `*.gradle` in tree (`find . -name '*.gradle'` = empty) |
| SDK Version (`minSdkVersion` >= 24) | Pass | M:5 `minSdkVersion="31"` |
| SDK Version (`targetSdkVersion` >= 35) | Pass | M:5 `targetSdkVersion="35"` |
| Bzlmod Config (Bazel 9, no WORKSPACE) | Pass | No WORKSPACE file exists; MODULE.bazel lines 1–45 define all deps (rules_android 0.7.1, rules_kotlin 2.3.20, rules_java 7.12.2, rules_jvm_external 6.2, rules_android_ndk 0.1.5) |
| Bazel Platform Flag Setup (ARM64 Only) | Pass | task.md Environment section line "Mandatory bazel flag …--android_platforms=@rules_android//:arm64-v8a"; used in appbuild4–9 command lines (/tmp/appbuild9.log header) |
| AppCompat Avoidance (Extends `Activity`) | Pass | K:30 `class MainActivity : Activity()`; no androidx.appcompat in MODULE.bazel artifacts |
| Bzlmod SDK Configuration Fallback | Pass | MOD `android_sdk_repository_extension.configure(path=…/Library/Android/sdk, api_level=35, build_tools_version="35.0.0")` |
| Source Compilation Target | Pass | B:4 `kt_android_library(name="app_lib", srcs=glob([src/main/java/**/*.kt]))` |
| Binary Dependency Verification | Pass | B: `android_binary(name="litertlm_chat", deps=[":app_lib"])` |
| Edge-to-Edge Handling | Pass | K:68 `ViewCompat.setOnApplyWindowInsetsListener` padding systemBars+ime |
| **UI State & Flow** | | |
| Initial State (inputs disabled except picker) | Pass | K:132–138 `applyState`: picker enabled only in INITIAL; rbCpu/rbGpu/btnImage/btnAudio/etInput/btnSend gated; called K:113 |
| Model Picked State | Pass | K:132 `btnPickModel.isEnabled = state == UiState.INITIAL` → disabled once MODEL_COPYING |
| Backend Selected State | Pass | K:133–134 radios enabled only in BACKEND_WAIT; onBackendChosen → ENGINE_LOADING disables |
| Backend Selector Options | Pass | X:53 `rb_cpu` "CPU", X:59 `rb_gpu` "GPU" |
| Backend Label Alignment | Pass | X:44 "Backend:" TextView placed left of RadioGroup in horizontal LinearLayout |
| Backend Default Selection | Pass | X RadioGroup has no `checkedButton`; neither RadioButton has `checked="true"` |
| Inference State (disable all during inference) | Pass | K applyState: only READY enables inputs; INFERRING set in onSend before generate |
| Post-Inference State | Pass | K onSend completion path `applyState(UiState.READY)` on both ok and fail branches |
| Loading Animation: File to Cache | Pass | K:143–145 `startLoadingAnimation()` around `withContext(Dispatchers.IO){copyToCache}` |
| Loading Animation: Model in Engine | Pass | K:162 `startLoadingAnimation()` in onBackendChosen around ChatEngine.create |
| Loading Animation: Waiting for Response | Pass | K onSend `startLoadingAnimation()`; stopped on first streamed chunk |
| Shared Animation Helper Used | Pass | Single helper K:startLoadingAnimation/stopLoadingAnimation (K:~289–314), used by all three states |
| Active Animation Tracking Verified | Pass | Animation stopped by `stopLoadingAnimation()` on operation completion (K:146, 163, first-chunk path), not by fixed timers; dots loop is `while(isActive)` cancelled by Job |
| **Layout & Components** | | |
| Model Picker UI | Pass | K:89–93 `Intent(ACTION_GET_CONTENT)`, `type="*/*"` , `startActivityForResult(REQ_PICK_MODEL)` |
| Model Label Alignment | Pass | X:tv_model_name in same horizontal LinearLayout right of btn_pick_model |
| Model Filename Display | Pass | K `tvModelName.text = copied.name` where name resolved via OpenableColumns (K:322) |
| Strict Layout Order | Pass | X top→bottom: pick-model row → backend row → sv_conversation → image/audio row → input+send row (matches diagram) |
| Conversation Display Layout Weight | Pass | X:72 ScrollView `layout_weight="1"`, height 0dp |
| Conversation Display Layout | Pass | X:69 ScrollView id sv_conversation wrapping vertical LinearLayout ll_conversation; images use K:228/253 `adjustViewBounds = true` |
| Auto-Scroll | Pass | K:283 `svConversation.post { svConversation.fullScroll(View.FOCUS_DOWN) }` called from scrollDown() on add/stream |
| Markdown Support (Markwon) | Pass | MOD artifact `io.noties.markwon:core:4.6.2`; K:86 `Markwon.create`, used in addSystemNote + final answer render |
| Explicit UI Contrast & ForceDark Checked | Pass | X:9 `android:forceDarkAllowed="false"` on root; explicit textColor/background on buttons (X:24,99,106,124), labels (X:33,47), radios (X:57,63), EditText (X:117–119), conversation bg X:73 |
| **Multi-modal UI & File Handling** | | |
| Text-only Fallback Buttons Disabling | Pass | K:135 `btnImage.isEnabled = ready && modelIsMultimodal`; K:136 audio kept disabled |
| Image Picker (Storage Select) | Pass | K:96–100 ACTION_GET_CONTENT `image/*` via startActivityForResult |
| Audio Picker (Storage Select) | Pass | K:103–107 ACTION_GET_CONTENT `audio/*` via startActivityForResult |
| Images Rendered Visually | Pass | K:onImagePicked adds ImageView with decoded bitmap immediately on selection; also rendered in user bubble at send (K:addUserBubble) |
| Audio Clearly Indicated (Filename/Icon) | Pass | K:onAudioPicked adds note "Audio attached: 🎵 `<name>`" immediately on selection |
| Audio Filename Resolution (`OpenableColumns`) | Pass | K:322 copyToCache resolves `OpenableColumns.DISPLAY_NAME` for every picked file incl. audio |
| Background File Operations | Pass | K:145/220 and onAudioPicked: all copyToCache calls inside `withContext(Dispatchers.IO)` |
| File Extension & Caching Preservation | Pass | K:copyToCache writes to `cacheDir` (K:325) under the resolved DISPLAY_NAME (original extension preserved); native calls receive `File` paths, never content:// |

All items Pass; no Pending/Fail rows remain. Build evidence: /tmp/appbuild9.log `Build completed successfully` with `--android_platforms=@rules_android//:arm64-v8a`.
