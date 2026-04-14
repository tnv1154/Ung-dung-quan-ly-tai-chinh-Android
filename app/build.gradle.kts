import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

val receiptLocalConfig = Properties().apply {
    val file = rootProject.file("receipt_ai.local.properties")
    if (file.exists()) {
        file.inputStream().use { input -> load(input) }
    }
}
val receiptAiApiUrl = (receiptLocalConfig.getProperty("API_URL") ?: "").trim()
val escapedReceiptAiApiUrl = receiptAiApiUrl.replace("\\", "\\\\").replace("\"", "\\\"")

val releaseSigningConfig = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { input -> load(input) }
    }
}
val releaseStoreFilePath = (releaseSigningConfig.getProperty("storeFile") ?: "").trim()
val releaseStorePassword = (releaseSigningConfig.getProperty("storePassword") ?: "").trim()
val releaseKeyAlias = (releaseSigningConfig.getProperty("keyAlias") ?: "").trim()
val releaseKeyPassword = (releaseSigningConfig.getProperty("keyPassword") ?: "").trim()
val releaseKeystoreFile = releaseStoreFilePath.takeIf { it.isNotEmpty() }?.let { rootProject.file(it) }
val hasReleaseSigningConfig = releaseKeystoreFile?.exists() == true
    && releaseStorePassword.isNotEmpty()
    && releaseKeyAlias.isNotEmpty()
    && releaseKeyPassword.isNotEmpty()

if (!hasReleaseSigningConfig) {
    logger.lifecycle(
        "Release signing is not configured. Create keystore.properties from keystore.properties.example."
    )
}

android {
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "RECEIPT_AI_API_URL", "\"$escapedReceiptAiApiUrl\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                releaseKeystoreFile?.let { storeFile = it }
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}

val customApkBaseName = "Sổ thu chi cá nhân"

listOf("Debug" to "debug", "Release" to "release").forEach { (taskSuffix, outputFolder) ->
    val renameTask = tasks.register("copyRenamed${taskSuffix}Apk") {
        doLast {
            val outputDir = layout.buildDirectory.dir("outputs/apk/$outputFolder").get().asFile
            val sourceApk = outputDir
                .listFiles()
                ?.firstOrNull { file ->
                    file.isFile && file.extension.equals("apk", ignoreCase = true) && file.name.startsWith("app-")
                }
            if (sourceApk != null) {
                sourceApk.copyTo(outputDir.resolve("$customApkBaseName.apk"), overwrite = true)
            }
        }
    }
    tasks.matching { it.name == "assemble$taskSuffix" }.configureEach {
        finalizedBy(renameTask)
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.livedata)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation("androidx.exifinterface:exifinterface:1.4.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.vanniktech:android-image-cropper:4.6.0")
    implementation("androidx.camera:camera-core:1.6.0")
    implementation("androidx.camera:camera-camera2:1.6.0")
    implementation("androidx.camera:camera-lifecycle:1.6.0")
    implementation("androidx.camera:camera-view:1.6.0")
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.google.play.services.auth)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
