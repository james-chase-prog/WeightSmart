// build.gradle.kts (Project: WeightSmart)
plugins {
    id("com.android.application") version "8.11.2" apply false
    id("com.android.library") version "8.11.2" apply false

    // Kotlin 2.0 + KSP
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    id("org.jetbrains.kotlin.kapt") version "2.3.0" apply false
    id("com.google.devtools.ksp") version "2.3.4" apply false

    // Hilt
    id("com.google.dagger.hilt.android") version "2.58" apply false
}