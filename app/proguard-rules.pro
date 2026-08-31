# Project specific ProGuard rules

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions
-keepclassmembers class com.martinrevert.latorrentola.** {
    *** Companion;
    *** $serializer;
}
# Keep serializable classes but allow obfuscation by default
-keep,allowobfuscation,allowoptimization @kotlinx.serialization.Serializable class com.martinrevert.latorrentola.** { *; }
-keepnames class kotlinx.serialization.internal.GeneratedSerializer { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.annotations.SerializedName { *; }

# Firestore / Models
# Firestore uses reflection to map documents to these classes.
# We must keep the class names and all members (fields/methods) from obfuscation.
# This "hard keep" overrides the more general allowobfuscation rule for these packages.
-keep class com.martinrevert.latorrentola.model.** { *; }
-keepclassmembers class com.martinrevert.latorrentola.model.** {
    <fields>;
    <methods>;
    public <init>(...);
}

# Explicitly keep classes annotated for Firestore
-keep @com.google.firebase.firestore.IgnoreExtraProperties class * { *; }
-keepclassmembers @com.google.firebase.firestore.IgnoreExtraProperties class * { *; }

-keepattributes RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, Signature

# Retrofit 2 / OKHttp
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# Hilt / Dagger
# Rules are automatically generated and applied by the Hilt Gradle Plugin.

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class com.martinrevert.latorrentola.database.** { *; }

# Coil 3
-keep class coil3.** { *; }

# YouTube Player
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }

# Navigation 3
-keep class androidx.navigation3.** { *; }
-keep interface androidx.navigation3.** { *; }
-keep class com.martinrevert.latorrentola.ui.navigation.** { *; }
-keepclassmembers class com.martinrevert.latorrentola.ui.navigation.Route* { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep entry points for reflection-heavy utilities
-keep class com.martinrevert.latorrentola.utils.** {
  public <fields>;
  public <methods>;
}

# Keep the HiltViewModel annotation usage for safety
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
