# Ktor + kotlinx.serialization keep rules. FOLD ships no reflection-heavy code
# of its own, so this list stays deliberately short.
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.jeelgajera.fold.**$$serializer { *; }
-keepclassmembers class com.jeelgajera.fold.** { *** Companion; }

-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-keep class io.ktor.server.cio.** { *; }

# Room generated implementations.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
