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
-keep class com.martinrevert.latorrentola.model.** { *; }

# Retrofit 2
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep @retrofit2.http.* interface * { <methods>; }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class com.martinrevert.latorrentola.Dagger* { *; }
-keep class com.martinrevert.latorrentola.Hilt* { *; }
-keep class com.martinrevert.latorrentola.**_HiltComponents* { *; }
-keep class com.martinrevert.latorrentola.**_HiltComponents$* { *; }
-keep class com.martinrevert.latorrentola.**$*CImpl { *; }
-keep class com.martinrevert.latorrentola.**$SwitchingProvider { *; }
-keep class com.martinrevert.latorrentola.**_MembersInjector { *; }
-keep class com.martinrevert.latorrentola.**_Factory { *; }
-keep class com.martinrevert.latorrentola.**_ProvidesAdapter { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    @javax.inject.Inject <init>(...);
}
-keepclassmembers class com.martinrevert.latorrentola.** {
  @javax.inject.Inject <fields>;
  @javax.inject.Inject <methods>;
}
-keep @javax.inject.Singleton class * { *; }

# Hilt Navigation Compose
-keep class androidx.hilt.navigation.compose.** { *; }
-keep interface androidx.hilt.navigation.compose.** { *; }

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

# Keep everything in model package
-keep class com.martinrevert.latorrentola.model.** { *; }

# Keep data classes to ensure hashCode/equals/copy are preserved
-keepclassmembers class com.martinrevert.latorrentola.** {
    public *** get*();
    public *** set*();
    public *** component*();
    public *** copy*(...);
    public boolean equals(java.lang.Object);
    public int hashCode();
    public java.lang.String toString();
}

# Keep everything in utils package (Managers)
-keep class com.martinrevert.latorrentola.utils.** { *; }

# Keep everything in di package
-keep class com.martinrevert.latorrentola.di.** { *; }
