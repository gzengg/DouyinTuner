# Xposed / LSPosed 入口类必须保留
-keep class com.mod.xiaojueding.XposedHook { *; }
-keep class com.mod.xiaojueding.MainActivity { *; }

# Xposed API
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# 保留注解与反射相关成员
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
