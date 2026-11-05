# Arquitectura de SoftMYC TPV (diagramas)

Diagramas en Mermaid (se ven en GitHub y en cualquier visor de Markdown con Mermaid). Cada nombre de clase existe en el código a fecha 16-oct-2026; las flechas de dependencia se han contrastado con `grep` de los `import` y llamadas citadas en cada sección. Fuente única: este fichero.

## 1. Capas y dependencias

```mermaid
flowchart TB
    subgraph presentation["presentation (Activities, adapters, viewmodel/)"]
        PA[PedidoActivity]
        PWA[PedidosWebActivity]
        LA[LoginActivity]
        VMP[PedidoViewModel]
        VML[LoginViewModel]
    end
    subgraph domain["domain (modelos, use cases, pricing, puertos)"]
        UC[ImprimirYGuardarVentaUseCase<br/>CalcularTotalUseCase]
        PRC[pricing/PricingStrategy*]
        PORT[port/: VentaStore, TicketPrinter,<br/>TicketComposer, ItemsSerializer, PrinterSettings]
        REPO[repository/: ProductoRepository,<br/>VentaRepository, CatalogoTenantRepository]
    end
    subgraph data["data (Room, red, impresora, sync)"]
        ROOM[local/AppDatabase + DAOs]
        REPOIMPL[repo/*Room, *Android, *Prefs]
        NET[network/OrderSyncService]
        AUTH[auth/Auth, SessionManager]
        SYNC[sync/SyncManager, VentaUploader]
        PRN[printer/PrinterHelper]
    end
    presentation --> domain
    presentation --> data
    data -. implementa .-> PORT
    data -. implementa .-> REPO
    REPOIMPL --> ROOM
    SYNC --> NET
    NET --> AUTH
```

Nota: `presentation` también depende de `data` (p. ej. `PedidosWebActivity` usa `OrderSyncService`); la inversión de dependencias solo está completa para el flujo de cobro (puertos de `domain/port`).

## 2. Puertos y adaptadores (inversión de dependencias del cobro)

```mermaid
classDiagram
    class ImprimirYGuardarVentaUseCase
    class VentaStore { <<interface>> }
    class TicketPrinter { <<interface>> }
    class TicketComposer { <<interface>> }
    class ItemsSerializer { <<interface>> }
    class PrinterSettings { <<interface>> }
    class VentaStoreRoom
    class TicketPrinterAndroid
    class TicketComposerAndroid
    class ItemsSerializerSync
    class PrinterSettingsPrefs
    ImprimirYGuardarVentaUseCase --> VentaStore
    ImprimirYGuardarVentaUseCase --> TicketPrinter
    ImprimirYGuardarVentaUseCase --> TicketComposer
    ImprimirYGuardarVentaUseCase --> ItemsSerializer
    ImprimirYGuardarVentaUseCase --> PrinterSettings
    VentaStore <|.. VentaStoreRoom
    TicketPrinter <|.. TicketPrinterAndroid
    TicketComposer <|.. TicketComposerAndroid
    ItemsSerializer <|.. ItemsSerializerSync
    PrinterSettings <|.. PrinterSettingsPrefs
```

## 3. Multi-tenant: una base SQLite por negocio

```mermaid
flowchart LR
    LOGIN[Login / AdminTenantSelectorDialog] -->|restaurantId| TC[TenantContext<br/>canonicalId, read, save]
    TC --> PREFS[(SecurePreferencesHelper<br/>getPrinterPrefs: restaurant_id, current_tenant)]
    TC --> ADB[AppDatabase.getDatabaseForTenant]
    ADB --> MAP{{TENANT_INSTANCES<br/>mapa de instancias Room}}
    MAP --> DB1[(softmyc_database.db<br/>lanube)]
    MAP --> DB2[(softmyc_negocio.db<br/>resto de negocios)]
    ADB -->|al crear la instancia| SEED[TenantCatalogSeeder<br/>segundo plano]
    TC -. sin negocio válido .-> EX[NoActiveTenantException<br/>BaseActivity vuelve al login]
```

## 4. Motor universal frente al modelo heredado

```mermaid
flowchart LR
    subgraph legado["Modelo heredado (hostelería)"]
        P[Producto] --> PI[PedidoItem / CustomizedOrderItem]
        IO[IngredientOption]
        CT[CalcularTotalUseCase]
    end
    subgraph universal["Modelo agnóstico (Fase 2)"]
        CI[CatalogItem] --> UOI[UniversalOrderItem]
        IM[ItemModifier] --> UOI
        UO[UniversalOrder]
        PSF[PricingStrategyFactory] --> S1[StandardUnit]
        PSF --> S2[Weight]
        PSF --> S3[Time]
        PSF --> S4[TieredVolume]
        CUT[CalculateUniversalTotalUseCase]
    end
    LOA[LegacyOrderAdapter] -->|convierte| P
    LOA -->|a| CI
    LOA -->|a| IM
    PI --> CT
    UOI --> CUT --> PSF
```

