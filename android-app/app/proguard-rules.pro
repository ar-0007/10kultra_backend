# ============================================================
# Apna TV — R8 / ProGuard rules (production release)
# Obfuscates + shrinks the app while keeping everything that is
# accessed reflectively (Gson models, Retrofit, enums) intact.
# ============================================================

# ---- Keep attributes needed by Gson / Retrofit reflection ----
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keep class kotlin.Metadata { *; }

# ---- App data models (de)serialized by Gson — keep names & fields ----
# API DTOs (Stalker portal + dashboard backend), all @SerializedName-annotated.
-keep class com.apnatv.iptv.data.api.models.** { *; }
# Domain models — cached to disk via Gson (ContentCache: List<Category>, etc.)
# and passed across navigation (VodItem), so field names must survive.
-keep class com.apnatv.iptv.domain.model.** { *; }
# Navigation payload serialized with Gson.
-keep class com.apnatv.iptv.presentation.screens.vod.VodDetailArgs { *; }

# Any class with @SerializedName fields — keep those members regardless of package.
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Enums are matched by name (DataStore stores theme/layout/quality .name).
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- Gson ----
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-dontwarn com.google.gson.**

# ---- Retrofit / OkHttp / Okio ----
# Retrofit does reflection on generic parameters and method annotations.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep interface com.apnatv.iptv.data.api.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ---- Kotlin coroutines ----
-dontwarn kotlinx.coroutines.**

# ---- ZXing (on-device QR generation for the setup / connect QRs) ----
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ---- Keep app entry points (manifest-declared) from being stripped ----
-keep class com.apnatv.iptv.MainActivity { *; }
-keep class com.apnatv.iptv.IptvApp { *; }
-keep class com.apnatv.iptv.data.update.UpdateInstallReceiver { *; }
