// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    // 여기서는 classpath 에만 올려두고(apply false), 실제 적용은 app 모듈에서
    // google-services.json 존재 여부를 보고 조건부로 한다.
    alias(libs.plugins.google.services) apply false
}