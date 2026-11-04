# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.tripmate.ai.**$$serializer { *; }
-keepclassmembers class com.tripmate.ai.** { *** Companion; }
-keepclasseswithmembers class com.tripmate.ai.** { kotlinx.serialization.KSerializer serializer(...); }
# Ktor / slf4j pulled in by Firebase AI Logic
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
