# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
-dontwarn com.mpatric.mp3agic.**
-keep class com.mpatric.mp3agic.** { *; }
