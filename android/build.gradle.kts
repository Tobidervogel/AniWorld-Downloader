// Versionen wie bei Live Up (AGP 8.4.1, Gradle 8.7, SDK 34): dieselbe JDK/SDK-Kombination baut dann auch auf dem PC.
plugins {
    id("com.android.application") version "8.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
