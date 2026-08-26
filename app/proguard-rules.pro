# kotlinx.serialization keeps generated serializers via companion objects.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.cursoid.data.net.** {
    *** Companion;
}
-keepclasseswithmembers class dev.cursoid.data.net.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# WorkManager instantiates workers reflectively by class name.
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# OkHttp bundles optional platform integrations that are absent on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
