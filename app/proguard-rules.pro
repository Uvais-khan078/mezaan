# Meezan ProGuard Rules

# Strip android.util.Log calls from release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Keep the Room-generated code
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase {
    <init>(...);
}

# Moshi rules
-keep class com.example.meezan.data.api.models.** { *; }
-keep class com.example.meezan.data.entities.** { *; }
