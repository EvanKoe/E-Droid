# kotlinx.serialization: keep generated serializers for the index models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.edroid.**$$serializer { *; }
-keepclassmembers class app.edroid.** { *** Companion; }
-keepclasseswithmembers class app.edroid.** { kotlinx.serialization.KSerializer serializer(...); }
