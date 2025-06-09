plugins {
    alias(libs.plugins.android.application)
    // Add Firebase plugins
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.taskflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.taskflow"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    // Enable view binding
    buildFeatures {
        viewBinding = true
    }
    
    // Fix packaging conflicts
    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
     
    // AndroidX Preference library
    implementation("androidx.preference:preference:1.2.1")
    
    // Add CircleImageView dependency
    implementation("de.hdodenhof:circleimageview:3.1.0")
    
    // Add SwipeRefreshLayout dependency
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    
    // Google Sign In dependencies - Updated for compatibility
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    
    // Google API Client dependencies
    implementation("com.google.api-client:google-api-client-android:1.33.0")
    implementation("com.google.http-client:google-http-client-gson:1.42.0")
    
    // Google Tasks API dependencies - Fixed version to one that exists in Maven
    implementation("com.google.apis:google-api-services-tasks:v1-rev20210709-1.32.1")
    
    // Google Play Services Tasks (needed for Task<T> class)
    implementation("com.google.android.gms:play-services-tasks:18.1.0")
    
    // Google Drive API dependencies
    implementation("com.google.android.gms:play-services-drive:17.0.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20211107-1.32.1") {
        exclude(group = "org.apache.httpcomponents")
    }
    
    // Gmail API for email sending
    implementation("com.google.apis:google-api-services-gmail:v1-rev20220404-1.32.1")
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    
    // Core dependencies for notifications
    implementation("androidx.core:core:1.12.0")
    
    // Glide for image loading
    implementation("com.github.bumptech.glide:glide:4.15.1")
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.1")
    
    // Firebase and Firestore dependencies - Updated to latest
    implementation(platform("com.google.firebase:firebase-bom:32.8.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    
    // Play Services Base
    implementation("com.google.android.gms:play-services-base:18.3.0")
    
    // Room components
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

// Play services version checker
apply(plugin = "com.google.gms.google-services")