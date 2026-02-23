// Root build.gradle.kts — AGP 8.11.0 + Kotlin 2.0.20 + KSP

plugins {
    id("com.android.application") version "8.11.2" apply false
    id("com.android.library")    version "8.11.2" apply false

    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.kapt")    version "2.0.20" apply false   // keep for Hilt only
    id("com.google.devtools.ksp")      version "2.0.20-1.0.24" apply false

    id("com.google.dagger.hilt.android") version "2.51.1" apply false
}
