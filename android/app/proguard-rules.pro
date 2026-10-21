# Keep source file names and line numbers for readable Crashlytics stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: type-safe navigation routes are @Serializable objects.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Typed navigation routes are looked up through their kotlinx.serialization serializers at runtime.
-keepclassmembers @kotlinx.serialization.Serializable class com.tomessa.miff.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
