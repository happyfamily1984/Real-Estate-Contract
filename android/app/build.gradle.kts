plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.happyfamily.contract"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.happyfamily.contract"
        minSdk = 28
        targetSdk = 35
        // CI 에서 -PversionCode=<빌드번호> 로 넘겨준다.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "1.0.${versionCode}"
    }

    // 서명 키는 암호화된 signing.keystore.enc 로만 저장소에 들어 있다.
    // CI 가 GitHub Secret KEYSTORE_PASSWORD 로 풀어서 signing.keystore 를 만든다. (README 참고)
    // 키가 없으면 각 PC 의 기본 디버그 키로 서명한다 (구글 로그인은 안 됨).
    val keystore = file("signing.keystore")
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    val hasReleaseKey = keystore.exists() && !keystorePassword.isNullOrEmpty()

    signingConfigs {
        if (hasReleaseKey) {
            create("shared") {
                storeFile = keystore
                storePassword = keystorePassword
                keyAlias = "contract"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        val signing = if (hasReleaseKey) signingConfigs.getByName("shared") else signingConfigs.getByName("debug")
        debug {
            signingConfig = signing
        }
        release {
            isMinifyEnabled = false
            signingConfig = signing
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // ./gradlew testDebugUnitTest -Proborazzi.record=true 로 화면 스크린샷을 만든다.
                it.systemProperty("roborazzi.test.record", project.findProperty("roborazzi.record") ?: "false")
                it.systemProperty("roborazzi.record.filePathStrategy", "relativePathFromRoborazziContextOutputDirectory")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.ai)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.mlkit.document.scanner)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
