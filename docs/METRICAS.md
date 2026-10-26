# Métricas del proyecto (generado)

Generado por `evidencias/generar_metricas.sh` el 2026-10-22 sobre el commit `51c6a0e`. No editar a mano: vuelve a ejecutar el script.

| Métrica | Valor | Cómo se calcula |
|---|---|---|
| Tests JVM (`@Test`) | 1003 en 125 clases | recuento de `@Test` en `app/src/test` |
| Última ejecución JVM | 1003 ejecutados, 0 fallos, 0 omitidos | XML de `app/build/test-results/testDebugUnitTest` |
| Cobertura JVM (JaCoCo) | líneas 90.5 % (8 073 de 8 925); instrucciones 90.2 % (40 976 de 45 447); ramas 74.0 % (2 798 de 3 782) | `jacocoDebugUnitTestReport` |
| Pruebas instrumentadas totales (`@Test`) | 36 en 5 clases | recuento en `app/src/androidTest` |
| Pruebas instrumentadas del paquete `qa` | 29 | las que ejecuta `connectedDebugAndroidTest` con el comando de CLAUDE.md |
| Tests de la API Node (`test(`/`it(`) | 115 | recuento en `tfg-db/api/test` (muchos se omiten sin PostgreSQL) |
| Líneas de código Java (producción) | 18574 | `app/src/main` |
| Líneas de código Java (tests JVM) | 18867 | `app/src/test` |
| APK release sin firmar | no generado | `assembleRelease` |
| AAB release | 7 590 955 B (7.24 MiB) | `bundleRelease` |

Los tiempos de arranque, jank y memoria no se calculan aquí: requieren la tablet (ver `docs/INFORME_RENDIMIENTO.md`).
