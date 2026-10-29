# Floating Apps for Android

Project Kotlin untuk Android 10+ dengan katalog aplikasi, izin overlay, kontrol jendela/bubble, dan percobaan peluncuran freeform bila didukung OS.

**Batasan platform:** Android tidak menyediakan API publik untuk menanam Activity arbitrer dari aplikasi lain ke dalam `WindowManager` overlay pihak ketiga. `Intent` hanya meluncurkan Activity sebagai task; mode freeform melalui `ActivityOptions.setLaunchBounds()` diabaikan bila perangkat tidak mendukung freeform/PiP windowing. Karena itu aplikasi akan mencoba jendela native freeform dan fallback ke peluncuran normal, dengan kontrol overlay. Dukungan untuk setiap aplikasi tidak bisa dijamin di perangkat Android 10.
