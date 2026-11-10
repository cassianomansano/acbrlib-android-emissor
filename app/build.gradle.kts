plugins {
    id("com.android.application")
}

android {
    namespace = "br.com.calunaty.acbremissor"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.calunaty.acbremissor"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Bibliotecas nativas alinhadas em 16 KB (Android 15+): nao usar useLegacyPackaging.
    packaging { jniLibs { useLegacyPackaging = false } }
}

dependencies {
    // AARs da ACBrLib para Android (nao versionados): copie para app/libs. Veja app/libs/LEIA-ME.md.
    implementation(files("libs/ACBrLibComum-release.aar"))
    implementation(files("libs/ACBrLibNFe-release.aar"))
    implementation("net.java.dev.jna:jna:5.18.1@aar")
}
