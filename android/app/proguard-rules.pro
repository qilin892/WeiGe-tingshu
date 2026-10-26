# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Media3
-dontwarn androidx.media3.**

# Compose
-dontwarn androidx.compose.**

# 书源里用反射读取规则字段，模型整体保留
-keep class com.weige.tingshu.model.** { *; }
