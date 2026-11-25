plugins {
    id("com.android.application") version "9.4.0" apply false
    // AGP 9 heeft Kotlin ingebouwd: de plugin org.jetbrains.kotlin.android hoort hier niet meer.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    // KSP 2.3.x ondersteunt AGP 9 (built-in Kotlin); oudere 2.2.x-versies weigeren dat.
    id("com.google.devtools.ksp") version "2.3.11" apply false
}
