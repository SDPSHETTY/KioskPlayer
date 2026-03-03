# KioskPlayer ProGuard Rules
# Generated for Android release build security

##---------------Begin: proguard configuration for ExoPlayer/Media3  ----------
# Keep ExoPlayer classes used by the application
-keep class com.google.android.exoplayer2.** { *; }
-keep class androidx.media3.** { *; }
-dontwarn com.google.android.exoplayer2.**
-dontwarn androidx.media3.**

# Keep MediaSource implementations
-keep class * implements androidx.media3.exoplayer.source.MediaSource$Factory { *; }
-keep class * implements androidx.media3.extractor.ExtractorsFactory { *; }

# Keep Renderer implementations
-keep class * extends androidx.media3.exoplayer.Renderer { *; }
-keep class * implements androidx.media3.exoplayer.Renderer { *; }

# Keep DataSource implementations
-keep class * extends androidx.media3.datasource.DataSource { *; }
-keep class * implements androidx.media3.datasource.DataSource$Factory { *; }

##---------------End: proguard configuration for ExoPlayer/Media3  ----------

##---------------Begin: proguard configuration for Android components  ----------
# Keep broadcast receivers (they need to be accessible by the system)
-keep class com.esper.kioskplayer.ManagedConfigReceiver { *; }
-keep class com.esper.kioskplayer.DebugConfigReceiver { *; }
-keep class com.esper.kioskplayer.ControlReceiver { *; }

# Keep MainActivity and its lifecycle methods
-keep class com.esper.kioskplayer.MainActivity { *; }

# Keep configuration classes used by managed configuration
-keep class com.esper.kioskplayer.KioskConfig { *; }
-keep class com.esper.kioskplayer.KioskConfig$* { *; }

# Keep classes with native methods
-keepclasseswithmembernames class * { native <methods>; }

# Keep parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep serializable classes
-keep class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
##---------------End: proguard configuration for Android components  ----------

##---------------Begin: general optimization rules  ----------
# Optimize and obfuscate but keep crash reports readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Remove logging in release builds for security
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Keep enum classes and their values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
##---------------End: general optimization rules  ----------