## 5. Secuencia: sesión, renovación única y sincronización de ventas

```mermaid
sequenceDiagram
    participant UI as Activity / ViewModel
    participant A as Auth (fachada)
    participant SM as SessionManager
    participant HTTP as OkHttp (AuthInterceptor + TokenAuthenticator)
    participant API as API Node (/api/tpv)
    participant J as SyncJobService (JobScheduler)
    participant VU as VentaUploader
    participant G as SyncGuard

    UI->>A: login(usuario, clave)
    A->>API: POST /api/tpv/auth/login
    API-->>A: access + refresh (hash en BD)
    A->>SM: SessionStore.save (EncryptedSharedPreferences)
    J->>VU: subirPendientes()
    VU->>G: canUploadFor(sesión, negocio local)
    alt negocio de la base local ≠ negocio de la sesión
        G-->>VU: no (SESION_OTRO_NEGOCIO)
    else mismo negocio
        VU->>HTTP: syncLocalOrders(lote de ventas isSynced=false)
        HTTP->>API: POST /api/tpv/orders/sync (Bearer access)
        alt 401 (acceso caducado)
            API-->>HTTP: 401
            HTTP->>SM: refreshIfStale(token viejo) [synchronized]
            SM->>API: POST /api/tpv/auth/refresh
            API-->>SM: nueva pareja de tokens
            HTTP->>API: reintento con el token nuevo
        end
        API-->>VU: 200
        VU->>VU: marca ventas isSynced = true en Room
    end
```

## 6. Modelo de datos

### 6.1 Room (tablet, por negocio)

```mermaid
erDiagram
    ProductoLocal {
        int codigo PK
        string nombre
        double precio
        string categoria
        list ingredientes
        string unidadMedida
        int stock "-1 = sin control"
        double gastosAnadidos "por unidad; v7"
    }
    ExtraLocal { string nombre PK
        double precio }
    SalsaLocal { string nombre PK }
    VentaLocal {
        int id PK
        long fecha
        double total
        string itemsJson
        boolean isSynced
        string metodoPago
        string uuid "añadido en la migración 5 a 6"
    }
    IngredienteLocal {
        long id PK
        string nombre
        string nombreNormalizado UK
        string unidad "G, ML o UD"
        double stock "null = sin control; puede ser negativo"
        double precioCompra "€/kg, €/L o €/ud"
    }
    RecetaLineaLocal {
        long id PK
        int productoCodigo "sin FK a productos"
        long ingredienteId FK
        double cantidad
        string nombreEnCarta
    }
    ExtraConsumoLocal {
        long id PK
        string extraNormalizado "sin FK a extras"
        long ingredienteId FK
        double cantidad
    }
    IngredienteLocal ||--o{ RecetaLineaLocal : "ON DELETE CASCADE"
    IngredienteLocal ||--o{ ExtraConsumoLocal : "ON DELETE CASCADE"
```

Las entidades del catálogo y de ventas no tienen claves foráneas: `VentaLocal.itemsJson` guarda el pedido serializado (ver `ItemsSerializerSync`). Room `version = 7`, migraciones manuales 1 a 7, sin `fallbackToDestructiveMigration()`.

#### Escandallo (v7): stock de ingredientes y coste por receta

- **Modelo.** `ingredientes` (unidad G/ML/UD, existencias y precio de compra), `receta_lineas` (qué lleva cada producto por unidad vendida) y `extra_consumos` (qué gasta cada extra). Recetas y extras se enlazan por código o nombre normalizado, **sin clave foránea a productos ni a extras**: `CatalogSync` borra y reinserta el catálogo al sincronizar y una cascada se llevaría las recetas. Solo hay cascada desde `ingredientes`.
- **Descuento al cobrar.** `VentaStockRegistrar` llama a `IngredienteStockRegistrar` dentro de la misma transacción que guarda la venta: o se guardan las dos cosas o ninguna. Al editar una venta solo se aplica la diferencia (`ConsumoIngredientes.diferencia`). Las ventas antiguas sin código de producto no tocan ingredientes, igual que no tocan el stock de producto.
- **Reglas** (dominio puro, `domain/usecase/ConsumoIngredientes`):
  - cada unidad gasta la receta; un ingrediente quitado ("sin tomate") no se gasta; cada extra ("+ queso") gasta su ración;
  - **NEG-11**: solo las líneas en unidades (UD) gastan ingredientes; las de peso u horas no tienen receta por unidad (coherente con NEG-09);
  - **NEG-12**: si un ingrediente no llega se avisa al añadir el producto (`AvisoIngredientes`), nunca se bloquea la venta;
  - el stock de ingrediente puede quedar negativo (la pantalla muestra 0 y el descuadre): así editar y deshacer una venta devuelve exactamente lo descontado, cosa que el recorte `MAX(0, …)` del stock de producto no garantiza.
