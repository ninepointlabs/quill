plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ninepointlabs.quill"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.ninepointlabs.quill"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}

val cargoBuildTask = tasks.register<Exec>("cargoBuild") {
    val ndkHome = File(System.getProperty("user.home"), ".local/share/mise/installs/android-sdk/latest/ndk/28.2.13676358").absolutePath
    val toolchain = "$ndkHome/toolchains/llvm/prebuilt/linux-x86_64"
    val api = 26
    
    environment("NDK", ndkHome)
    environment("TOOLCHAIN", toolchain)
    environment("API", api.toString())
    environment("CC_aarch64_linux_android", "$toolchain/bin/aarch64-linux-android${api}-clang")
    environment("AR_aarch64_linux_android", "$toolchain/bin/llvm-ar")
    environment("CFLAGS_aarch64_linux_android", "--sysroot=$toolchain/sysroot")
    environment("ANDROID_NDK_HOME", ndkHome)
    
    workingDir = File(project.rootDir, "nostrdb-bridge")
    commandLine("cargo", "build", "--target", "aarch64-linux-android")
}

val copyRustLibsTask = tasks.register<Copy>("copyRustLibs") {
    dependsOn(cargoBuildTask)
    from(File(project.rootDir, "nostrdb-bridge/target/aarch64-linux-android/debug/libnostrdb_bridge.so"))
    into(File(project.projectDir, "src/main/jniLibs/arm64-v8a"))
}

tasks.named("preBuild") {
    dependsOn(copyRustLibsTask)
}

android {
    sourceSets {
        getByName("main") {
            jniLibs.srcDir("src/main/jniLibs")
        }
    }
}
