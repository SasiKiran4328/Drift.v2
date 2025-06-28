# ProGuard rules for Drift app
-keep class com.google.android.gms.ads.** { *; }
-keep class com.drift.** { *; }
-dontwarn com.google.android.gms.** 

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep ViewModels and LiveData
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# Keep Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep StateFlow and Flow
-keepclassmembers class * {
    @kotlinx.coroutines.flow.StateFlow *;
    @kotlinx.coroutines.flow.Flow *;
}

# Keep AdMob classes
-keep class com.google.android.gms.ads.** {
   public *;
}

# Keep Bluetooth and WiFi related classes
-keep class android.bluetooth.** { *; }
-keep class android.net.wifi.** { *; }

# Keep service classes
-keep class com.drift.service.** { *; }

# Keep ViewBinding
-keep class com.drift.databinding.** { *; }

# Keep custom attributes
-keep class * {
    @androidx.annotation.Keep *;
}

# Optimize string operations
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom exceptions
-keep public class * extends java.lang.Exception

# Remove unused code
-dontwarn android.support.**
-dontwarn androidx.**
-dontwarn org.jetbrains.annotations.**

# Optimize for size
-repackageclasses ''
-allowaccessmodification
-mergeinterfacesaggressively

# Keep reflection for ViewBinding
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions 