# FinanceMate R8 configuration.

# --- Anthropic Java SDK / Jackson -------------------------------------------
# The SDK binds JSON via Jackson, which resolves properties reflectively. R8
# does not see those accesses and will strip them, producing failures that show
# up only in release builds. Keep the SDK's model types and Jackson's plumbing.
-keep class com.anthropic.** { *; }
-keepclassmembers class com.anthropic.** { *; }
-dontwarn com.anthropic.**

-keep class com.fasterxml.jackson.** { *; }
-keepclassmembers class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Jackson probes for optional modules that are absent on Android.
-dontwarn java.beans.**
-dontwarn org.w3c.dom.bootstrap.**

# --- victools jsonschema-generator (transitive via the Anthropic SDK) ---------
# The SDK ships a helper that derives a JSON schema from a POJO class, used by
# the class-based structured-output overload. That helper depends on
# java.lang.reflect.AnnotatedType, which the Android runtime does not implement,
# so the whole code path is unusable here regardless of keep rules.
#
# FinanceMate therefore always passes an explicit JsonOutputFormat schema and
# never touches the class-based overload. These rules stop R8 failing on the
# resulting dangling references in code we never call.
#
# If you are tempted to switch to the POJO overload for convenience: it will
# compile, pass debug testing, and then throw NoClassDefFoundError on a real
# device. Write the schema out by hand.
-dontwarn com.github.victools.**
-dontwarn java.lang.reflect.AnnotatedType
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedElement

# --- kotlinx.serialization ---------------------------------------------------
# Serializers are generated as companions; the lookup is reflective by name.
-keepclassmembers class dev.financemate.** {
    *** Companion;
}
-keepclasseswithmembers class dev.financemate.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- SQLCipher ---------------------------------------------------------------
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# --- PdfBox-Android ----------------------------------------------------------
-dontwarn org.apache.pdfbox.**
-dontwarn com.tom_roush.**

# --- Kotlin coroutines -------------------------------------------------------
-dontwarn kotlinx.coroutines.**
