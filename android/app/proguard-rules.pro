# UniFFI and JNA native bridges
-keep class uniffi.** { *; }
-keep class com.sun.jna.** { *; }

# Media3 & Codec hardware modules
-keep class com.spaceslimmer.videohw.** { *; }
-keep class com.spaceslimmer.audiohw.** { *; }
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-dontwarn com.sun.jna.**
-dontwarn java.awt.**
-dontwarn javax.annotation.**

# Preserve annotations and signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep annotated members
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}

# Keep any native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

