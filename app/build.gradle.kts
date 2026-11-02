import java.util.Properties
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
    }
}

androidComponents {
    onVariants { variant ->
        // Правим байткод tdl-coroutines (см. buildSrc/TdJsonPatchFactory.kt и TdJsonFix.kt).
        variant.instrumentation.transformClassesWith(TdJsonPatchFactory::class.java, InstrumentationScope.ALL) { }
        variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COPY_FRAMES)
    }
}

android {
    namespace = "app.yougram"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.yougram"
        minSdk = 31
        targetSdk = 36

        versionCode = 36
        versionName = "1.3.3"

        buildConfigField(
            "int",
            "TG_API_ID",
            localProps.getProperty("TG_API_ID") ?: "0"
        )

        buildConfigField(
            "String",
            "TG_API_HASH",
            "\"${localProps.getProperty("TG_API_HASH") ?: ""}\""
        )

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        buildConfigField(
            "String",
            "UPDATE_URL",
            "\"${localProps.getProperty("UPDATE_URL") ?: ""}\""
        )

        // Шаблон чата локальной ИИ-модели: gemma | chatml (должен совпадать с файлом модели).
        // Меняется в local.properties без правки кода; по умолчанию gemma.
        val aiTemplate = (localProps.getProperty("AI_MODEL_TEMPLATE") ?: "gemma").trim().lowercase()
        require(aiTemplate == "gemma" || aiTemplate == "chatml") {
            "AI_MODEL_TEMPLATE в local.properties: допустимо gemma или chatml, получено '$aiTemplate'"
        }
        buildConfigField("String", "AI_MODEL_TEMPLATE", "\"$aiTemplate\"")

        // Прямая ссылка на файл модели (.task) на нашем хостинге и его точный размер в байтах (проверка целостности).
        fun prop(name: String): String? = localProps.getProperty(name)
        buildConfigField("String", "AI_URL_QWEN", "\"${prop("AI_URL_QWEN") ?: prop("AI_MODEL_URL") ?: ""}\"")
        buildConfigField("long", "AI_SIZE_QWEN", "${prop("AI_SIZE_QWEN") ?: prop("AI_MODEL_SIZE") ?: "0"}L")
        buildConfigField("String", "AI_URL_GEMMA", "\"${prop("AI_URL_GEMMA") ?: ""}\"")
        buildConfigField("long", "AI_SIZE_GEMMA", "${prop("AI_SIZE_GEMMA") ?: "0"}L")
        buildConfigField("String", "AI_URL_DEEPSEEK", "\"${prop("AI_URL_DEEPSEEK") ?: ""}\"")
        buildConfigField("long", "AI_SIZE_DEEPSEEK", "${prop("AI_SIZE_DEEPSEEK") ?: "0"}L")

        buildConfigField(
            "String",
            "GOOGLE_FONTS_API_KEY",
            "\"${localProps.getProperty("GOOGLE_FONTS_API_KEY") ?: ""}\""
        )
    }

    signingConfigs {
        create("release") {
            val pass = localProps.getProperty("KS_PASS")
            val keystore = rootProject.file("yougram.jks")

            if (pass != null && keystore.exists()) {
                storeFile = keystore
                storePassword = pass
                keyAlias = "yougram"
                keyPassword = pass
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")

            isMinifyEnabled = true
            isShrinkResources = true

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        )
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

dependencies {
    // Нативный код (плагины, tgcalls/libyougram_voip) вынесен в модуль :cpp
    implementation(project(":plugin-host"))
    implementation(project(":voip"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    // Compose BOM 2025.09.00 (Compose 1.9.x), версия задаётся в libs.versions.toml
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation("androidx.compose.ui:ui-text-google-fonts")

    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.common)
    implementation(libs.zxing.core)

    implementation(libs.tdl.coroutines)

    implementation("com.airbnb.android:lottie-compose:6.6.7")

    implementation("androidx.graphics:graphics-shapes:1.1.0")

    // Локальная ИИ-модель (.task). Только для запуска на устройстве.
    implementation("com.google.mediapipe:tasks-genai:0.10.24")
}
// Видео-рендерер из Telegram (org.webrtc.TextureViewRenderer) тянет их UI-классы LiteMode и VoIPService.
// Видеозвонков в Yougram нет, поэтому файл не компилируем; сам исходник остаётся на месте и переживает повторное копирование.
tasks.withType<JavaCompile>().configureEach {
    exclude("org/webrtc/TextureViewRenderer.java")
}