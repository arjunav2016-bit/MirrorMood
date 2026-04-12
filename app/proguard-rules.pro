# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ── Debugging ────────────────────────────────────────────────────
# Preserve line numbers for crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Room Database ────────────────────────────────────────────────
-keep class com.mirrormood.data.db.** { *; }

# ── ML Kit ───────────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }

# ── Hilt / Dagger ────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# ── WorkManager + HiltWorker ─────────────────────────────────────
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }
-keepclassmembers class * {
    @androidx.hilt.work.HiltWorker <init>(...);
}
-keep class androidx.hilt.work.** { *; }

# ── Data classes used in JSON backup ─────────────────────────────
-keep class com.mirrormood.data.WellnessRecommendation { *; }

# ── Kotlin Coroutines ────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── MPAndroidChart ───────────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }
