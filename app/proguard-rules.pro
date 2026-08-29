# JarvisHA ProGuard Rules

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Room entities
-keep class uk.org.retallack.jarvis.data.db.** { *; }

# Keep TFLite model classes
-keep class org.tensorflow.lite.** { *; }

# Suppress missing Error Prone annotations used by Google Tink / security-crypto
-dontwarn com.google.errorprone.annotations.**
