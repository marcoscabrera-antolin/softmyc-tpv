# Reglas de Ofuscación ProGuard / R8 para SoftMYC

# 1. Preservar Clases de Dominio (Modelos de dominio y JSON)
-keep class com.softmyc.tpv.domain.** { *; }
-keepclassmembers class com.softmyc.tpv.domain.** {
    <fields>;
    <init>();
    *** get*();
    *** set*(***);
}

# 2. Preservar Entidades de Base de Datos Local Room y DAOs
-keep class com.softmyc.tpv.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class com.softmyc.tpv.data.local.Converters { *; }

# 3. Preservar Librería de Impresión Térmica ESC/POS (JitPack)
-keep class com.dantsu.escaposprinterlibrary.** { *; }
-keepclassmembers class com.dantsu.escaposprinterlibrary.** { *; }

# 4. Atributos necesarios para la serialización JSON con Gson (genéricos y anotaciones)
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 5. Eliminar en release los registros de depuración (pueden contener URL, tenant o ids de pedido)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# 6. SQLCipher para Android (Zetetic): clases y métodos nativos JNI de la base cifrada
-keep,includedescriptorclasses class net.zetetic.database.** { *; }
-keep,includedescriptorclasses interface net.zetetic.database.** { *; }
-keepclasseswithmembernames class net.zetetic.database.** {
    native <methods>;
}
-dontwarn net.zetetic.database.**
