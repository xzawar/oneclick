# Stored app data (kotlinx.serialization) — keep generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class pk.oneclick.app.** { *** Companion; *; }
-keep class pk.oneclick.app.**$$serializer { *; }
-dontwarn org.osmdroid.**
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
