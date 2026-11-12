# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
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
# ---- Grand Arbiter ---------------------------------------------------------------------------
# Readable crash stack traces in Play Console (with the mapping file in the AAB).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: the library ships its own rules; these keep the generated serializers
# of OUR models (rules bundle, manifest, Scryfall) even under R8 full mode, as its README advises.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class es.alejandro.grandarbiter.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class es.alejandro.grandarbiter.**$$serializer { *; }

# Room, Koin (constructor DSL, no reflection), OkHttp, Coil and Play In-App Updates ship their
# own consumer rules: nothing to add here.

# Play In-App Review (review-ktx 2.0.2) refers to this Play services annotation without
# depending on the library that has it. An annotation only: nothing runs without it.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
