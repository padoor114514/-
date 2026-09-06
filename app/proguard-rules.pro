# 高德地图 Android SDK 混淆保留规则（R8 会混淆库内部实现类 com.amap.api.col.**，
# 高德 native 层按原类名查找，混淆会导致 createSurface 崩溃，因此整包 keep）
-keep class com.amap.api.** { *; }
-keep class com.autonavi.** { *; }
-keep class com.loc.** { *; }
-dontwarn com.amap.api.**
-dontwarn com.autonavi.**
-dontwarn com.loc.**
