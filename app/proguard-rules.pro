# ---------------------------------------------------------------------------
# Speak — release shrinking rules
# ---------------------------------------------------------------------------

# JNI entry points are called from C++ by name. R8 cannot see those call sites,
# so without this the whole native bridge is stripped and the app crashes on
# first use with UnsatisfiedLinkError.
-keepclasseswithmembernames,includedescriptorclasses class com.speak.app.stt.WhisperBridge {
    native <methods>;
}
-keepclasseswithmembernames,includedescriptorclasses class com.speak.app.llm.LlamaBridge {
    native <methods>;
}
-keep class com.speak.app.stt.WhisperBridge { *; }
-keep class com.speak.app.llm.LlamaBridge { *; }

# Room generates an implementation that is looked up reflectively.
-keep class com.speak.app.data.db.SpeakDatabase_Impl { *; }

# kotlinx.serialization keeps its generated serializers on the companion.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# The backup format is data the user keeps; renaming its fields would silently
# break every previously exported file.
-keep,allowobfuscation,allowshrinking class com.speak.app.data.backup.BackupManager$* { *; }

# Room, DataStore and Compose ship their own consumer rules; nothing more needed.