- **Foto del coste.** Al cobrar, cada línea guarda `coste_ingredientes` y `gastos_anadidos` en su JSON (`CostesVenta.anotar`). Si después cambian precios o recetas, el beneficio de las ventas ya hechas no cambia. Las ventas anteriores se valoran con la receta actual.
- **Rentabilidad.** `CalculadoraRentabilidad`: ventas, beneficio bruto (ventas − ingredientes), beneficio neto (bruto − gastos añadidos) y margen sobre coste. Las líneas sin coste conocido no se cuentan como beneficio y se informan aparte.
- **Datos iniciales.** `assets/escandallo_lanube.csv` (generado desde el documento de precios por kilo y por ración del negocio) se carga una sola vez para La Nube; `EscandalloLanubeTest` comprueba que el coste de cada producto coincide con el documento de costes.
- **Raíz de composición.** Las pantallas nuevas (`IngredientesActivity`, `RecetasActivity`) obtienen el repositorio a través de `composicion/Dependencias`, que solo entrega interfaces de dominio. Así no importan `data` y la lista de deuda de `ArquitecturaCapasTest` no crece; una regla nueva del test lo vigila.

### 6.2 PostgreSQL 16 (`tfg-db/initdb/`, multi-tenant por columna `tenant_id`)

```mermaid
erDiagram
    tenants ||--o{ productos : "tenant_id"
    tenants ||--o{ extras : "tenant_id"
    tenants ||--o{ salsas : "tenant_id"
    tenants ||--o{ ventas : "tenant_id"
    tenants ||--o{ usuario_negocios : "tenant_id"
    usuarios ||--o{ usuario_negocios : "usuario_id"
    usuarios ||--o{ sesiones : "usuario_id"
    tenants ||--o{ sesiones : "tenant_id"
    sesiones ||--o| sesiones : "reemplazada_por"
    ventas ||..o{ ventas_historial : "venta_id (sin FK; lo escribe un disparador)"
    tenants { text id PK
        text nombre
        text sector
        boolean activo }
    productos { text tenant_id PK
        int codigo PK
        numeric precio
        int stock }
    ventas { bigint id PK
        text tenant_id
        bigint id_local "UNIQUE con tenant_id y fecha"
        uuid uuid "idempotencia de la subida"
        timestamptz fecha
        numeric total
        jsonb items
        text metodo_pago }
    usuarios { bigint id PK
        text username UK
        text password_hash "scrypt"
        boolean es_admin
        int intentos_fallidos }
    usuario_negocios { bigint usuario_id PK
        text tenant_id PK
        text rol "admin, encargado, tpv_operator" }
    sesiones { uuid id PK
        uuid familia
        bytea access_hash UK
        bytea refresh_hash UK
        timestamptz revocada_en }
```

También existen `api_keys`, `auditoria_auth` y `login_intentos_ip` (sin relaciones con otras tablas en el modelo) y las vistas `venta_lineas` y `ventas_diarias`. `ventas_historial` (SEG-API-02) guarda los valores anteriores de una venta reescrita; `venta_id` no es clave foránea (`tfg-db/initdb/08-api-seguridad.sql`).

## Limitaciones

- Los diagramas están exportados a SVG y PNG en `docs/diagramas/` (junto a su fuente `.mmd`), generados el 16-oct-2026 con `@mermaid-js/mermaid-cli` 11.4.2 y el Chrome del sistema. Para regenerarlos: `npx @mermaid-js/mermaid-cli -p puppeteer.json -i docs/diagramas/01-capas.mmd -o docs/diagramas/01-capas.svg` con `puppeteer.json` = `{"executablePath":"/usr/bin/google-chrome","args":["--no-sandbox"]}`. Si se edita este fichero, hay que mantener las `.mmd` sincronizadas.
- `graphify-out/GRAPH_REPORT.md` fue generado desde el commit `ec787fcb`, distinto del actual; no se ha usado como fuente.
