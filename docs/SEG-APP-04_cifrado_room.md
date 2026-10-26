# SEG-APP-04: cifrado de la base Room

Estado: **implementado** en la rama `mejora/paso7-sqlcipher` (sin push). Hallazgo de la auditoría integral de 2026-10-16 (gravedad GRAVE). Pendiente de verificación en tablet (ver §6).

Este documento empezó como análisis de opciones (A-D) y ahora recoge la decisión, el diseño implementado, los riesgos y la prueba manual pendiente.

## 1. Situación previa

- `AppDatabase.getDatabaseForTenant()` creaba cada base con `Room.databaseBuilder(...)` sin `openHelperFactory`: los ficheros `softmyc_<tenant>.db` (y `softmyc_database.db` para `lanube`) estaban **en claro** en el almacenamiento privado.
- Contenido: ventas (`VentaLocal`, incluido `isSynced`), catálogo, extras, salsas y stock.
- Mitigaciones previas: `allowBackup="false"`, `data_extraction_rules.xml` que excluye `database`/`file`, sandbox de Android.
- No mitigaban: root, build depurable con `adb run-as` ni extracción física del almacenamiento.

## 2. Decisión

Opción **A: SQLCipher para Android** (`net.zetetic:sqlcipher-android:4.17.0`) con `androidx.sqlite:sqlite:2.6.2`, aplicado a cada base por negocio mediante `openHelperFactory` de Room. Se descartaron B (cifrado de campos: rompe consultas, orden y agregados) y D (SEE: licencia). La opción C (riesgo aceptado) queda sustituida por esta implementación.

Versión de SQLCipher: la más reciente (4.19.1) exige `compileSdk` 37 y el proyecto compila contra 35; se usa **4.17.0**, la más reciente compatible con `compileSdk` 35 (`minCompileSdk=1`). Subir a 4.18+ requiere subir `compileSdk`.

## 3. Diseño

Paquete `com.softmyc.tpv.data.local.cifrado`:

| Clase | Responsabilidad |
|---|---|
| `GestorBaseCifrada` | Punto de entrada. Decide según estado del fichero y clave, migra si hace falta y devuelve `SupportOpenHelperFactory`. |
| `ClaveBase` | Genera 256 bits con `SecureRandom` una sola vez por instalación, los guarda en hexadecimal y los usa como clave *raw* de SQLCipher (`x'...'`, sin derivación PBKDF2 en cada apertura). Nunca se loguea ni aparece en mensajes de error. |
| `AlmacenClaveSegura` | Persiste la clave en `EncryptedSharedPreferences` (`SecurePreferencesHelper.getClaveBasePrefs`), cuya clave maestra vive en el Android Keystore. |
| `EstadoBase` | Clasifica el fichero por cabecera: `AUSENTE` (o vacío), `EN_CLARO` (`SQLite format 3\0`), `CIFRADO` (cualquier otro contenido). |
| `PlanApertura` | Decisión pura: ausente → crear cifrada; en claro → migrar; cifrado con clave → abrir; cifrado sin clave → **error** (nunca se regenera la clave). |
| `MigradorBaseCifrada` | Migración verificada (ver §4). |
| `OperacionesBase` / `OperacionesSqlCipher` | Única parte que usa la API nativa: lectura de instantáneas (`user_version`, recuentos por tabla, `integrity_check`) y `sqlcipher_export`. |
| `ProveedorCifrado` | Punto de inyección. `AppDatabase` lo usa; en tests JVM se sustituye por `null` (bases en claro). |

Cambios en código existente:

- `AppDatabase`: `construirBase()` aplica `openHelperFactory` cuando hay proveedor. `setProveedorCifradoParaTests(null)` es el único hook de tests. Room sigue con `version = 6`, migraciones 1→6 y `exportSchema = true` sin cambios.
- `SecurePreferencesHelper`: nuevo almacén `ClaveBaseTPVEncrypted` y `estaDegradado(nombre)`.
- `app/build.gradle.kts`: dependencias SQLCipher y androidx.sqlite.
- `app/proguard-rules.pro`: regla 6 para `net.zetetic.database.**` (JNI y nativos). El AAR ya trae sus reglas de consumidor; se dejan explícitas.
- Tests: las 68 clases Robolectric reciben un bloque `static` que llama a `setProveedorCifradoParaTests(null)`.

## 4. Migración de bases en claro

Orden (cada paso comprobable; el original no se toca hasta el paso 5):

