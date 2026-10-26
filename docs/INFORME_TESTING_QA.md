# Informe de testing y QA — SoftMYC TPV

Fecha de ejecución: 2026-10-16 · Rama `main` · Responsable: sesión automatizada (Claude Code) bajo supervisión del autor.

## 1. Ficha del entorno

| Elemento | Valor |
|---|---|
| Dispositivo | Samsung SM-X210R (tablet física, USB/adb) |
| Android | 16 (API 36) |
| Pantalla | 1200×1920 px, 240 dpi (uso apaisado) |
| RAM | 7,2 GiB (`MemTotal` 7 578 172 kB) |
| App | `com.softmyc.tpv`, `debug`, minSdk 26 / compileSdk 35 |
| Negocio de pruebas | `paqueteria` (sesión demo creada por `QaSupport`) |
| Impresora en pruebas | `127.0.0.1:9` (inaccesible a propósito) |
| Herramientas | JUnit4, Robolectric (sdk 34), Mockito, `FakeApiServer` (OkHttp real), Espresso 3.5.1, UiAutomator 2.3.0, Monkey, `dumpsys` |

## 2. Resumen ejecutivo

| Capa | Pruebas | Resultado |
|---|---|---|
| Unitarias/integración JVM (`testDebugUnitTest`) | 904 en 110 clases | 904 PASS · 0 FAIL · 0 omitidas (tras la ronda de correcciones del 2026-10-16) |
| Instrumentadas en tablet (`com.softmyc.tpv.qa`) | 29 | 29 PASS · 0 FAIL en 3 pasadas seguidas (2026-10-16, tablet desbloqueada); JVM también 3 pasadas seguidas en verde |
| Monkey (4 ejecuciones, 5 500 eventos, con y sin WiFi) | 4 | 0 caídas, 0 ANR |
| Permisos `pm grant/revoke` (Bluetooth) | 1 | sin fallos |
| Cobertura de líneas (JaCoCo, solo JVM) | 85,8 % (7 330 de 8 543 líneas; antes ≈ 39–43 %) | objetivo del 80 % superado por 5,8 puntos (ver §7) |

Defectos encontrados por esta campaña: 2 (1 corregido, 1 abierto) más 2 observaciones (§6).

## 3. Catálogo de casos

### 3.1 Instrumentados en la tablet

Clases: `PantallasInfladoTest` (PI), `CicloVidaYEntradasTest` (CV), `FlujoCobroTest` (FC). Todos los resultados son los **reales** de la ejecución del 2026-10-16.

| ID | Módulo | Escenario | Precondiciones | Esperado | Real |
|---|---|---|---|---|---|
| PI-01…14 | Todas las pantallas | Splash, Login, Principal, Pedido, Ajustes, Stock, Historial, Estadísticas, Arqueo, Modificar precios, Lista productos, Ayuda, Simulador Fase 2, Pedidos web se inflan | sesión demo, tablet desbloqueada y apaisada | llega a RESUMED, vista clave visible, ninguna vista no desplazable fuera del ancho | PASS (14/14) |
| CV-01 | Pedido | Rotar vertical→horizontal tras cargar | Pedido abierto | la lista sigue visible | PASS |
| CV-02 | Pedido | Rotar dos veces **durante la carga** | Pedido recién lanzado | no se cae; rejilla visible | PASS |
| CV-03 | Principal, Pedido, Ajustes, Historial, Modificar precios | `recreate()` | — | vuelven a RESUMED | PASS |
| CV-04 | Pedido | CREATED→STARTED→RESUMED→DESTROYED (segundo plano y vuelta) | — | la lista se ve al volver | PASS |
| CV-05 | Ajustes (ticket) | 600 caracteres, emojis, comillas, `<b>`, `DROP TABLE` en nombre/dirección/info fiscal y Guardar | — | no se cierra | PASS |
| CV-06 | Ajustes (impresora) | IP/puerto vacíos, IP 999.999.1.1, puerto `abc`, `-1`, `99999`, emojis, 600 caracteres | — | no se cierra, no guarda valores inválidos | PASS tras corregir BUG-QA-01 (antes: FAIL) |
| CV-07 | Ajustes | Doble toque en Guardar | — | sin fallo | PASS |
| CV-08 | Ajustes (servidor) | URL vacía, sin esquema, `ftp://`, `javascript:`, emojis, 600 caracteres | — | rechaza o pide confirmación; no se cierra | PASS |
| CV-09 | Login | Vacíos, solo email, solo clave, 600 caracteres, emojis, espacios, `' OR 1=1 --` | sin sesión | sigue en Login, sin caída | PASS |
| CV-10 | Modificar precios | ID vacío, 0, -1, desbordado, texto, emojis, decimal | — | sin caída | PASS |
| CV-11 | Principal | Doble toque en una tarjeta | — | una sola navegación, app viva | PASS |
| FC-01 | Pedido | Imprimir con el pedido vacío | — | no guarda ninguna venta | PASS |
| FC-02 | Pedido | Añadir producto (selector → «Añadir al carrito») y pulsar Imprimir **dos veces** | catálogo sembrado | un solo diálogo «Método de pago» | PASS |
| FC-03 | Cobro E2E | Producto → carrito → Imprimir → Efectivo con impresora caída | `127.0.0.1:9` | venta guardada (+1) y diálogo «Ticket n.º N sin imprimir» con *Reintentar* | PASS |

Las pruebas FC borran las ventas que crean (`@After`); las capturas quedan en `getExternalFilesDir(PICTURES)/qa/`.

### 3.2 JVM (`app/src/test`)

| Grupo (paquete) | Clases | Casos | Contenido |
|---|---|---|---|
| `data/auth` | 5 | 61 | login/refresh/logout, `SessionManager`, interceptor, política de sesión; respuestas 200/400/401/403/500 y caducidad con `FakeApiServer` |
| `data/local` | 11 | 89 | migraciones Room 1→6, DAOs, sembrado del catálogo, `TenantContext`, stock al editar/borrar ventas |
| `data/network` | 2 | 31 | `OrderSyncService`: éxito, 4xx/5xx, tiempos agotados, URL inválida, claves |
| `data/printer` | 4 | 33 | ESC/POS, formato de ticket, impresora TCP falsa |
| `data/sync` | 11 | 76 | subida por lotes, rechazos, 401/403 como fallo de credenciales, ediciones concurrentes, cola de catálogo, banner/omisión/reinicio |
| `domain` + `pricing` + `usecase` + `adapter` + `port` | 19 | 118 | precios (UD/peso/horas/volumen), recargos, `PorcentajeExtra`, `StockAdvisor`, puente heredado, cálculo de totales, datos nulos/vacíos/corruptos |
| `presentation` + `viewmodel` | 5 | 39 | `PedidoViewModel`/`LoginViewModel` en todos los estados, `AppExecutors`, guarda de sesión, validación de puerto |
| `util`, `data/repo` y raíz | 13 | 56 | CSV, formatos, utilidades, repositorios |

## 4. Rendimiento y estabilidad (adb)

| Prueba | Comando | Resultado |
|---|---|---|
| Arranque en frío | `am start -W SplashActivity` | 672 ms y 707 ms (TotalTime) |
| WiFi apagado + Monkey | `svc wifi disable` + `monkey -s 101 --throttle 50 -v 1000` | 1 000 eventos, 0 caídas/ANR, «not connected» durante toda la ejecución |
| WiFi encendido + Monkey | `svc wifi enable` + `monkey -s 202 … 1000` | 1 000 eventos, 0 caídas/ANR |
| Monkey largo | `monkey -s 303 --throttle 50 -v 2000` | 2 000 eventos, 0 `FATAL`, 0 ANR |
| Permisos | `pm grant/revoke BLUETOOTH_CONNECT/SCAN` + arranque | sin fallos (la impresión es solo TCP; los permisos no se piden) |
| Memoria (`dumpsys meminfo`) | tras 2 000 eventos | PSS 108 MB → 139 MB → 135 MB; Activities 5 → 6 → 3; ViewRootImpl 5 → 18 → 12 |
| Fotogramas (`dumpsys gfxinfo`) | durante Monkey | 1 444 fotogramas, 9,49 % con *jank*; p50 6 ms, p90 18 ms, p95 29 ms, p99 69 ms (GPU p99 12 ms) |

Lectura: el uso de memoria sube bajo estrés extremo y baja al terminar, sin crecimiento sostenido de Activities; no hay evidencia de fuga, pero tampoco se ha hecho un análisis con LeakCanary. El *jank* del 9,5 % es con entrada aleatoria a 50 ms, no uso normal.

## 5. Bucle de depuración

`adb logcat -c` antes de cada ejecución y revisión de `-b crash` después: **0 `FATAL`** en toda la campaña. Los `E` de logcat restantes son del sistema (SurfaceFlinger, InputDispatcher, `libpenguin.so` de Samsung) y no pertenecen a la app.

## 6. Registro de defectos

| ID | Gravedad | Descripción | Estado |
|---|---|---|---|
| BUG-QA-01 | Alta | `AjustesActivity` hacía `Integer.parseInt` del puerto sin validar: un valor no numérico lanzaba `NumberFormatException` y se aceptaban puertos fuera de 1–65535 | **Corregido** (`puertoValido`, `PuertoValidoTest`; CV-06 pasa) |
| BUG-QA-02 | Leve | En vertical (1200 px) `MainActivity` deja `btnAyuda` fuera de pantalla | **Corregido** (barra inferior desplazable; prueba en tablet `principalEnVerticalNoSeSaleDeLaPantalla`) |
| OBS-QA-03 | Leve | La IP de la impresora no se validaba (se aceptaba `999.999.1.1`) | **Corregido** (`ValidadorImpresora`, IPv4 u hostname) |
| BUG-QA-05 | Media | `SyncManager.deserializarItems` tragaba el JSON corrupto y devolvía un pedido vacío | **Corregido** antes (NEG-06, `DeserializacionVentaException`; `SyncManagerDeserializacionTest` sin `@Ignore`, los llamantes la gestionan) |
| OBS-QA-06 | Leve | `ResumenSemanalCorreo.guardarVenta` (guardaba con `isSynced=true`) no tenía ninguna llamada | **Eliminado** (código muerto, sin tests asociados) |
| OBS-QA-07 | Leve | Los singletons estáticos de `Auth` (`store`, `manager`, `client`) conservaban las preferencias y la URL de la prueba anterior, y por eso se había quitado el test del login correcto en `LoginActivity` | **Corregido** (`Auth.reset()`; `LoginActivityTest.login_correcto_guarda_la_sesion_y_abre_el_menu_principal`) |
| OBS-QA-08 | Leve | `LoginViewModelTest.login_correcto_guarda_la_sesion_y_entra_al_negocio` fallaba de forma intermitente en la suite completa (2 de ~9 ejecuciones; nunca aislado): el alta del negocio tras el login se encola en el ejecutor de disco, un único hilo compartido por toda la JVM, detrás de lo que dejan otras pruebas | **Corregido por diseño** (`LoginViewModel` recibe el `Executor`; el test usa uno síncrono; 10 ejecuciones completas seguidas en verde, 865/865). Causa exacta no reproducida de forma directa, ver `docs/SINGLETONS_Y_AISLAMIENTO_TESTS.md` |
| OBS-QA-04 | Info | `ticket n.º` y capturas confirman que con impresora caída la venta se guarda antes (diseño intencionado) | — |

## 7. Alcance y limitaciones (leer antes de dar el informe por cerrado)

- «Todas las pruebas posibles» no es alcanzable; este informe cubre lo ejecutado. **No** se ha ejecutado: tests de la API Node contra PostgreSQL (sin BD/Docker locales), migración SQL, `restaurar.sh`, compose endurecido, JobScheduler a los 15 min en dispositivo, impresión real por ESC/POS, biometría, importación CSV desde el selector de archivos, flujos de edición de venta y cambio de negocio por la interfaz.
- Cobertura JaCoCo 85,8 % de líneas (JVM; margen de 487 líneas sobre el 80 %); sin cubrir del todo: `Fase2DemoActivity` (0 %), `PedidoActivity` (75 %, falta el flujo de pago que imprime), `PedidosWebActivity` (parcial), impresión/reimpresión en `PedidoViewModel`, biometría y la lectura truncada de la respuesta o el fallo al registrar el resultado en `VentaUploader` (cerrar la base compartida rompe los demás tests). La parte Keystore de la biometría no tiene test JVM y no se ha probado con una huella real.
- La prueba de WiFi apagado se hizo bajo Monkey (las Activities no están exportadas y no se puede navegar por `am start`); no se verificó el banner de sincronización de forma dirigida.
- Los tiempos de arranque y *jank* son de una sola tablet.

### Estado de lo no ejecutado (revisado el 22-oct-2026)

