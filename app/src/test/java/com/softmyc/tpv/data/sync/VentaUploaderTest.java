package com.softmyc.tpv.data.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import com.softmyc.tpv.data.SecurePreferencesHelper;
import com.softmyc.tpv.data.local.TenantDatabasesTestSupport;
import com.softmyc.tpv.data.auth.Auth;
import com.softmyc.tpv.data.auth.AuthSession;
import com.softmyc.tpv.data.auth.FakeApiServer;
import com.softmyc.tpv.data.local.AppDatabase;
import com.softmyc.tpv.data.local.VentaLocal;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** TEST-02: la subida de ventas contra un servidor HTTP real simulado (OkHttp de verdad). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class VentaUploaderTest {

    static {
        // Bases en claro en tests JVM: SQLCipher no carga sus libs nativas en Robolectric.
        com.softmyc.tpv.data.local.AppDatabase.setProveedorCifradoParaTests(null);
    }

    private static final String RUTA = "/api/tpv/orders/sync";

    private final Context context = ApplicationProvider.getApplicationContext();
    private FakeApiServer server;
    private AppDatabase db;
    private final java.util.concurrent.ExecutorService hiloDb = java.util.concurrent.Executors.newSingleThreadExecutor();
    /** La base de producción no admite consultas en el hilo principal (el del test): se delegan a un hilo propio. */
    private com.softmyc.tpv.data.local.VentaDao dao;
    private final AtomicInteger rechazadasAvisadas = new AtomicInteger();

    @Before
    public void setUp() throws Exception {
        server = new FakeApiServer();
        SharedPreferences prefs = SecurePreferencesHelper.getPrinterPrefs(context);
        prefs.edit().putString("current_tenant", "fruteria")
                .putString("backend_base_url", server.baseUrl())
                .putString("backend_api_key", "clave_de_prueba_con_mas_de_veinte_caracteres").commit();
        Auth.store(context).clear();
        db = AppDatabase.getDatabase(context);
        com.softmyc.tpv.data.local.VentaDao real = db.ventaDao();
        dao = (com.softmyc.tpv.data.local.VentaDao) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{com.softmyc.tpv.data.local.VentaDao.class},
                (proxy, metodo, args) -> {
                    try {
                        return hiloDb.submit(() -> metodo.invoke(real, args)).get();
                    } catch (java.util.concurrent.ExecutionException e) {
                        Throwable causa = e.getCause();
                        throw causa instanceof java.lang.reflect.InvocationTargetException ? causa.getCause() : causa;
                    }
                });
        dao.deleteAll();
    }

    @After
    public void tearDown() {
        dao.deleteAll();
        Auth.store(context).clear();
        server.close();
        hiloDb.shutdown();
        TenantDatabasesTestSupport.cerrarInstanciasYBorrarFicheros(context);
    }

    private int insertarVenta(double total) {
        VentaLocal v = new VentaLocal(System.currentTimeMillis() - 1000, total,
                "[{\"nombre\":\"A\",\"cantidad\":1,\"precio_unitario\":" + total + ",\"importe_linea\":" + total + "}]", false, "Efectivo");
        return (int) dao.insert(v);
    }

    private void subirYEsperar() throws Exception {
        CountDownLatch fin = new CountDownLatch(1);
        new VentaUploader(context, hiloDb, rechazadasAvisadas::addAndGet).subirPendientes(fin::countDown);
        // alTerminar se dispara después de registrar el resultado del último lote: no hace falta esperar más.
        assertTrue("la subida no terminó a tiempo", fin.await(15, TimeUnit.SECONDS));
    }

    /** Respuesta de éxito que confirma todos los localId recibidos y rechaza los indicados. */
    private FakeApiServer.Reply confirmar(FakeApiServer.Request req, int... rechazar) {
        try {
            JSONArray ordenes = new JSONObject(req.body).getJSONArray("orders");
            JSONArray ok = new JSONArray();
            JSONArray rej = new JSONArray();
            for (int i = 0; i < ordenes.length(); i++) {
                int id = ordenes.getJSONObject(i).getInt("localId");
                boolean rechazada = false;
                for (int r : rechazar) if (r == id) rechazada = true;
                if (rechazada) rej.put(new JSONObject().put("localId", id).put("index", i).put("reason", "total no coincide"));
                else ok.put(id);
            }
            return new FakeApiServer.Reply(200, new JSONObject().put("success", true).put("syncedIds", ok).put("rejected", rej).toString());
        } catch (Exception e) {
            return new FakeApiServer.Reply(500, "{}");
        }
    }

    @Test
    public void ventaPendiente_seSubeYSeMarcaComoSincronizada() throws Exception {
        int id = insertarVenta(2.4);
        server.on(RUTA, req -> confirmar(req));

        subirYEsperar();

        assertEquals(1, server.countOf(RUTA));
        assertTrue(dao.getUnsynced().isEmpty());
        assertTrue(dao.getById(id).isSynced);
    }

    @Test
    public void elCuerpoLlevaElNegocioYPorCadaVentaUuidMetodoYLineas() throws Exception {
        insertarVenta(2.4);
        server.on(RUTA, req -> confirmar(req));

        subirYEsperar();

        JSONObject cuerpo = new JSONObject(server.requests().get(0).body);
        JSONObject venta = cuerpo.getJSONArray("orders").getJSONObject(0);
        assertEquals("fruteria", cuerpo.getString("restaurantId"));
        assertTrue(venta.getString("uuid").length() >= 32);
        assertEquals("Efectivo", venta.getString("metodoPago"));
        assertEquals(1, venta.getJSONArray("items").length());
    }

    @Test
    public void ventaRechazadaPorElServidor_quedaPendienteYSeAvisa() throws Exception {
        int buena = insertarVenta(1.0);
        int mala = insertarVenta(2.0);
        server.on(RUTA, req -> confirmar(req, mala));

        subirYEsperar();

        List<Integer> pendientes = new ArrayList<>();
        for (VentaLocal v : dao.getUnsynced()) pendientes.add(v.id);
        assertEquals(1, pendientes.size());
        assertEquals(mala, (int) pendientes.get(0));
        assertTrue(dao.getById(buena).isSynced);
        assertEquals(1, rechazadasAvisadas.get());
    }

    @Test
    public void masDeCincuentaVentas_seSubenEnVariosLotes() throws Exception {
        for (int i = 0; i < 120; i++) insertarVenta(1.0 + i);
        server.on(RUTA, req -> confirmar(req));

        subirYEsperar();

        assertEquals(3, server.countOf(RUTA));
        assertTrue(dao.getUnsynced().isEmpty());
        for (FakeApiServer.Request r : server.requests()) {
            assertTrue(new JSONObject(r.body).getJSONArray("orders").length() <= VentaSyncLote.TAMANO_MAXIMO);
        }
    }

    @Test
    public void errorDelServidorEnUnLote_noImpideSubirLosSiguientes() throws Exception {
        for (int i = 0; i < 60; i++) insertarVenta(1.0 + i);
        AtomicInteger llamadas = new AtomicInteger();
        server.on(RUTA, req -> llamadas.getAndIncrement() == 0 ? new FakeApiServer.Reply(500, "{}") : confirmar(req));

        subirYEsperar();

        assertEquals(2, server.countOf(RUTA));
        assertEquals("el primer lote (50) falló y el segundo (10) se subió", 50, dao.getUnsynced().size());
    }

    @Test
    public void respuesta400_dejaLasVentasPendientes() throws Exception {
        insertarVenta(2.4);
        server.on(RUTA, 400, "{\"success\":false,\"error\":\"Cuerpo inválido\"}");

        subirYEsperar();

        assertEquals(1, dao.getUnsynced().size());
    }

    @Test
    public void respuestaSinJson_dejaLasVentasPendientes() throws Exception {
        insertarVenta(2.4);
        server.on(RUTA, 200, "<html>proxy</html>");

        subirYEsperar();

        assertEquals(1, dao.getUnsynced().size());
    }

    @Test
    public void ventaEditadaMientrasSeEnviaba_siguePendiente() throws Exception {
        int id = insertarVenta(2.4);
        server.on(RUTA, req -> {
            VentaLocal v = dao.getById(id);
            v.total = 9.9;
            v.isSynced = false;
            dao.update(v);
            return confirmar(req);
        });

        subirYEsperar();

        assertEquals("la versión nueva no llegó al servidor", 1, dao.getUnsynced().size());
    }

    @Test
    public void sinRed_dejaPendienteYPermiteReintentarDespues() throws Exception {
        insertarVenta(2.4);
        server.close();   // nadie escucha: fallo de conexión
        subirYEsperar();
        assertEquals(1, dao.getUnsynced().size());

        server = new FakeApiServer();
        SecurePreferencesHelper.getPrinterPrefs(context).edit().putString("backend_base_url", server.baseUrl()).commit();
        server.on(RUTA, req -> confirmar(req));
        subirYEsperar();

        assertTrue("tras el fallo de red la bandera debe haberse liberado", dao.getUnsynced().isEmpty());
    }

    @Test
    public void sesionDeOtroNegocio_noSubeNada() throws Exception {
        insertarVenta(2.4);
        Auth.store(context).save(new AuthSession("at", "rt", System.currentTimeMillis() + 600_000L,
                System.currentTimeMillis() + 86_400_000L, "ana", "encargado", "taller", false));
        server.on(RUTA, req -> confirmar(req));

        subirYEsperar();

        assertEquals(0, server.countOf(RUTA));
        assertEquals(1, dao.getUnsynced().size());
    }

    @Test
    public void sinVentasPendientes_noHaceNingunaPeticion() throws Exception {
        server.on(RUTA, req -> confirmar(req));

        subirYEsperar();

        assertEquals(0, server.countOf(RUTA));
    }

    @Test
    public void ventaSinUuid_seLeAsignaYSePersisteAntesDeEnviarla() throws Exception {
        int id = insertarVenta(2.4);
        VentaLocal sinUuid = dao.getById(id);
        sinUuid.uuid = "";
        dao.update(sinUuid);
        server.on(RUTA, req -> confirmar(req));

        subirYEsperar();

        String enviado = new JSONObject(server.requests().get(0).body).getJSONArray("orders").getJSONObject(0).getString("uuid");
        assertTrue(enviado.length() >= 32);
        assertEquals(enviado, dao.getById(id).uuid);
    }

    @Test
    public void respuesta401_dejaLasVentasPendientesYLaSubidaSiguePudiendoReintentarse() throws Exception {
        insertarVenta(2.4);
        server.on(RUTA, 401, "{\"success\":false}");
        subirYEsperar();
        assertEquals(1, dao.getUnsynced().size());

        server.on(RUTA, req -> confirmar(req));
        subirYEsperar();

        assertTrue(dao.getUnsynced().isEmpty());
    }

    @Test
    public void confirmacionParcial_soloMarcaLasVentasConfirmadas() throws Exception {
        int a = insertarVenta(1.0);
        int b = insertarVenta(2.0);
        server.on(RUTA, req -> new FakeApiServer.Reply(200,
                "{\"success\":true,\"syncedIds\":[" + a + "],\"rejected\":[]}"));

        subirYEsperar();

        assertTrue(dao.getById(a).isSynced);
        assertFalse(dao.getById(b).isSynced);
    }

    @Test
    public void ventaEditadaEnLasLineasMientrasSeEnviaba_siguePendiente() throws Exception {
        int id = insertarVenta(2.4);
        server.on(RUTA, req -> {
            VentaLocal v = dao.getById(id);
            v.itemsJson = "[{\"nombre\":\"B\",\"cantidad\":2,\"precio_unitario\":1.2,\"importe_linea\":2.4}]";
            v.isSynced = false;
            dao.update(v);
            return confirmar(req);
        });

        subirYEsperar();

        assertEquals(1, dao.getUnsynced().size());
    }

    @Test
    public void sesionRealDelMismoNegocio_enviaElTokenEnLaCabecera() throws Exception {
        insertarVenta(2.4);
        Auth.store(context).save(new AuthSession("at_valido", "rt", System.currentTimeMillis() + 600_000L,
                System.currentTimeMillis() + 86_400_000L, "ana", "encargado", "fruteria", false));
        server.on(RUTA, req -> confirmar(req));

        subirYEsperar();

        assertEquals(1, server.countOf(RUTA));
        assertEquals("Bearer at_valido", server.requests().get(0).headers.get("authorization"));
        assertNotNull(server.requests().get(0).headers.get("content-type"));
    }

    @Test
    public void accesoCaducadoEnElServidor_renuevaLaSesionYSubeLaVentaUnaSolaVez() throws Exception {
        int id = insertarVenta(2.4);
        Auth.store(context).save(new AuthSession("at_viejo", "rt_viejo", System.currentTimeMillis() + 600_000L,
                System.currentTimeMillis() + 86_400_000L, "ana", "encargado", "fruteria", false));
        server.on("/api/tpv/auth/refresh", 200, "{\"success\":true,\"token\":\"at_nuevo\",\"refreshToken\":\"rt_nuevo\","
                + "\"expiresIn\":1800,\"refreshExpiresAt\":\"2099-10-23T12:00:00.000Z\","
                + "\"user\":{\"restaurantId\":\"fruteria\",\"role\":\"encargado\"}}");
        server.on(RUTA, req -> "Bearer at_nuevo".equals(req.headers.get("authorization"))
                ? confirmar(req) : new FakeApiServer.Reply(401, "{\"success\":false}"));

        subirYEsperar();

        assertEquals(1, server.countOf("/api/tpv/auth/refresh"));
        assertEquals(2, server.countOf(RUTA));   // el intento rechazado y el reintento con el token nuevo
        assertTrue(dao.getById(id).isSynced);
    }

    @Test
    public void trasSincronizarConExito_otraSubidaNoVuelveAEnviarLaVenta() throws Exception {
        insertarVenta(2.4);
        server.on(RUTA, req -> confirmar(req));

        subirYEsperar();
        subirYEsperar();

        assertEquals(1, server.countOf(RUTA));
    }

    @Test
    public void servidorCaidoYRecuperado_laVentaSeSubeEnElSiguienteIntentoSinDuplicarse() throws Exception {
        int id = insertarVenta(2.4);
        server.on(RUTA, 503, "{\"success\":false}");
        subirYEsperar();
        assertFalse(dao.getById(id).isSynced);

        server.on(RUTA, req -> confirmar(req));
        subirYEsperar();
        subirYEsperar();

        assertTrue(dao.getById(id).isSynced);
        assertEquals(2, server.countOf(RUTA));   // un intento fallido y uno correcto; el tercero no envía nada
    }

    @Test
    public void subidaSolapada_seRepiteAlTerminarLaQueEstabaEnMarcha() throws Exception {
        int id = insertarVenta(2.4);
        server.on(RUTA, req -> confirmar(req));
        java.util.concurrent.atomic.AtomicBoolean enCurso = estatico("EN_CURSO");
        java.util.concurrent.atomic.AtomicBoolean repetir = estatico("REPETIR");
        try {
            enCurso.set(true);
            subirYEsperar();
            assertEquals("con otra subida en marcha no se envía nada", 0, server.countOf(RUTA));
            assertTrue("queda anotada la repetición", repetir.get());

            enCurso.set(false);
            subirYEsperar();   // al terminar esta, libera y lanza la repetición pendiente
            esperarHasta(() -> !repetir.get());
        } finally {
            enCurso.set(false);
            repetir.set(false);
        }
        assertTrue(dao.getById(id).isSynced);
    }

    @Test
    public void ejecutorCerradoAlRegistrarElResultado_liberaLaBandera() throws Exception {
        insertarVenta(2.4);
        server.on(RUTA, req -> confirmar(req));
        AtomicInteger llamadas = new AtomicInteger();
        java.util.concurrent.Executor cierraTrasLaPrimera = tarea -> {
            if (llamadas.getAndIncrement() > 0) throw new java.util.concurrent.RejectedExecutionException("cerrado");
            hiloDb.execute(tarea);
        };
        CountDownLatch fin = new CountDownLatch(1);

        new VentaUploader(context, cierraTrasLaPrimera, rechazadasAvisadas::addAndGet).subirPendientes(fin::countDown);

        assertTrue(fin.await(15, TimeUnit.SECONDS));
        assertFalse("la bandera no debe quedar activa", estatico("EN_CURSO").get());
        assertEquals(1, dao.getUnsynced().size());
    }

    @Test
    public void errorPreparandoLaSubida_liberaYTermina() throws Exception {
        insertarVenta(2.4);
        SecurePreferencesHelper.getPrinterPrefs(context).edit().remove("current_tenant").commit();
        CountDownLatch fin = new CountDownLatch(1);

        new VentaUploader(context, hiloDb, null).subirPendientes(fin::countDown);

        assertTrue(fin.await(15, TimeUnit.SECONDS));
        assertEquals(0, server.countOf(RUTA));
    }

    private static java.util.concurrent.atomic.AtomicBoolean estatico(String campo) throws Exception {
        java.lang.reflect.Field f = VentaUploader.class.getDeclaredField(campo);
        f.setAccessible(true);
        return (java.util.concurrent.atomic.AtomicBoolean) f.get(null);
    }

    private static void esperarHasta(java.util.function.BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + 5000;
        while (!condicion.getAsBoolean() && System.currentTimeMillis() < limite) Thread.sleep(10);
    }
}
