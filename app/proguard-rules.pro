# Project specific ProGuard rules

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions
-keepclassmembers class com.martinrevert.latorrentola.** {
    *** Companion;
    *** $serializer;
}
-keep,allowobfuscation,allowoptimization @kotlinx.serialization.Serializable class com.martinrevert.latorrentola.** { *; }
-keepnames class kotlinx.serialization.internal.GeneratedSerializer { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.annotations.SerializedName { *; }

# Firestore / Models
# Firestore uses reflection to map documents to these classes.
-keep class com.martinrevert.latorrentola.model.** { *; }

# Retrofit 2 / OKHttp
# Consumer rules are bundled with Retrofit 2.9.0+ and OkHttp 4.0+.
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

# Keep entry points for reflection-heavy utilities if needed
-keep class com.martinrevert.latorrentola.utils.** {
  public <fields>;
  public <methods>;
}

# Keep the HiltViewModel annotation usage for safety
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
