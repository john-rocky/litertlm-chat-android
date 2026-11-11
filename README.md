# litertlm-chat-android

An Android app that loads a `.litertlm` model from the phone's storage and chats with it on the device, with text and photos. It is about 500 lines of Kotlin on Google's [`litertlm-android`](https://maven.google.com/web/index.html#com.google.ai.edge.litertlm:litertlm-android), short enough to read as sample code. [`ChatEngine.kt`](litert_lm_android_sample_app/src/main/java/com/litertlm/chat/ChatEngine.kt) is where a photo reaches the model.

<p align="center">
<img src="docs/screenshot.png" alt="The app on a Pixel 8a with LFM2.5-VL-450M, answering a question about a baseball photo" width="300">
</p>

I built it to try LFM2.5-VL with photos on an Android phone. The screenshot is from an earlier build that drew the photo twice; the app now draws it once.

## Models I ran on a Pixel 8a

These worked in the app in August 2026, on `litertlm-android` 0.16.0:

| Model from [litert-community](https://huggingface.co/litert-community) | Size | Loaded on | Sample answer |
|---|---|---|---|
| [LFM2.5-VL-450M](https://huggingface.co/litert-community/LFM2.5-VL-450M) int4 | 407 MB | `gpu`: text on the GPU, vision on the CPU | "A baseball player is getting ready to hit a ball with a bat." |
| [LFM2.5-VL-1.6B](https://huggingface.co/litert-community/LFM2.5-VL-1.6B) int4 | 1.3 GB | `gpu`: text on the GPU, vision on the CPU | "A baseball player is getting ready to swing his bat." |
| [FastVLM-0.5B](https://huggingface.co/litert-community/FastVLM-0.5B) | 1.16 GB | `gpu` | "A baseball player in an orange and white uniform … is poised to swing at a pitch, while a catcher … crouches behind him." |
| [SmolVLM2-500M](https://huggingface.co/litert-community/SmolVLM2-500M) | 361 MB | `gpu` | "A baseball player in a red uniform is swinging at a ball while a catcher in a yellow uniform watches." |
| [PaddleOCR-VL-1.6](https://huggingface.co/litert-community/PaddleOCR-VL-1.6) | 1.39 GB | `gpu` | Read all three lines of a sign: "CLOSED BACK AT 3 PM" |

On the LFM2.5-VL models, only the top quarter of the photo reaches the model, so questions about position come out wrong ([LiteRT-LM#3246](https://github.com/google-ai-edge/LiteRT-LM/issues/3246), still open).

The app copies each model to its cache, so leave free space of about twice the file size.

Two larger models did not run in the app on this 8 GB phone. While loading Qwen2-VL-2B (1.78 GB), the app's process was killed, on both backends. I couldn't test LFM2.5-VL-3B int4 (2.35 GB): the phone's disk was over 97 % full, and its storage manager kept removing the file.

## Build

There is no prebuilt APK. To build it with Bazel 9, you need the Android SDK with API 35 and build-tools 35.0.0, NDK 27.3.13750724, and `bazelisk`. In `litert_lm_android_sample_app/MODULE.bazel`, set `path` in `android_sdk_repository_extension.configure` and `android_ndk_repository_extension.configure` to your SDK and NDK. Then run:

```bash
cd litert_lm_android_sample_app
bazelisk build //:litertlm_chat \
  --android_platforms=@rules_android//:arm64-v8a \
  --cxxopt=-std=c++17 --host_cxxopt=-std=c++17
adb install -r bazel-bin/litertlm_chat.apk
```

I built it on a Mac, so `.bazelrc` sets `DEVELOPER_DIR` to `/Applications/Xcode.app/Contents/Developer`. If your Xcode is elsewhere, or you build on another system, change or delete those entries.

`MODULE.bazel` takes the latest `litertlm-android` release, which is now newer than the 0.16.0 I ran, and I have not built against a newer one. To get 0.16.0, write `0.16.0` in place of `latest.release`. The LFM2.5-VL-450M, SmolVLM2-500M and PaddleOCR-VL-1.6 files on the Hub have been replaced since those runs, and their model cards say the new files need LiteRT-LM 0.19.0 or later on the Android GPU. With 0.16.0, use the earlier files the cards link; the SmolVLM2-500M and PaddleOCR-VL-1.6 ones were edited after my runs (tokenizer and metadata).

## Usage

1. Push or download a `.litertlm` model to the device (e.g. from [litert-community](https://huggingface.co/litert-community)).
2. **Pick Model** → choose the file (it is copied to the app cache; 400 MB takes a few seconds).
3. Pick **CPU** or **GPU**. If the model can't load that way, the app falls back step by step, down to text only on the CPU, and shows what loaded. If Android kills the process, there is no fallback.
4. **Image** → pick a photo → type a question → **Send**.

The **Audio** button is disabled, because audio isn't wired up yet. The runtime problems I worked around are in [the details](docs/details.md).

## License

Apache-2.0 for the code. Each model has its publisher's license, and the LFM2.5-VL family is under the LFM Open License v1.0.
