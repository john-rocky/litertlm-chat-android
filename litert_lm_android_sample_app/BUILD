load("@rules_android//rules:rules.bzl", "android_binary")
load("@rules_kotlin//kotlin:android.bzl", "kt_android_library")

kt_android_library(
    name = "app_lib",
    srcs = glob(["src/main/java/**/*.kt"]),
    custom_package = "com.litertlm.chat",
    manifest = "src/main/AndroidManifest.xml",
    resource_files = glob(["src/main/res/**"]),
    deps = [
        "@maven//:androidx_core_core_ktx",
        "@maven//:io_noties_markwon_core",
        "@maven//:com_google_ai_edge_litertlm_litertlm_android",
        "@maven//:org_jetbrains_kotlinx_kotlinx_coroutines_android",
    ],
)

android_binary(
    name = "litertlm_chat",
    custom_package = "com.litertlm.chat",
    manifest = "src/main/AndroidManifest.xml",
    deps = [":app_lib"],
)
