# The instrumentation APK is minified with the app, and androidx.test refers to
# compile-only annotations and JDK types that do not exist on the phone.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.lang.model.**
