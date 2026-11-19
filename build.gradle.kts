// AGP 9 compila Kotlin in modo nativo ("built-in Kotlin").
// La riga kotlin.android serve solo a fissare la versione di Kotlin (non viene applicata).
plugins {
    id("com.android.application") version "9.3.3" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
}
