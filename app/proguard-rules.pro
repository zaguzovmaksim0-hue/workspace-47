# Compose/application code is optimized. Preserve reflection-driven signing engines.
-keep class org.bouncycastle.** { *; }
-keep class org.apache.xml.security.** { *; }
-keep class com.tom_roush.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Optional library-only surfaces absent in the pre-existing Android runtime:
# PDFBox JP2 image rendering is optional; signing does not decode/render page images.
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
# SLF4J 1.7 explicitly falls back to NOP when no binding is installed.
-dontwarn org.slf4j.impl.StaticLoggerBinder
# Build-time OSGi metadata on Woodstox providers, not an Android runtime service.
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
# Santuario is used only for DOM canonicalization, not its optional StAX/JSR105 APIs.
-dontwarn javax.xml.stream.**
-dontwarn javax.xml.crypto.MarshalException
