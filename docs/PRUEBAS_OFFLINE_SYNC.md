# Pruebas del modo offline y de la sincronización de ventas (TASK 2.1)

Servidor HTTP simulado (`FakeApiServer`, `ServerSocket` real) con OkHttp real, Room real (Robolectric) y la sesión real (`Auth`). Cada escenario indica el test que lo comprueba. Ejecutar: `./gradlew testDebugUnitTest --offline --tests "*VentaUploaderTest" --tests "*SyncJobCiclo*" --tests "*SyncRetryPolicyTest" --tests "*AuthHttpTest"`.

| Escenario | Resultado esperado | Test |
|---|---|---|
| Venta pendiente, servidor correcto | Se sube y queda `isSynced = true` | `VentaUploaderTest.ventaPendiente_seSubeYSeMarcaComoSincronizada` |
| Contenido enviado | Negocio, y por venta uuid, método y líneas | `VentaUploaderTest.elCuerpoLlevaElNegocioYPorCadaVentaUuidMetodoYLineas` |
| Más de 50 ventas | Varios lotes | `VentaUploaderTest.masDeCincuentaVentas_seSubenEnVariosLotes` |
| Sin red | Queda pendiente; se puede reintentar después | `VentaUploaderTest.sinRed_dejaPendienteYPermiteReintentarDespues` |
| 5xx en un lote | No impide subir los siguientes | `VentaUploaderTest.errorDelServidorEnUnLote_noImpideSubirLosSiguientes` |
| **Servidor caído (503) y recuperado** | Un intento fallido, uno correcto y un tercero que no envía nada; no se duplica | `VentaUploaderTest.servidorCaidoYRecuperado_laVentaSeSubeEnElSiguienteIntentoSinDuplicarse` (nuevo) |
| 400 / respuesta sin JSON | Ventas pendientes | `respuesta400_dejaLasVentasPendientes`, `respuestaSinJson_dejaLasVentasPendientes` |
| 401 sin sesión válida | Pendientes y reintentable | `respuesta401_dejaLasVentasPendientesYLaSubidaSiguePudiendoReintentarse` |
| **401 con refresco válido** | Renueva una vez, reintenta con el token nuevo y sube (2 POST de ventas, 1 refresh) | `VentaUploaderTest.accesoCaducadoEnElServidor_renuevaLaSesionYSubeLaVentaUnaSolaVez` (nuevo) |
| Confirmación parcial | Solo se marcan las confirmadas | `confirmacionParcial_soloMarcaLasVentasConfirmadas` |
| Rechazo de una venta por el servidor | Pendiente y se avisa | `ventaRechazadaPorElServidor_quedaPendienteYSeAvisa` |
| Venta editada mientras se enviaba | Sigue pendiente (no se pierde la edición) | `ventaEditadaMientrasSeEnviaba_siguePendiente`, `ventaEditadaEnLasLineasMientrasSeEnviaba_siguePendiente` |
| Subida solapada | Se repite al terminar la que estaba en marcha | `subidaSolapada_seRepiteAlTerminarLaQueEstabaEnMarcha` |
| **Tras una subida correcta** | Otra subida no vuelve a enviar la venta | `VentaUploaderTest.trasSincronizarConExito_otraSubidaNoVuelveAEnviarLaVenta` (nuevo) |
| Sesión de otro negocio | No sube nada (`SyncGuard`) | `sesionDeOtroNegocio_noSubeNada`, `SyncGuardTest` |
| Venta sin uuid | Se asigna y se guarda antes de enviar | `ventaSinUuid_seLeAsignaYSePersisteAntesDeEnviarla` |
| Refresco concurrente | Una sola renovación para muchas peticiones 401 | `AuthHttpTest.manyConcurrentUnauthorizedRequests_shareASingleRefresh` |
| Refresco sin red | Se conserva la sesión | `AuthHttpTest.refreshFailingForLackOfNetwork_keepsTheSession` |
| Ciclo en segundo plano | Reprograma ante fallo reintentable; no reprograma sin motivo | `SyncJobCicloTest`, `SyncJobCicloSubidaTest`, `SyncRetryPolicyTest` |

## Idempotencia

- **Cliente:** una venta confirmada no se reenvía (`trasSincronizarConExito_...`).
- **Servidor:** la subida es idempotente por `(tenant_id, id_local, fecha)` y por `uuid` (`tfg-db/initdb/02-schema.sql`, `03-api.sql`); la edición reenvía el mismo uuid y queda registrada en `ventas_historial`. Esto solo se puede comprobar con PostgreSQL: los tests de la API (`auth-db.test.js`, `api.test.js`) se omiten en este equipo (sin Docker ni PostgreSQL) y los ejecuta la CI.

## No cubierto

- **Tiempo de espera agotado (timeout):** el cliente usa 10 s de conexión y 20 s de lectura (`Auth.PLAIN`); no hay test porque obligaría a esperar esos tiempos.
- **Respuesta truncada a mitad:** la lectura truncada de `VentaUploader` sigue sin cubrir (ver `INFORME_TESTING_QA.md` §7).
- **Subida real al reconectar en la tablet:** no comprobada. La prueba con WiFi apagado se hizo el 16-oct (tabla siguiente), pero con una sesión de demostración, que no sincroniza; falta repetirla con credenciales reales y comprobar `isSynced = 1`.

## Prueba en tablet con WiFi apagado (16-oct-2026)

| Paso | Resultado |
|---|---|
| `svc wifi disable` | `Active default network: none` |
| Cobro en efectivo de 1 kg de plátanos (2,95 €) | «Venta guardada»; la impresión falla por `ENETUNREACH` a los 3 000 ms y aparece el diálogo «Ticket n.º 2 sin imprimir» con «Reintentar impresión» |
| Base SQLite (`consultar_bd_tablet.sh`) | `integrity_check = ok`, 2 ventas, ambas `isSynced = 0` |
| Pantalla principal | Banner «2 ventas sin sincronizar · sesión caducada o sin credenciales: inicia sesión de nuevo» |
| `svc wifi enable` y 12 s de espera | Red activa; siguen `isSynced = 0` y el mismo banner (sesión de demostración) |

Datos en `evidencias/mediciones_tablet_2026-10-16.md`.