1. Borrar temporal `.cifrando` de intentos anteriores.
2. `leerEnClaro`: checkpoint del WAL, `user_version`, recuentos y `integrity_check`. Si no pasa la integridad, no se migra.
3. `exportarACifrada` a `<base>.cifrando` con `ATTACH ... KEY ?`, `sqlcipher_export` y `PRAGMA user_version`.
4. `leerCifrada` del temporal: debe ser equivalente (misma versión y mismos recuentos) o se borra el temporal y se conserva el original.
5. Copia de seguridad `<base>.claro.bak` (copia, con `fsync` y comprobación de tamaño).
6. `ATOMIC_MOVE` del temporal sobre la base original (`fsync` previo).
7. Verificación de la base cifrada. Si falla, se restaura el `.bak` en la ruta original y se lanza error.
8. Solo entonces se borra el `.bak`.

Estados interrumpidos: un `.cifrando` huérfano se descarta; un `.bak` con base ya cifrada se verifica (`integrity_check`) y se borra; base cifrada sin clave → error sin crear clave nueva.

## 5. Riesgos

- **Pérdida de clave = pérdida de datos.** La clave vive solo en el almacén del dispositivo (`allowBackup=false`, sin copia en la nube). Reinstalar la app, limpiar datos o resetear el Keystore deja la base cifrada inaccesible. Esta es la contrapartida principal y debe figurar en la memoria y en el procedimiento de recuperación.
- **Almacén degradado.** Si el Keystore falla, `SecurePreferencesHelper` usa memoria: la app **no crea ni migra** bases (lanza `BaseCifradaException`), para no dejar datos cifrados con una clave que se pierde al cerrar. Efecto: el negocio no puede operar en ese dispositivo hasta que el Keystore funcione. Es un fallo visible, no silencioso, pero bloquea ventas.
- **Migración en el hilo que llama a `getDatabaseForTenant`.** Es síncrona y proporcional al tamaño de la base. Con bases pequeñas es aceptable; no se ha medido en tablet.
- **Dependencia nativa.** `libsqlcipher.so` añade unos 2 MB por arquitectura al APK (la release de prueba pesa lo que indica el build). `System.loadLibrary("sqlcipher")` se llama al construir `OperacionesSqlCipher`; si la lib no carga, la migración falla con `UnsatisfiedLinkError` y no se toca el original.
- **Rendimiento.** Pequeño coste por consulta por el cifrado de páginas; no medido.
- **Una sola clave por instalación.** Todas las bases de los negocios comparten clave. Una fuga de la clave expone todos los negocios del dispositivo. Aceptado por simplicidad; la alternativa (clave por tenant) no mejora el modelo de amenaza, que ya asume dispositivo no comprometido.
- **Tests JVM sin cifrado real.** Robolectric no carga SQLCipher; la apertura cifrada y la migración real se prueban solo con fakes (`OperacionesBaseFalsas`) y en tablet (§6).
- **Recuperación de copias.** Un volcado de la base cifrada solo se puede leer con la clave del dispositivo.

## 6. Pendiente: prueba manual en tablet (no ejecutada)

No se han usado tablet ni tests instrumentados. Antes de considerar el cifrado en producción:

1. Instalar la versión anterior (sin cifrar), crear ventas y productos reales, sincronizar parte y dejar otras sin sincronizar.
2. Instalar esta versión por encima (update, no desinstalación, para conservar datos). Abrir la app y comprobar: aparece `softmyc_<tenant>.db` cifrada (`file` no reconoce cabecera SQLite con `adb shell run-as`), no quedan `.cifrando` ni `.claro.bak`, y el conteo de ventas y productos coincide con el de antes.
3. Verificar que la sincronización sigue subiendo las ventas no sincronizadas y que los IDs no se duplican.
4. Reiniciar la app varias veces: la base abre sin migrar de nuevo y sin tiempos de arranque inaceptables (medir el arranque con y sin migración).
5. Forzar un fallo: `adb shell pm clear` tras migrar (la clave se pierde) y comprobar que la app muestra error claro y no sobrescribe la base cifrada.
6. Comprobar que la base cifrada no es legible con `sqlite3` sin clave.
7. Medir el tamaño del APK release con `libsqlcipher.so` y el coste de consultas en `MedicionesRendimientoTest`.
8. Ejecutar `connectedDebugAndroidTest` con la base cifrada activa (los tests instrumentados pueden necesitar el proveedor real).

## 7. Texto para la memoria (sustituye al anterior)

> **Cifrado de la base de datos local.** Las bases SQLite de Room (una por negocio) se cifran con SQLCipher para Android (AES-256 por página). La clave de 256 bits se genera una vez por instalación y se guarda en `EncryptedSharedPreferences`, cuya clave maestra reside en el Android Keystore. Las bases existentes en claro se migran de forma verificada: se exportan a un fichero nuevo, se comprueban recuentos por tabla y se sustituyen de forma atómica conservando una copia hasta confirmar la base cifrada. Si se pierde la clave (reinstalación, borrado de datos o reset del Keystore) la base no se puede abrir; por eso no se incluye en copias de seguridad. Un dispositivo con acceso root o con la clave extraída queda fuera del modelo de amenaza.
