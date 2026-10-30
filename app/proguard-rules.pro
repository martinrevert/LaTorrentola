# =====================================================================
# LaTorrentola - Optimized R8 / ProGuard Configuration
# =====================================================================

# 1. Attributes & Annotations
# Preserve essential attributes for reflection, generics, and stack traces
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions

# 2. Kotlinx Serialization
# Retain serializer metadata while allowing R8 to inline, merge, and optimize
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowoptimization @kotlinx.serialization.Serializable class * { *; }

# 3. Gson
# Preserve only fields explicitly annotated with @SerializedName
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 4. Firestore / Cloud Sync Models
# Retain constructor and property getters/setters for models mapped by Firestore
-keep @com.google.firebase.firestore.IgnoreExtraProperties class * {
    public <init>();
    <fields>;
    <methods>;
}

# 5. Native / JNI Entry Points
# Preserve JNI wrappers invoked from libtorrent4j and libnfs C/C++ libraries
-keep class org.libtorrent4j.swig.libtorrent_jni { *; }
-keep class com.martinrevert.latorrentola.service.storage.remote.LibNfsNative { *; }
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# 6. Google Cast Framework
# Google Cast reflection needs the constructor of the options provider declared in Manifest
-keep class com.martinrevert.latorrentola.service.AppCastOptionsProvider {
    public <init>();
}

# 7. Release Logging Optimization
# Completely strip Verbose and Debug logging calls and string overhead in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

-assumenosideeffects class com.martinrevert.latorrentola.utils.AppLogger {
    public void v(...);
    public void d(...);
}

# 8. Third-Party Warning Suppressions (-dontwarn)
# Suppress references to missing desktop Java classes in SMB / BouncyCastle / Retrofit
-dontwarn java.rmi.UnmarshalException
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
-dontwarn com.hierynomus.**
-dontwarn org.bouncycastle.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn com.google.mlkit.**
