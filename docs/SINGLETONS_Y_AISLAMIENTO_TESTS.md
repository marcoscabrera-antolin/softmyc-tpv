# Estado compartido y aislamiento entre tests (TASK 1.3)

Inventario del 16-oct-2026 con `grep` de campos `static` mutables y ejecutores en `app/src/main/java`. Cada fila indica qué se comparte y cómo se evita que una prueba herede el estado de la anterior.

| Singleton / estado | Clase | Qué comparte | Aislamiento en tests |
|---|---|---|---|
| Gestor, almacén y cliente de sesión | `data/auth/Auth` | `SessionManager`, `SessionStore`, `OkHttpClient` (con URL y prefs de la primera aplicación que los pidió) | **Corregido en esta task:** los singletons guardan la aplicación con la que se crearon y se descartan solos si cambia (`descartarSiCambiaLaAplicacion`). `Auth.reset()` sigue disponible. Test: `AuthAislamientoTest`. Antes, 14 clases de test usaban `Auth` sin llamar a `reset()` y dependían de que ninguna otra hubiera creado antes el singleton |
| Caché de prefs cifradas | `data/SecurePreferencesHelper` (`CACHE`, `MEMORY_FALLBACK`, `DEGRADED_FILES`) | Instancia por fichero | La caché guarda la aplicación y no la reutiliza si es otra; `setFactoryForTests(null)` la vacía (`SecurePreferencesHelperTest`) |
| Bases por negocio | `data/local/AppDatabase.TENANT_INSTANCES` | Una instancia Room por negocio | `TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros` (lo usan 20 clases de test). Revisar si alguna de las 59 que tocan Room/Robolectric no lo usa (ver TASK 2.2) |
| Ejecutor de disco | `presentation/AppExecutors.DISCO` | Un único hilo para toda la JVM | `LoginViewModel` recibe el ejecutor por constructor (tests: `Runnable::run`). Las Activities lo siguen usando directamente: sus tests Robolectric no esperan a que se vacíe, por lo que una tarea lenta de un test puede retrasar la del siguiente (riesgo residual, sin fallos observados en 10 pasadas) |
| Ejecutor de impresión | `data/printer/PrinterHelper.printerExecutor` | Un hilo para toda la JVM | Los tests usan `FakeImpresoraTcp` y esperan el resultado; sin incidencias |
| Contador de caducidades | `Auth.EXPIRATIONS` (LiveData) | Número de caducidades de sesión | `Auth.reset()` lo pone a 0 |

## Causa del flaky OBS-QA-08

`LoginViewModelTest.login_correcto_guarda_la_sesion_y_entra_al_negocio` encolaba el alta del negocio en `AppExecutors.disco()`, hilo único de toda la JVM compartido con las demás pruebas. La hipótesis es que tareas de otras clases lo ocupaban y la espera de 15 s se agotaba bajo carga. **No se reprodujo el fallo de forma directa** (era del 22 % en la suite completa y nunca aislado), así que la causa sigue siendo una hipótesis. La corrección elimina la dependencia: el ViewModel recibe el `Executor` y el test usa uno síncrono, sin hilo compartido.

Evidencia: 10 ejecuciones consecutivas de `./gradlew cleanTestDebugUnitTest testDebugUnitTest --offline -q`, todas con salida 0 y 865 tests sin fallos (antes de añadir los tests de la TASK 1.4; después, 898) (≈29 s cada una).
