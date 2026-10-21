# Keep source file names and line numbers for readable Crashlytics stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: type-safe navigation routes are @Serializable objects.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
