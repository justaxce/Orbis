# R8 Proguard rules for Orbis
-keep class com.floating.virtualwindow.** { *; }

# Shizuku reflection preservation
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# HiddenApiBypass
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**

-dontwarn java.lang.invoke.**
-dontwarn sun.misc.**
