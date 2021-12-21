plugins {
  id("plugin.base")
  id("com.android.application")
  kotlin("android")
  kotlin("android.extensions")
  kotlin("kapt")
}

android {
  compileSdkVersion(29)
  defaultConfig {
    applicationId = "org.reduxkotlin.example.todos"
    minSdkVersion(26)
    targetSdkVersion(29)
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  buildTypes {
    getByName("release") {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
    }
    getByName("debug") {
      // MPP libraries don't currently get this resolution automatically
      matchingFallbacks = listOf("release")
      isDebuggable = true
    }
  }
  packagingOptions {
    exclude("META-INF/*.kotlin_module")
  }
}

dependencies {
  implementation("androidx.appcompat:appcompat:_")
  implementation("androidx.constraintlayout:constraintlayout:_")
  implementation("androidx.recyclerview:recyclerview:_")

  implementation(project(":examples:todos:todos-common"))
  implementation(project(":redux-kotlin-threadsafe"))
}
