# Methods called from the game's JavaScript must keep their names.
-keepclassmembers class com.alexandercbgm.ugomonikota.StoreBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface
