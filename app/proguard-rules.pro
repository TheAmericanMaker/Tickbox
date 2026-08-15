# Room generates implementations reflectively at runtime.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Tesseract and Leptonica are reached from JNI; native code looks these up by name,
# which R8 cannot see.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
