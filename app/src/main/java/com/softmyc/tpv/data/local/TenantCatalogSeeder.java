package com.softmyc.tpv.data.local;

import android.content.Context;
import android.util.Log;

import com.softmyc.tpv.data.CsvParser;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeder de datos multi-tenant para inicializar bases de datos independientes
 * por cada sector de negocio en SoftMYC.
 */
public class TenantCatalogSeeder {

    private static final String TAG = "TenantCatalogSeeder";
    /** Solo estos negocios tienen catálogo inicial; cualquier otro arranca con la base vacía. */
    private static final Set<String> KNOWN_CATALOGS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "lanube", "elbocata", "fruteria", "taller", "paqueteria", "mayorista")));
    private static final String STATE_TABLE = "catalog_state";
    private static final String SEEDED_KEY = "catalogo_sembrado";
    private static final String CREATE_STATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + STATE_TABLE + " (clave TEXT NOT NULL PRIMARY KEY, valor TEXT)";

    public static String getBusinessName(String tenantId) {
        if (tenantId == null) return "La Nube Bocatería";
        String id = tenantId.toLowerCase().trim();
        switch (id) {
            case "elbocata":
            case "softmyc_elbocata":
                return "El Bocata Gourmet";
            case "fruteria":
            case "softmyc_fruteria":
                return "Frutería El Huerto Bio";
            case "taller":
            case "softmyc_taller":
                return "Taller Mecánico MotorSport";
            case "paqueteria":
            case "softmyc_paqueteria":
                return "SoftMYC Express Paquetería";
            case "mayorista":
            case "softmyc_mayorista":
                return "Central Mayorista de Distribución";
            case "lanube":
            case "softmyc_lanube":
            default:
                return "La Nube Bocatería";
        }
    }

    public static String getBusinessSector(String tenantId) {
        if (tenantId == null) return "Hostelería";
        String id = tenantId.toLowerCase().trim();
        switch (id) {
            case "fruteria":
            case "softmyc_fruteria":
                return "Comercio al Peso / Granel";
            case "taller":
            case "softmyc_taller":
                return "Servicios y Automoción";
            case "paqueteria":
            case "softmyc_paqueteria":
                return "Logística y Transporte";
            case "mayorista":
            case "softmyc_mayorista":
                return "Venta y Distribución B2B";
            case "elbocata":
            case "softmyc_elbocata":
            case "lanube":
            case "softmyc_lanube":
            default:
                return "Hostelería y Restauración";
        }
    }

    public static String getBusinessIcon(String tenantId) {
        if (tenantId == null) return "🍔";
        String id = tenantId.toLowerCase().trim();
        switch (id) {
            case "elbocata":
            case "softmyc_elbocata":
                return "🥪";
            case "fruteria":
            case "softmyc_fruteria":
                return "🍎";
            case "taller":
            case "softmyc_taller":
                return "🔧";
            case "paqueteria":
            case "softmyc_paqueteria":
                return "📦";
            case "mayorista":
            case "softmyc_mayorista":
                return "🏭";
            case "lanube":
            case "softmyc_lanube":
            default:
                return "🍔";
        }
    }

    /**
     * Puebla el catálogo del tenant si está vacío. Serializado: el seeder de {@link AppDatabase} y el cambio
     * de negocio del administrador pueden coincidir.
     *
     * @return true si el catálogo ya existía o se pobló; false si hubo un error (p. ej. base no abrible).
     */
    public static synchronized boolean seedTenantSync(Context context, AppDatabase db, String tenantId) {
        if (context == null || db == null) return false;
        final String safeTenant = TenantContext.canonicalId(tenantId);
        if (safeTenant == null || !KNOWN_CATALOGS.contains(safeTenant)) {
            Log.w(TAG, "Negocio sin catálogo inicial conocido: su base queda vacía");
            return false;
        }

        try {
            ensureStateTable(db);
            // Todo en una transacción: o queda el catálogo completo y marcado, o nada.
            db.runInTransaction(() -> {
                if (isMarkedSeeded(db)) return;
                if (db.productoDao().getAll().isEmpty()) {
                    Log.i(TAG, "🌱 Inicializando catálogo inicial para tenant: " + safeTenant);
                    seedCatalog(context, db, safeTenant);
                }
                markSeeded(db);
            });
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error poblando catálogo inicial para " + safeTenant, e);
            return false;
        }
    }

    private static void seedCatalog(Context context, AppDatabase db, String safeTenant) {
        switch (safeTenant) {
            case "fruteria":
                seedFruteria(db);
                break;
            case "taller":
                seedTaller(db);
                break;
            case "paqueteria":
                seedPaqueteria(db);
                break;
            case "mayorista":
                seedMayorista(db);
                break;
            case "elbocata":
                seedElBocata(db);
                break;
            default:
                seedLaNube(context, db);
                break;
        }
    }

    /** Marca persistente de "ya sembrado": una base vaciada a propósito no se vuelve a sembrar. */
    private static void ensureStateTable(AppDatabase db) {
        db.getOpenHelper().getWritableDatabase().execSQL(CREATE_STATE_TABLE_SQL);
    }

    private static boolean isMarkedSeeded(AppDatabase db) {
        try (android.database.Cursor cursor = db.getOpenHelper().getReadableDatabase()
                .query("SELECT 1 FROM " + STATE_TABLE + " WHERE clave = ?", new Object[]{SEEDED_KEY})) {
            return cursor.moveToFirst();
        }
    }

    private static void markSeeded(AppDatabase db) {
        db.getOpenHelper().getWritableDatabase()
                .execSQL("INSERT OR REPLACE INTO " + STATE_TABLE + " (clave, valor) VALUES (?, ?)", new Object[]{SEEDED_KEY, "1"});
    }

    /** Lanza si falta un CSV: el seeder completo se revierte en lugar de dejar un catálogo parcial. */
    private static void seedLaNube(Context context, AppDatabase db) {
        String[][] assetFiles = {
                {"menu_bocadillos_formato_visual.csv", "Bocadillos"},
                {"menu_hamburguesas_mayusculas.csv", "Hamburguesas"},
                {"otros.csv", "Otros"},
                {"salsas.csv", "Salsas"},
                {"ingredientes_precio.csv", "Extras"}
        };
        for (String[] fileInfo : assetFiles) {
            try (InputStream is = context.getAssets().open(fileInfo[0])) {
                CsvParser.ParseResult res = CsvParser.parse(is, fileInfo[1]);
                if (!res.productos.isEmpty()) db.productoDao().insertAll(res.productos);
                if (!res.extras.isEmpty()) db.extraDao().insertAll(res.extras);
                if (!res.salsas.isEmpty()) db.salsaDao().insertAll(res.salsas);
            } catch (Exception e) {
                throw new IllegalStateException("Error cargando asset " + fileInfo[0], e);
            }
        }
    }

    private static void seedElBocata(AppDatabase db) {
        List<ProductoLocal> prods = new ArrayList<>();
        prods.add(new ProductoLocal(10, "Serranito Gourmet", 5.80, "Bocadillos Gourmet",
                Arrays.asList("Lomo ibérico al ajillo", "Jamón serrano reserva", "Pimiento verde asado", "Tomate natural"), "UD"));
        prods.add(new ProductoLocal(11, "Bocata Calamares Deluxe", 5.20, "Bocadillos Gourmet",
                Arrays.asList("Calamares a la andaluza", "Alioli casero suave", "Rúcula fresca"), "UD"));
        prods.add(new ProductoLocal(12, "Hamburguesa Angus 200g", 7.50, "Hamburguesas",
                Arrays.asList("Carne Angus 200g", "Queso cheddar curado", "Cebolla pochada", "Bacon crujiente", "Salsa BBQ casera"), "UD"));
        prods.add(new ProductoLocal(13, "Patatas Bravas Tradicionales", 3.80, "Raciones",
                Arrays.asList("Patatas agria seleccionadas", "Salsa brava artesana picante"), "UD"));
        prods.add(new ProductoLocal(14, "Tarta de Queso Horneada", 4.20, "Postres",
                Arrays.asList("Queso crema artesano", "Coulis de frambuesa"), "UD"));
        prods.add(new ProductoLocal(15, "Cerveza Artesana IPA", 3.50, "Bebidas",
                Arrays.asList("Botellín 33cl", "Lúpulo aromático"), "UD"));
        db.productoDao().insertAll(prods);

        List<ExtraLocal> extras = new ArrayList<>();
        extras.add(new ExtraLocal("Huevo Frito Campero", 1.00));
        extras.add(new ExtraLocal("Doble Queso Gouda", 0.80));
        extras.add(new ExtraLocal("Bacon Ahumado Extra", 0.90));
        extras.add(new ExtraLocal("Cebolla Crujiente", 0.50));
        db.extraDao().insertAll(extras);

        List<SalsaLocal> salsas = new ArrayList<>();
        salsas.add(new SalsaLocal("Brava Artesana"));
        salsas.add(new SalsaLocal("Alioli Suave"));
        salsas.add(new SalsaLocal("Mostaza a la Antigua"));
        salsas.add(new SalsaLocal("Mayonesa Trufada"));
        db.salsaDao().insertAll(salsas);
    }

    private static void seedFruteria(AppDatabase db) {
        List<ProductoLocal> prods = new ArrayList<>();
        prods.add(new ProductoLocal(101, "Manzanas Golden Bio", 2.40, "Frutas Frescas",
                Arrays.asList("Origen Lleida", "Calibre Extra", "Cultivo ecológico certificado"), "KG"));
        prods.add(new ProductoLocal(102, "Plátanos de Canarias", 2.95, "Frutas Frescas",
                Arrays.asList("IGP Canarias", "Maduración natural en planta"), "KG"));
        prods.add(new ProductoLocal(103, "Naranjas de Mesa Valencia", 1.90, "Frutas Frescas",
                Arrays.asList("Cítricos Valencia", "Zumo abundante y sabor dulce"), "KG"));
        prods.add(new ProductoLocal(104, "Tomate Rosa de Barbastro", 3.80, "Verduras y Hortalizas",
                Arrays.asList("Huerta de Huesca", "Piel fina y carnoso"), "KG"));
        prods.add(new ProductoLocal(105, "Aguacate Hass Maduro", 4.90, "Frutas Frescas",
                Arrays.asList("Cultivo tropical Costa Tropical", "Punto óptimo"), "KG"));
        prods.add(new ProductoLocal(106, "Patata Nueva Lavada", 1.25, "Verduras y Hortalizas",
                Arrays.asList("Variedad agria", "Especial fritura y guiso"), "KG"));
        prods.add(new ProductoLocal(107, "Pimiento Rojo Morrón", 2.60, "Verduras y Hortalizas",
                Arrays.asList("Carne gruesa", "Apto para asar"), "KG"));
        prods.add(new ProductoLocal(108, "Nueces del País con Cáscara", 6.80, "Granel y Secos",
                Arrays.asList("Cosecha nacional", "Ricas en Omega 3"), "KG"));
        db.productoDao().insertAll(prods);

        List<ExtraLocal> extras = new ArrayList<>();
        extras.add(new ExtraLocal("Bolsa Papel Kraft (-0.050kg)", 0.10));
        extras.add(new ExtraLocal("Bandeja Plástico (-0.120kg)", 0.20));
        extras.add(new ExtraLocal("Caja Madera Fruta (-0.350kg)", 0.80));
        extras.add(new ExtraLocal("Malla Reutilizable Algodón", 0.50));
        db.extraDao().insertAll(extras);

        List<SalsaLocal> salsas = new ArrayList<>();
        salsas.add(new SalsaLocal("Maduración óptima hoy"));
        salsas.add(new SalsaLocal("Para cocinar / zumo"));
        salsas.add(new SalsaLocal("Calibre grande extra"));
        salsas.add(new SalsaLocal("Bolsa biodegradable"));
        db.salsaDao().insertAll(salsas);
    }

    private static void seedTaller(AppDatabase db) {
        List<ProductoLocal> prods = new ArrayList<>();
        prods.add(new ProductoLocal(201, "Mano de Obra Mecánica", 45.00, "Mano de Obra",
                Arrays.asList("Mecánica general y reparación", "Técnico especialista"), "HORA"));
        prods.add(new ProductoLocal(202, "Diagnosis Electrónica OBD", 35.00, "Diagnosis",
                Arrays.asList("Escaneo integral de centralitas", "Borrado código averías"), "HORA"));
        prods.add(new ProductoLocal(203, "Cambio Aceite Sintético 5W30", 65.00, "Mantenimiento",
                Arrays.asList("Aceite 100% sintético 5L", "Homologación fabricante"), "UD"));
        prods.add(new ProductoLocal(204, "Filtro de Aceite Mann-Filter", 16.50, "Repuestos",
                Arrays.asList("Cartucho filtrante de alta pureza"), "UD"));
        prods.add(new ProductoLocal(205, "Pastillas Freno Delanteras Brembo", 62.00, "Repuestos",
                Arrays.asList("Juego 4 pastillas cerámicas", "Sin amianto"), "UD"));
        prods.add(new ProductoLocal(206, "Neumático Michelin 205/55 R16", 82.00, "Neumáticos",
                Arrays.asList("Modelo Primacy 4", "Eficiencia A", "Agarre mojado A"), "UD"));
        prods.add(new ProductoLocal(207, "Alineación Dirección Láser", 38.00, "Mantenimiento",
                Arrays.asList("Ajuste cotas 4 ruedas con sensor óptico"), "UD"));
        prods.add(new ProductoLocal(208, "Carga Gas Climatizador R134a", 55.00, "Mantenimiento",
                Arrays.asList("Vaciado, test de estanqueidad y recarga gas/aceite"), "UD"));
        db.productoDao().insertAll(prods);

        List<ExtraLocal> extras = new ArrayList<>();
        extras.add(new ExtraLocal("Servicio Urgente (+20%)", 9.00));
        extras.add(new ExtraLocal("Desplazamiento Domicilio", 15.00));
        extras.add(new ExtraLocal("Tasa Gestión Residuo Aceite", 2.50));
        extras.add(new ExtraLocal("Tasa NFU Neumático Fuera de Uso", 1.85));
        db.extraDao().insertAll(extras);

        List<SalsaLocal> salsas = new ArrayList<>();
        salsas.add(new SalsaLocal("Revisión 20 puntos de seguridad"));
        salsas.add(new SalsaLocal("Informe diagnóstico impreso"));
        salsas.add(new SalsaLocal("Prueba dinámica en carretera"));
        salsas.add(new SalsaLocal("Reciclaje ecológico fluidos"));
        db.salsaDao().insertAll(salsas);
    }

    private static void seedPaqueteria(AppDatabase db) {
        List<ProductoLocal> prods = new ArrayList<>();
        prods.add(new ProductoLocal(301, "Envío Estándar 24/48h hasta 2kg", 4.90, "Envíos Nacionales",
                Arrays.asList("Entrega peninsular domicilio", "Trazabilidad online por código de barras"), "UD"));
        prods.add(new ProductoLocal(302, "Envío Express 14h hasta 5kg", 9.50, "Envíos Urgentes",
                Arrays.asList("Entrega prioritaria garantizada antes de las 14:00"), "UD"));
        prods.add(new ProductoLocal(303, "Kilo Adicional Exceso Peso", 1.80, "Envíos Nacionales",
                Arrays.asList("Facturación por peso real o volumétrico"), "KG"));
        prods.add(new ProductoLocal(304, "Envío Internacional Europa", 19.50, "Envíos Especiales",
                Arrays.asList("Paquete hasta 3kg Zona Euro", "Despacho de aduana incluido"), "UD"));
        prods.add(new ProductoLocal(305, "Caja Cartón Reforzada M", 2.50, "Embalajes y Cajas",
                Arrays.asList("Canal doble resistente", "Dimensiones 35x25x20 cm"), "UD"));
        prods.add(new ProductoLocal(306, "Caja Cartón Grande XL", 4.20, "Embalajes y Cajas",
                Arrays.asList("Cartón ondulado reforzado", "Dimensiones 60x40x40 cm"), "UD"));
        prods.add(new ProductoLocal(307, "Sobre Acolchado Burbuja", 1.50, "Embalajes y Cajas",
                Arrays.asList("Papel kraft con burbuja interna", "Cierre adhesivo"), "UD"));
        db.productoDao().insertAll(prods);

        List<ExtraLocal> extras = new ArrayList<>();
        extras.add(new ExtraLocal("Seguro Transporte Todo Riesgo", 3.50));
        extras.add(new ExtraLocal("Entrega Garantizada en Sábado", 6.00));
        extras.add(new ExtraLocal("Notificación SMS y Firma Digital", 0.60));
        extras.add(new ExtraLocal("Gestión Contra Reembolso", 2.00));
        db.extraDao().insertAll(extras);

        List<SalsaLocal> salsas = new ArrayList<>();
        salsas.add(new SalsaLocal("Tratar con cuidado (Frágil)"));
        salsas.add(new SalsaLocal("No voltear"));
        salsas.add(new SalsaLocal("Entregar en portería"));
        salsas.add(new SalsaLocal("Llamar antes de entregar"));
        db.salsaDao().insertAll(salsas);
    }

    private static void seedMayorista(AppDatabase db) {
        List<ProductoLocal> prods = new ArrayList<>();
        prods.add(new ProductoLocal(401, "Aceite Oliva Virgen Extra (Caja 5L)", 45.00, "Aceites y Grasas",
                Arrays.asList("Caja profesional 4 garrafas 5L", "Escalado por volumen"), "UD"));
        prods.add(new ProductoLocal(402, "Café Grano Tueste Natural (Saco 6kg)", 65.00, "Café y Grano",
                Arrays.asList("100% Arábica hostelería", "Envasado con válvula desgasificadora"), "UD"));
        prods.add(new ProductoLocal(403, "Harina Panificable W300 (Saco 25kg)", 28.50, "Harinas y Masas",
                Arrays.asList("Harina de trigo de media fuerza", "Cereal nacional"), "UD"));
        prods.add(new ProductoLocal(404, "Cerveza Artesana Lager (Caja 24 uds)", 32.00, "Bebidas",
                Arrays.asList("Botellines 33cl cristal", "Elaboración tradicional"), "UD"));
        prods.add(new ProductoLocal(405, "Arroz Grano Redondo (Saco 20kg)", 36.00, "Alimentación Seca",
                Arrays.asList("Categoría extra", "Especial arrocerías"), "UD"));
        db.productoDao().insertAll(prods);

        List<ExtraLocal> extras = new ArrayList<>();
        extras.add(new ExtraLocal("Palet Europeo Flejado (Gratis)", 0.00));
        extras.add(new ExtraLocal("Embalaje Isotérmico Palet", 5.00));
        extras.add(new ExtraLocal("Seguro Transporte Carga Completa", 12.00));
        db.extraDao().insertAll(extras);

        List<SalsaLocal> salsas = new ArrayList<>();
        salsas.add(new SalsaLocal("Entrega en muelle / plataforma"));
        salsas.add(new SalsaLocal("Con albarán valorado desglosado"));
        salsas.add(new SalsaLocal("Copia departamento de compras"));
        salsas.add(new SalsaLocal("Certificado lote sanitario"));
        db.salsaDao().insertAll(salsas);
    }
}
