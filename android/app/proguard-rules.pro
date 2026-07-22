# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ── 크래시 스택 추적을 위한 소스 정보 보존 ──────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Retrofit / OkHttp ────────────────────────────────────────────────────
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# ── Gson / DTO 직렬화 클래스 ─────────────────────────────────────────────
# 네트워크 DTO 패키지 전체 유지 (Gson 리플렉션 필요)
-keep class com.example.on_safe.network.dto.** { *; }
-keepclassmembers class com.example.on_safe.network.dto.** { *; }

# ── 보안: EncryptedSharedPreferences ─────────────────────────────────────
-keep class androidx.security.crypto.** { *; }

# ── Kotlin Coroutines ─────────────────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**