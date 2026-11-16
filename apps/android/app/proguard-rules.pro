# HOPA keeps: the ADB stack and BouncyCastle reach into their own classes at
# runtime, and kotlinx.serialization generates serializers that R8 must see.
-keep class io.github.muntashirakon.adb.** { *; }
-keep class com.github.muntashirakon.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-keep class org.bouncycastle.jcajce.provider.** { *; }

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# The hidden-display helper is started by name from `app_process` as shell, so
# nothing in the app references it and R8 would remove it. Keep the entry point;
# everything it drives is then reachable.
-keep class com.hopa.capabilities.display.ShellMain { *; }

# The gateway between Compose and the Android views it hosts.
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**
