# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.jsnu.laundry.**$$serializer { *; }
-keepclassmembers class com.jsnu.laundry.** {
    *** Companion;
}
-keepclasseswithmembers class com.jsnu.laundry.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 华为 AGC / CloudDB：保留实体类、注解与 SDK，避免反射/序列化失败
-keep class com.huawei.agconnect.** { *; }
-keep class com.hianalytics.android.** { *; }
-dontwarn com.huawei.agconnect.**
-keep class com.jsnu.laundry.data.cloud.** { *; }
