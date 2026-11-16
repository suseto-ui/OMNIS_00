# Add project specific ProGuard rules here.
# See http://developer.android.com/guide/developing/tools/proguard.html

# Keep generated Moshi models used for API contracts across release builds.
-keep class com.example.api.** { *; }
-keep @com.squareup.moshi.JsonClass class com.example.api.** { *; }

# Keep kotlinx.serialization & Navigation Compose type-safe routes
-keepattributes *Annotation*,InnerClasses
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

# Keep Room entities/DAOs and SQLCipher JNI bindings
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Firestore / Firebase serialized models & Audit data classes
-keep class com.example.data.** { *; }
-keep class com.example.defense.** { *; }
-keep class com.example.security.** { *; }
-keep class com.example.engine.** { *; }
-keep class com.example.gateway.** { *; }
-keep class com.example.auth.** { *; }

# Moshi & Retrofit / OkHttp reflection safety
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**

# Firebase, App Check Play Integrity & WorkManager
-keep class com.google.firebase.** { *; }
-keep class androidx.work.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
