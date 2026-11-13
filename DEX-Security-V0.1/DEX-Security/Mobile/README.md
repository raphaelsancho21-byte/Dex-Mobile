# DEX Mobile

O projeto Android Kotlin está em `app/`.

No GitHub, o workflow compilador é `../.github/workflows/build-android.yml`. Execute-o em **Actions**. O APK de teste é disponibilizado como artefato.

Para build local com Android SDK e Gradle 8.11.1: `gradle :app:assembleDebug`.

Min SDK 26, target 35; Android 13 e Samsung A32 5G são alvo de testes. Não foi executada compilação Android neste ambiente.
