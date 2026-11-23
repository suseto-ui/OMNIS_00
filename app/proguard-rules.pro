# ==============================================================================
# O.M.N.I.S. APEX v4.5.0-GA — Production R8 / ProGuard Configuration
# ==============================================================================

# Preserve stack trace metadata and annotations
-keepattributes SourceFile,LineNumberTable,*Annotation*,InnerClasses,Signature,RuntimeVisibleAnnotations,AnnotationDefault
-renamesourcefileattribute SourceFile

# Keep generated Moshi & Retrofit API contracts across release builds
-keep class com.example.api.** { *; }
-keep @com.squareup.moshi.JsonClass class com.example.api.** { *; }
-keep interface com.example.**Service { *; }
-keep interface com.example.**Api { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# Keep kotlinx.serialization & Navigation Compose type-safe routes
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.**$$serializer { *; }
-keepclassmembers class com.example.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep UI navigation tabs & Credential Manager / Google Identity SDK
-keep class com.example.ui.OmnisTab { *; }
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Keep Room entities/DAOs and SQLCipher JNI bindings
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep O.M.N.I.S. domain, defense, security, cognitive kernel, gateway, and auth models
-keep class com.example.data.** { *; }
-keep class com.example.defense.** { *; }
-keep class com.example.security.** { *; }
-keep class com.example.engine.** { *; }
-keep class com.example.gateway.** { *; }
-keep class com.example.auth.** { *; }

# OkHttp / Retrofit / Coroutines warnings suppression
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# Firebase, App Check Play Integrity & WorkManager
-keep class com.google.firebase.** { *; }
-keep class androidx.work.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn javax.annotation.**
