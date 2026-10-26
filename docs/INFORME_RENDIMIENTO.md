# Informe de rendimiento (TASK 2.3)

Consolida las mediciones ya hechas en la tablet (carpeta `evidencias/`, enlace a Obsidian) y las que se pueden repetir sin ella. **Una sola tablet** (Samsung SM-X210R, Android 16) y las repeticiones indicadas en cada fila; no hay intervalos de confianza. Lo que falta por medir está en «Pendiente».

## 1. Tamaño del paquete de producción (medido el 16-oct-2026)

`./gradlew assembleRelease bundleRelease --offline` (R8 con `isMinifyEnabled = true` y `shrinkResources`):

| Artefacto | Tamaño |
|---|---|
| `app-release-unsigned.apk` | 2 244 480 B (2,14 MiB) |
| `app-release.aab` | 3 531 788 B (3,37 MiB) |

Coincide con lo que dice la memoria (2,1 y 3,3 MiB).

## 2. Cobro y base de datos (15-oct-2026, compilación debug)

Fuente: `evidencias/mc02_mc05_resultados.md` (`MedicionesRendimientoTest`, 5 calentamientos descartados y 30 muestras).

| Medida | n | Mediana | p95 | Máximo |
|---|---|---|---|---|
| Guardado de un cobro (3 líneas, stock controlado) | 30 | 2,10 ms | 4,55 ms | 11,15 ms |
| Historial, `SELECT … LIMIT 50`, 5 000 ventas | 30 | 0,70 ms | 0,78 ms | 0,80 ms |
| Historial con filtro en SQL (`getDesde`), periodo de 100 ventas, 20 000 ventas | 30 | 1,80 ms | 1,89 ms | — |
| Historial antiguo (`getAll()` y filtro en memoria), 20 000 ventas | 30 | 251,92 ms | 254,53 ms | — |

Cobro completo con impresora real (`evidencias/mediciones_impresora_2026-10-15.md`, 10 cobros tras 1 de calentamiento): ver ese fichero. «Impreso» significa bytes entregados al socket, no papel salido; la batería estaba al 1–2 % durante las mediciones (limitación declarada allí).

## 3. Apertura del diálogo de personalización (RNF-03, debug)

Fuente: `evidencias/rnf03_variantes_resumen.md` (1 apertura en frío y 30 en caliente por variante, orden alternado, tablet sin carga a 32,9 °C).

| Variante | Compilación | Mediana en caliente | p90 | Máximo | ¿< 150 ms? |
|---|---|---|---|---|---|
| Base (`Chip` de Material) | Debug | 195,67 ms | 233,13 ms | 303,57 ms | No |
| **C: `LightChipView` (la que está en `main`)** | Debug | **100,18 ms** | 109,15 ms | 123,02 ms | Sí |
| D: extras con «Ver más» (cambia el comportamiento) | Debug | 135,79 ms | 155,77 ms | 170,00 ms | Parcial |
| E: vista precalentada (no está en `main`) | Debug | 83,35 ms | 95,54 ms | 117,05 ms | Sí |
| Base | Release | 101,76 ms | 113,33 ms | 156,66 ms | Parcial |
| E | Release | 61,57 ms | 69,95 ms | 85,88 ms | Sí |

- **Qué está en `main`:** la variante C (commit `3942013`, `LightChipView`). La variante E (`prewarm`) no está en el código actual (`grep prewarm` sin resultados). Cualquier cifra de «después de optimizar» en la memoria debe ser la de C (100,18 ms en caliente, debug) salvo que se integre E.
- El commit `bb9ae92` (renderizado diferido, tag `archivo/rnf03-optimizacion`) dio 132,86 ms de mediana y quedó superado por C; por eso no se integró (TASK 1.1).
- **El arranque en frío sigue por encima de 150 ms** en debug (166,68 ms con C); en release con la base, 152,95 ms.
- Arranque de la aplicación (`am start -W`, 5 ejecuciones, 15-oct): 824, 803, 657, 669 y 669 ms (mediana 669 ms; incluye el arranque del proceso).

## 4. Estabilidad de las pruebas instrumentadas (TASK 1.5)

29 pruebas en tablet × 5 pasadas: 252–256 s por pasada, suma de tiempos de prueba 241,8 s de media (desviación típica 1,5 s), 0 `FATAL`. Detalle en `evidencias/qa_repeticion_2026-10-16/RESUMEN.md`.

## 5. Mediciones nuevas en tablet (16-oct-2026, debug con StrictMode)

Datos completos en `evidencias/mediciones_tablet_2026-10-16.md`.

| Medida | n | Resultado |
|---|---|---|
| Arranque en frío (`am start -W`) | 12 | mediana 654 ms, media 655,4, desviación típica 9,0, rango 639–669 |
| Arranque en caliente | 12 | mediana 95,5 ms (92 ms sin la primera muestra de 849 ms, casi en frío) |
| Jank (`gfxinfo`, flujo con 10 aperturas del diálogo) | 3 corridas | 22,8 %, 25,2 % y 22,8 % de fotogramas lentos; p50 9–12 ms, p90 38 ms, p99 73–77 ms |
| Memoria con Monkey (9 000 eventos) | 4 puntos | PSS 105,9 → 175,5 → 201,1 → 198,0 MB; se estabiliza |
| Memoria, prueba controlada (75 aperturas y cierres) | 4 puntos | PSS 78,3 → 99,1 → 99,5 → 100,2 MB; Activities y Views constantes (7 y 313) |

Lectura: el jank del 23 % es de una compilación debug y se concentra en la apertura del diálogo; no se midió release. No se observa fuga de Activities en la prueba controlada (el crecimiento de Activities con Monkey es la pila de la tarea, sin pulsar «atrás»). No se hizo una sesión de 30 minutos.

## 6. Pendiente (sigue sin medir)

1. **Jank en compilación release** (hoy solo debug).
2. **Memoria tras una sesión real de ≥ 30 minutos.**
3. **Repetir el cobro con impresora y batería cargada** (las mediciones de impresora se hicieron con 1–2 % de batería; la TASK 2.4 sigue aparcada).
