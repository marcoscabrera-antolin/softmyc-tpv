# Revisión de concurrencia e hilos (TASK 2.2)

Revisión estática del 16-oct-2026 (`grep` de `new Thread`, `Executors.`, `synchronized`, `volatile` en `app/src/main/java`), tests de concurrencia y una pasada de `StrictMode` en la tablet (sección siguiente).

## Hilos y ejecutores

| Dónde | Mecanismo | Ciclo de vida |
|---|---|---|
| `AppExecutors.disco()` | Un hilo daemon compartido (`softmyc-disco`) | Vive con el proceso; lo usan 8 Activities, `LoginViewModel` (inyectable) y `ResumenSemanalCorreo` |
| `PedidoViewModel.diskExecutor` | Un hilo por ViewModel | `onCleared()` lo cierra |
| `StockActivity`, `ArqueoCocinaActivity`, `MainActivity` | Un ejecutor por Activity | Cerrado en `onDestroy` |
| `AjustesActivity.executor` | Un ejecutor por Activity | **Defecto corregido:** no se cerraba nunca (un hilo vivo por cada visita a Ajustes). Ahora `onDestroy` lo cierra; test `AjustesActivityTest.al_destruirse_libera_el_hilo_de_segundo_plano` |
| `SyncJobService` | `new Thread("sync-job")` y un ejecutor de 1 hilo por ciclo | El ejecutor se cierra al terminar el ciclo |
| `BaseActivity.renovarSesionEnSegundoPlano` | `new Thread("renovar-sesion")` | Termina al volver el refresco |
| `PrinterHelper.printerExecutor` | Un hilo estático | Vive con el proceso |
| `AppDatabase.SEEDER_EXECUTOR` | Un hilo daemon para sembrar catálogos | Vive con el proceso |

## Estado compartido y exclusión mutua

- `AppDatabase.getDatabaseForTenant`: comprobación doble con `ConcurrentHashMap` y `synchronized (AppDatabase.class)`. Correcto. Test nuevo `AppDatabaseConcurrenciaTest`: 12 hilos que abren a la vez la misma base obtienen una única instancia; 12 escrituras simultáneas en 3 negocios no se mezclan.
- `Auth`: singletons `volatile` con doble comprobación; el candado es `Auth.class`. `SessionManager.refreshIfStale` es `synchronized (lock)`: una sola renovación para muchas peticiones 401 (`AuthHttpTest.manyConcurrentUnauthorizedRequests_shareASingleRefresh`) y un cierre de sesión durante un refresco no resucita la sesión (`logoutWhileARefreshIsInFlight_doesNotResurrectTheSession`).
- `VentaUploader`: subidas solapadas se serializan y se repite la última (`subidaSolapada_seRepiteAlTerminarLaQueEstabaEnMarcha`).
- `SecurePreferencesHelper`: `CACHE` en `ConcurrentHashMap` y creación bajo `CREATE_LOCK`.

## Riesgos que quedan (sin corregir)

1. **Cambio de negocio durante una operación larga.** `getDatabase(context)` resuelve el negocio activo en cada llamada; una tarea que ya tiene su `dao` sigue en su negocio, pero una que lo pide después del cambio usará el nuevo. `SyncGuard` evita subir ventas de una base local de otro negocio; no hay un bloqueo que impida cambiar de negocio con una venta a medias. Probabilidad baja (el cambio exige acción manual de un administrador).
2. **Un solo hilo de disco para toda la app (`AppExecutors.disco()`).** Una consulta lenta retrasa a las demás. Aceptable con el volumen de un comercio pequeño; no hay medición.
3. **`PedidosWebActivity`** usa su propio `Handler` y callbacks de OkHttp que vuelven a la interfaz con `enUiSiViva`, correcto frente a destrucción de la pantalla.

## StrictMode en la tablet (16-oct-2026)

La variante debug activa ahora StrictMode (`app/src/debug/java/com/softmyc/tpv/DebugApplication.java`, solo `penaltyLog`; ver con `adb logcat -s StrictMode`). Recorrido de cobro, historial, estadísticas, stock, arqueo, carta, gestión de precios, ajustes y monitor de cocina (detalle en `evidencias/mediciones_tablet_2026-10-16.md`):

- **0 violaciones de red y 0 fugas** de `Closeable`, cursores SQLite o Activities.
- **36 violaciones de disco en el hilo principal:** 10 por `SecurePreferencesHelper.createEncrypted` (primera creación de cada fichero cifrado, ≈150 ms una sola vez por fichero), 17 en `Toast.makeText` de `PedidoActivity:229` y `PedidosWebActivity:477` (la lectura ocurre en el framework) y 9 solo de framework.
- **Recomendación (no aplicada):** precalentar `SecurePreferencesHelper.getSessionPrefs/getPrinterPrefs` en `AppExecutors.disco()` desde `SplashActivity` para sacar la primera creación del hilo principal; habría que medirlo antes de adoptarlo.

## No hecho

- No se ha pasado una herramienta de detección de carreras (p. ej. `ThreadSanitizer` no aplica a Java en Android).
