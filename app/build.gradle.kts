import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
    }
}

android {
    namespace = "app.yougram"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.yougram"
        minSdk = 31
        targetSdk = 36

        versionCode = 23
        versionName = "0.9.2"

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

        buildConfigField(
            "String",
            "UPDATE_URL",
            "\"${localProps.getProperty("UPDATE_URL") ?: ""}\""
        )
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    // Compose BOM 2025.09.00 в†’ Compose 1.9.1
    implementation(platform(libs.androidx.compose.bom))
    // Material 3 Expressive (LoadingIndicator, MotionScheme, ButtonDefaults.shapes) есть только в material3 1.4+.
    // Побеждает более новая версия BOM; когда libs.versions.toml обновится до 2025.09.00, эту строку можно убрать.
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    // Явная версия: в логе сборки видна ранняя alpha material3 (MaterialExpressiveTheme там internal), стабильная 1.4.0 новее.
    implementation("androidx.compose.material3:material3:1.4.0")

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)

    // TDLib Coroutines 12.0.1
    implementation(libs.tdl.coroutines)

    implementation(libs.ntgcalls)

    implementation("com.airbnb.android:lottie-compose:6.6.7")

    // Lua-плагины (LuaPluginManager, PluginManager).
    implementation("org.luaj:luaj-jse:3.0.1")
}