| Elemento | Estado | Motivo / evidencia más cercana |
|---|---|---|
| Tests de la API Node contra PostgreSQL | **Ejecutado en CI** (22-oct-2026) | En local no hay PostgreSQL ni permiso de Docker (pasan 33 y se omiten 82). El job `api` de `.github/workflows/ci.yml` los ejecuta contra PostgreSQL 16: 115 ejecutados, 115 correctos, 0 omitidos (ejecución 39789850560 de GitHub Actions) |
| Migración SQL y `restaurar.sh`, compose endurecido | **No ejecutado** | Necesitan Docker/PostgreSQL |
| `JobScheduler` a los 15 min en dispositivo | **No ejecutado** | Con sesión de demostración no hay subida en segundo plano; JVM: `SyncSchedulerTest`, `SyncJobCicloTest` |
| Impresión real ESC/POS | **No ejecutado en esta ronda** | Medición del 15-oct con impresora de red (11 tickets, `evidencias/mediciones_impresora_2026-10-15.md`); protocolo probado contra impresora TCP falsa (`PrinterHelperTest`) |
| Biometría con huella real | **No ejecutado** | Solo política (`BiometricUnlockPolicyTest`); la parte Keystore no tiene test JVM |
| Importación CSV desde el selector de archivos | **Ejecutado el 16-oct** | `otros.csv`: «IMPORTACIÓN COMPLETADA, 11 productos»; carta restaurada desde copia. JVM: `CsvParserTest`, `CatalogSyncTest` |
| Edición de venta por la interfaz | **Ejecutado el 16-oct** | Ticket 1 de 4,68 € a 7,08 € conservando el uuid; 2 ventas en la base. JVM: `EdicionVentaStockTest`, `RecuperarVentaPesoHorasTest`, `PedidoActivityTest` |
| Cambio de negocio por la interfaz | **Ejecutado el 16-oct** | Admin a La Nube: nueva base `softmyc_database.db`, historial a 0,00 €. JVM: `AdminTenantSelectorDialogTest`, `LoginViewModelTest` |
| Banner de sincronización con WiFi apagado (dirigido) | **Ejecutado el 16-oct** | Cobro sin red guardado con `isSynced = 0` y banner «2 ventas sin sincronizar»; subida al reconectar no comprobada (sesión de demostración). `docs/PRUEBAS_OFFLINE_SYNC.md` |
| Estabilidad de las 29 pruebas instrumentadas | **Ejecutado (N = 5)** | `evidencias/qa_repeticion_2026-10-16/RESUMEN.md`: 5/5 en verde |

Observación: `CicloVidaYEntradasTest.ajustes_urlDeServidorInvalida` tarda ≈130 s por pasada (la mitad de toda la campaña); no falla, pero merece revisión.


## 8. Guía de reproducción

```bash
# JVM
./gradlew cleanTestDebugUnitTest testDebugUnitTest --offline -q
# Instrumentadas (tablet desbloqueada, USB, depuración activada). Solo el paquete qa:
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.softmyc.tpv.qa \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
# Una clase:  -Pandroid.testInstrumentationRunnerArguments.class=com.softmyc.tpv.qa.FlujoCobroTest
# Resultados: app/build/outputs/androidTest-results/connected/*/TEST-*.xml

# Entorno
adb devices; adb shell getprop ro.product.model; adb shell wm size; adb shell wm density
adb shell settings put global stay_on_while_plugged_in 2    # pantalla encendida por USB (restaurar a 0)
adb logcat -c; adb logcat -d -b crash

# Resiliencia
adb shell svc wifi disable; adb shell svc wifi enable
adb shell pm revoke com.softmyc.tpv android.permission.BLUETOOTH_CONNECT
adb shell pm grant  com.softmyc.tpv android.permission.BLUETOOTH_CONNECT
adb shell monkey -p com.softmyc.tpv -s 303 --throttle 50 --ignore-timeouts -v 2000
adb shell dumpsys meminfo com.softmyc.tpv
adb shell dumpsys gfxinfo com.softmyc.tpv reset; adb shell dumpsys gfxinfo com.softmyc.tpv
adb shell am start -W -n com.softmyc.tpv/.presentation.SplashActivity
```

**Aviso:** no lances los `androidTest` de medición de impresora (otras clases fuera del paquete `qa`) sin una impresora real conectada. Las pruebas `qa` guardan y restauran sesión, negocio y preferencias de impresora (`QaSupport.preparar/restaurar`).
