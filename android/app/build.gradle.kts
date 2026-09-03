import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

// local.properties → BuildConfig 주입 (커밋 금지 파일에서 시크릿 로드)
// 팀원별 로컬 값이 다를 수 있으므로 없어도 빌드는 통과시키고 빈 문자열로 폴백.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val jusoApiKey: String = localProperties.getProperty("JUSO_API_KEY", "")

android {
    namespace = "com.example.on_safe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.on_safe"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        // BuildConfig 클래스 자동 생성 활성화 (BASE_URL 등 빌드 타입별 상수에 사용)
        buildConfig = true
    }

    buildTypes {
        debug {
            // 에뮬레이터: 10.0.2.2 = 개발 PC의 localhost
            // 실기기 테스트 시 개발 PC의 실제 IP 주소로 변경 필요 (예: "http://192.168.x.x:8080/")
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
            // Python AI 서버(docker-compose 기준 8000 포트) WS 스트림 엔드포인트. BASE_URL과 동일한 컨벤션.
            buildConfigField("String", "AI_WS_URL", "\"ws://10.0.2.2:8000/ws/stream\"")
            // devices API는 Python 서버 전용 — Kotlin 중복분은 스펙 v4.2에서 제거
            buildConfigField("String", "AI_BASE_URL", "\"http://10.0.2.2:8000/\"")
            buildConfigField("String", "JUSO_API_KEY", "\"$jusoApiKey\"")
        }
        release {
            // TODO: 출시 전 백엔드 팀에서 제공한 실제 운영 서버 URL로 변경 필요
            buildConfigField("String", "BASE_URL", "\"https://api.neulbom.com/\"")
            // TODO: 출시 전 백엔드 팀에서 제공한 실제 운영 AI 서버 WS URL로 변경 필요
            buildConfigField("String", "AI_WS_URL", "\"wss://api.neulbom.com/ws/stream\"")
            // TODO: 출시 전 실제 운영 AI 서버 URL로 변경 필요.
            //       현재 BASE_URL과 동일한 값이라, 게이트웨이가 /api/devices/*를 Python으로
            //       프록시하지 않으면 404 → 보호자 홈 기기 ID가 공란이 된다 (앱은 실패를 무시).
            buildConfigField("String", "AI_BASE_URL", "\"https://api.neulbom.com/\"")
            buildConfigField("String", "JUSO_API_KEY", "\"$jusoApiKey\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.flexbox)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.video)
    implementation(libs.mediapipe.tasks.vision)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ViewModel — 뷰모델 클래스, viewModelScope, by viewModels() 델리게이트에 필요
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.ktx)

    // 보안: 토큰 암호화 저장 (EncryptedSharedPreferences)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}