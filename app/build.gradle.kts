plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.oshkitech.streamcapture"; compileSdk = 35
 defaultConfig { applicationId = "com.oshkitech.streamcapture"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1" } }

dependencies { implementation("org.jsoup:jsoup:1.18.3") }
