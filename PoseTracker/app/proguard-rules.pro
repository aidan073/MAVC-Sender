# Keep MediaPipe classes
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mediapipe.tasks.** { *; }

# Keep Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Navigation Safe Args
-keepnames class * extends androidx.navigation.NavArgs
