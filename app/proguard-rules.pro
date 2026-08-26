# kotlinx.serialization keeps generated serializers via companion objects.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.agentsforcursor.data.net.** {
    *** Companion;
}
-keepclasseswithmembers class dev.agentsforcursor.data.net.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp bundles optional platform integrations that are absent on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
