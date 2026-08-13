# Dependency Compliance Review — litert_lm_android_sample_app (step 4.4 audit, 2026-08-13)

Scenario: **Maven Integration** (recorded step 2.2). Copied from `references/compliance_checklist_dependency.md`.
Paths: `MOD` = `litert_lm_android_sample_app/MODULE.bazel`, `B` = `litert_lm_android_sample_app/BUILD`.

| Requirement | Status | Evidence |
| :--- | :--- | :--- |
| **Common Setup & Build** | | |
| No Source Copy (No C++ files in app dir) | Pass | `find litert_lm_android_sample_app -name '*.cc' -o -name '*.cpp' -o -name '*.h'` = empty; only Kotlin sources under src/main/java |
| Library Dependency Verification | Pass | B `kt_android_library(deps=[... "@maven//:com_google_ai_edge_litertlm_litertlm_android"])` |
| **Source Build Specifics** | | |
| Repo Check | Skipped | Maven scenario (step 2.2 recorded input); no source build |
| Target Arch Verification | Skipped | Maven scenario |
| Transitive Deps (Source Build) | Skipped | Maven scenario |
| Dual JAR Import Verification | Skipped | Maven scenario |
| Prebuilt Folder Check | Skipped | Maven scenario |
| **Maven Integration Specifics** | | |
| Maven Coordinate Verification | Pass | MOD artifact `com.google.ai.edge.litertlm:litertlm-android:latest.release`; resolved to 0.16.0 — fetched AAR path `.../maven.google.com/com/google/ai/edge/litertlm/litertlm-android/0.16.0/litertlm-android-0.16.0.aar` (bazel repo cache) |
| Maven Transitive Deps | Pass | maven.install repositories `https://maven.google.com` + `https://repo1.maven.org/maven2`, `version_conflict_policy="pinned"`, `use_starlark_android_rules=True`; resolution errors absent in /tmp/appbuild10.log (`Build completed successfully`) |

Extra (environment-specific, recorded for reproducibility): NDK 27.3.13750724 via `rules_android_ndk` 0.1.5 required for the arm64 platform CC toolchain; on a machine whose `xcode-select` points at an Xcode BETA, bazel host tool compiles fail with "absolute path inclusion(s)" until `--repo_env/--action_env/--host_action_env DEVELOPER_DIR` all point at the stable Xcode (litert_lm_android_sample_app/.bazelrc lines 1–4; failing logs /tmp/appbuild{1,4,6,7}.log, first green /tmp/appbuild8.log).
