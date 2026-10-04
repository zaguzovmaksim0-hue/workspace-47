# Compose/application code is optimized. Preserve reflection-driven signing engines.
-keep class org.bouncycastle.** { *; }
-keep class org.apache.xml.security.** { *; }
-keep class com.tom_roush.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
