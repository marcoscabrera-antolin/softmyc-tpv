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
```

Las cuatro entidades no tienen claves foráneas: `VentaLocal.itemsJson` guarda el pedido serializado (ver `ItemsSerializerSync`). Room `version = 6`, migraciones manuales 1 a 6, sin `fallbackToDestructiveMigration()`.

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
