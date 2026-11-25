# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Glance ActionCallback constructors invoked via reflection
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}
