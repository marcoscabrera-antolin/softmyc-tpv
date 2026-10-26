// Cobertura de los tests JVM (JUnit + Robolectric) con JaCoCo.
//
// Cómo ejecutarlo (desde la raíz del repo):
//   ./gradlew jacocoDebugUnitTestReport
// Genera:
//   app/build/reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml  (para CI / herramientas)
//   app/build/reports/jacoco/jacocoDebugUnitTestReport/html/index.html               (para leer en el navegador)
// La tarea ejecuta antes testDebugUnitTest. El porcentaje global aparece en el HTML (columna "Total" del índice)
// y en el XML (contadores INSTRUCTION/LINE del nodo raíz <report>).
//
// Última medición (2026-10-16, main, 898 tests): líneas 85,8 % (7 327 de 8 540). Se mide sobre los tests JVM;
// el cifrado y el Keystore reales no se ejercitan.
//
// Puerta de calidad: ./gradlew jacocoDebugUnitTestCoverageVerification falla si la cobertura de líneas baja de
// 0,80 (la CI la ejecuta). El umbral se puede cambiar con -PcoberturaMinima=0.85 (útil para probar que falla).
//
// Notas:
// - Robolectric carga las clases con su propio classloader: sin isIncludeNoLocationClasses, JaCoCo no registra las
//   clases sin tabla de líneas y la cobertura sale a medias. jdk.internal.* se excluye porque el JDK las instrumenta
//   al vuelo y ensucian el informe.
// - Se excluyen R, BuildConfig y las clases generadas de Room/Android: no son lógica propia.

apply(plugin = "jacoco")

extensions.configure<JacocoPluginExtension> {
    toolVersion = "0.8.12"
}

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val excluidasDeCobertura = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/DebugApplication.class",   // herramienta de la variante debug (StrictMode), no es lógica propia
    "android/**/*.*",
    "androidx/**/*.*"
)

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    group = "verification"
    description = "Informe JaCoCo (XML + HTML) de los tests unitarios de la variante debug."
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val compilacionJava = tasks.named<JavaCompile>("compileDebugJavaWithJavac")
    classDirectories.setFrom(
        fileTree(compilacionJava.get().destinationDirectory) { exclude(excluidasDeCobertura) }
    )
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("jacoco/testDebugUnitTest.exec") }
    )
}

tasks.register<JacocoCoverageVerification>("jacocoDebugUnitTestCoverageVerification") {
    group = "verification"
    description = "Falla si la cobertura de líneas de los tests JVM baja del umbral (por defecto 0,80)."
    dependsOn("testDebugUnitTest")

    val minima = (findProperty("coberturaMinima") as String?)?.toBigDecimal() ?: "0.80".toBigDecimal()
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                this.minimum = minima
            }
        }
    }

    val compilacionJava = tasks.named<JavaCompile>("compileDebugJavaWithJavac")
    classDirectories.setFrom(
        fileTree(compilacionJava.get().destinationDirectory) { exclude(excluidasDeCobertura) }
    )
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("jacoco/testDebugUnitTest.exec") }
    )
}
