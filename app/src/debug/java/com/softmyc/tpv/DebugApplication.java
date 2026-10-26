package com.softmyc.tpv;

import android.app.Application;
import android.os.StrictMode;

/**
 * Solo en la variante debug: registra en logcat (etiqueta StrictMode) el acceso a disco o red en el hilo principal y
 * los objetos Closeable o cursores SQLite sin cerrar. No interrumpe la app (solo penaltyLog).
 * Uso: adb logcat -s StrictMode
 */
public class DebugApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build());
    }
}
