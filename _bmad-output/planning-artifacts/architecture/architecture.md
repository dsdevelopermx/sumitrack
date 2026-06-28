---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
lastStep: 8
status: complete
completedAt: '2026-06-27'
inputDocuments:
  - _bmad-output/planning-artifacts/briefs/brief-sumitrack-2026-06-21/brief.md
  - _bmad-output/planning-artifacts/prds/prd-sumitrack-2026-06-22/prd.md
  - _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/DESIGN.md
  - _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md
workflowType: 'architecture'
project_name: 'sumitrack'
user_name: 'Josemtz'
date: '2026-06-27'
---

# Architecture Decision Document

_Este documento se construye colaborativamente paso a paso. Las secciones se agregan conforme avanzamos en cada decisión arquitectónica juntos._

## Análisis de Contexto del Proyecto

### Resumen de Requisitos

**Requisitos Funcionales (31 FRs en 9 categorías):**

| Categoría | FRs | Complejidad arq. |
|-----------|-----|-----------------|
| Autenticación y Sesión | FR-1 a FR-4 | Media — token persistente, sin expiración fija v1 |
| Gestión de Clientes | FR-5 a FR-9 | Baja — CRUD + búsqueda local |
| Catálogo de Materiales | FR-10 a FR-11 | Baja — con variantes definidas en UX S-05 |
| Registro de Ventas | FR-12 a FR-17 | Alta — offline obligatorio, folio auto-incremental, lógica de Crédito a Favor en cancelación |
| Cobros y Parcialidades | FR-18 a FR-20 + FR-18b | Alta — Crédito a Favor, recálculo de estatus en cascada |
| Recordatorios y Agenda | FR-21 a FR-23 | Media — push notifications (FCM vs WorkManager pendiente), calendar view |
| Ticket de Venta | FR-24 a FR-26 | Media — PNG en memoria, Bluetooth, Android Share Intent |
| Settings | FR-27 a FR-31 | Baja — descarga al iniciar sesión, sync bidireccional |
| Offline y Sincronización | FR-32 a FR-34 | Muy alta — el riesgo técnico más alto del sistema |

**Requisitos No Funcionales — implicaciones arquitectónicas:**

| NFR | Implicación |
|-----|-------------|
| NFR-1: < 2 min venta, < 10 sec saldo | Todas las lecturas críticas van a SQLite local — nunca a la nube en flujos operativos |
| NFR-2: 100% offline, 99.5% API | API es secundaria en el camino crítico; SQLite es el sistema de registro primario |
| NFR-3: bcrypt/Argon2, HTTPS, sin cifrado SQLite v1 | Seguridad declarada en API; deuda técnica de cifrado local documentada para V2 |
| NFR-4: Multi-tenant sin cambios de código | `tenant_id` en cada tabla y en cada query; aislamiento en todas las capas |
| NFR-5: Android API 26+ | Room disponible, WorkManager disponible, Compose estable |
| NFR-6: Extensible para CFDI | Campos reservados en entidades Venta y Producto desde el esquema inicial |

### Escala y Complejidad

- **Nivel de complejidad:** Alto
- **Dominio técnico:** Mobile-first full-stack con motor de sincronización offline
- **Componentes arquitectónicos estimados:** 8 — Android App, Sync Engine, API REST, Auth Layer, DB Schema (PostgreSQL + SQLite), Ticket Generator, Push Notifications, Bluetooth Print

### Restricciones Técnicas y Dependencias

1. **Stack fijo:** Android (Kotlin/Compose), .NET API, Railway, PostgreSQL — no se re-debate.
2. **Folio auto-incremental por tenant:** requiere coordinación del servidor para garantizar unicidad global. Estrategia: folio provisional local confirmado en el primer sync exitoso.
3. **Notificaciones push:** FCM vs WorkManager local — única decisión de stack pendiente de alto impacto.
4. **Sin CFDI en v1:** el esquema de datos debe prever los campos fiscales desde el inicio (NFR-6).

### Concerns Transversales Identificados

| Concern | Capas afectadas |
|---------|----------------|
| Aislamiento multi-tenant | API, PostgreSQL, SQLite local, todos los endpoints |
| Motor de sync offline | Todas las entidades mutables: Ventas, Cobros, Clientes, Settings |
| Detección y resolución de conflictos | Sync engine + UX S-15 |
| Secuencia de Folio por tenant | Registro de Ventas, sync, servidor |
| Autenticación / sesión offline | Login, middleware de autorización, SQLite |
| Accesibilidad WCAG AA | Toda la capa Android UI (especificada en DESIGN.md / EXPERIENCE.md) |
| Manejo de errores con lenguaje humano | API responses + mensajes en app (sin códigos al usuario) |

## Evaluación de Templates de Inicio

### Dominio Tecnológico Primario

Mobile-first full-stack: dos componentes independientes con stack pre-decidido. Este paso documenta versiones de librerías y comandos de inicialización exactos.

### Componente 1 — Android App

**Template base:** Android Studio → "Empty Activity" (Compose)

| Librería | Versión | Nota |
|----------|---------|------|
| Compose BOM | 2026.06.00 (stable) | Compose 1.11.3 |
| Room | 2.8.4 (stable) | Room 3.0 sigue en alpha — no usar en producción aún |
| WorkManager | 2.x stable | Sync en background |
| Hilt | 2.x stable | DI — estándar de facto con Compose |
| Retrofit + OkHttp | 2.x / 4.x | HTTP client REST |
| kotlinx.serialization | 1.x | JSON parsing |
| Navigation Compose | Incluido en BOM | |

**Configuración de proyecto:**
```
minSdk = 26  (Android 8.0 — NFR-5)
targetSdk = 36
buildSystem = Gradle Kotlin DSL + KSP
```

**Nota Room 3.0:** Cuando salga stable, la migración es incremental (namespace `androidx.room3`). La arquitectura no cambia.

### Componente 2 — .NET API Backend

**Template base:** `dotnet new webapi --use-controllers`

**Comando de inicialización:**
```bash
dotnet new webapi --use-controllers -n Sumitrack.Api -o src/Sumitrack.Api
```

| Componente | Versión | Nota |
|------------|---------|------|
| .NET | 10 (actual) | .NET 9 EOL mayo 2026; .NET 8 LTS expira nov 2026 |
| EF Core | 10.0.9 | Alineado con .NET 10 |
| Npgsql EF Provider | 10.x | PostgreSQL ↔ EF Core |
| ASP.NET Core | 10 (controllers) | Controllers > Minimal API para pipeline multi-tenant |
| Auth | JWT Bearer | Resolución de `tenant_id` en middleware |
| Migrations | EF Core Migrations code-first | Por ambiente |

**Por qué controllers:** la cadena de middleware para multi-tenancy (resolver `tenant_id` del JWT e inyectarlo en el DbContext de todas las queries) es más limpia y testeable con el pipeline de controllers.

### Estructura de Repositorio

```
sumitrack/
├── android/              ← proyecto Android Studio
└── backend/
    └── src/
        └── Sumitrack.Api/    ← dotnet webapi
```

Monorepo simple — dos proyectos en un solo repositorio. La inicialización de cada componente es la primera historia de implementación de su respectivo epic.

## Decisiones Arquitectónicas Centrales

### Análisis de Prioridad

**Decisiones críticas (bloquean implementación):**
- Schema-per-tenant con tabla de control central
- UUID generado en cliente — servidor como repositorio
- Estrategia de sync: push continuo / pull bajo demanda
- Detección de conflictos timestamp-based con resolución manual obligatoria

**Decisiones importantes (dan forma a la arquitectura):**
- MVVM + 4 capas en Android
- Folio local con cursor del servidor
- WorkManager para notificaciones locales
- Scalar para documentación de API

**Decisiones diferidas (post-MVP):**
- Multi-device folio y series múltiples (V2)
- Cifrado SQLite local (evaluación V2)
- FCM para notificaciones multi-dispositivo (V2)
- Sharding de tenants en múltiples servidores (según crecimiento)

---

### Datos y Multi-tenancy

**Estrategia:** Schema-per-tenant en PostgreSQL.

Una tabla de control central (`public.tenants`) mapea cada tenant a su schema:

```sql
-- Schema público (control)
CREATE TABLE public.tenants (
    id          UUID PRIMARY KEY,
    slug        VARCHAR(50) UNIQUE NOT NULL,
    schema_name VARCHAR(63) UNIQUE NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

El DbContext de EF Core resuelve el schema en cada operación a través del `ITenantContext` inyectado. Beneficio clave: permite migrar tenants a distintos servidores de PostgreSQL en el futuro sin cambiar ninguna lógica de negocio — solo la tabla de control.

**Identidad de registros:** UUID generado en el cliente con `UUID.randomUUID()` (Android) antes de persistir en SQLite. El servidor no asigna IDs — actúa como repositorio. Esto permite operación offline completa sin coordinación de secuencias.

**Campos obligatorios en todas las entidades sincronizables:**

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | UUID | Generado en cliente, PK global |
| `tenant_id` | UUID | FK a `public.tenants` |
| `created_at` | TIMESTAMPTZ | Timestamp de creación (cliente) |
| `updated_at` | TIMESTAMPTZ | Timestamp de última modificación (cliente) |
| `sync_status` | ENUM | `synced` \| `pending` \| `conflict` |

---

### Sincronización Offline

**Modelo de dos flujos independientes:**

**Pull (servidor → app)** — Bajo demanda:
- Al login inicial: descarga completa del delta desde el servidor.
- Manual: botón "Sincronizar ahora" en Configuración (S-14).
- Endpoint: `GET /api/v1/sync/pull/{entity}?since={last_sync_at}`
- La app almacena `last_sync_at` por entidad en SQLite.

**Push (app → servidor)** — Continuo en background:
- WorkManager ejecuta sync de registros con `sync_status = pending` mientras hay conexión.
- Al recuperar señal, retoma automáticamente la cola pendiente.
- Endpoint: `POST /api/v1/sync/push/{entity}`
- Todas las operaciones del usuario (crear, editar, cobrar) escriben a SQLite primero; el SyncManager toma desde ahí.

**Detección y resolución de conflictos:**
- Conflicto: `server.updated_at > client.updated_at` para el mismo `id`.
- El push verifica primero — si hay conflicto, **no se ejecuta el push**.
- El registro pasa a `sync_status = conflict` y se presenta en S-15 para resolución manual.
- Solo después de resolver el conflicto se puede hacer push del registro.
- Sin resolución automática silenciosa.

**Granularidad:** Por entidad (una llamada por tipo de entidad). Más llamadas, pero errores acotados y fáciles de diagnosticar.

---

### Folio de Venta

- En el pull inicial, la app descarga el último número de folio confirmado del servidor por tenant.
- Almacena el contador en SQLite y genera consecutivos localmente (`serie + contador++`).
- El folio se asigna al crear la Venta en SQLite — es definitivo desde el primer momento.
- Riesgo controlado en v1 (un solo dispositivo por tenant): no hay colisión posible.
- V2: análisis de series múltiples y coordinación multi-dispositivo.

---

### Notificaciones Push

**WorkManager local.** El dispositivo tiene toda la data de Parcialidades en SQLite — no se requiere servidor para calcular cuándo notificar. WorkManager programa alarmas por cada fecha de vencimiento configurada en Settings (`dias_anticipacion_recordatorio`). Cero dependencia de Firebase en v1.

---

### API y Comunicación

**Versionado:** `/api/v1/` en URL. Visible en logs, sin negociación de contenido.

**Documentación:** Scalar. Generado desde OpenAPI spec nativo de .NET 10. Visualmente superior a Swagger UI, cambio mínimo de configuración.

**Manejo de errores:** Custom error response. El servidor loguea el stack trace completo con Serilog; devuelve al consumidor una lista de errores conocidos sin detalles internos:

```json
{
  "errors": [
    { "code": "CONFLICT_DETECTED", "message": "El registro fue modificado en otro dispositivo." }
  ]
}
```

**Endpoints de sync:**
```
GET  /api/v1/sync/pull/{entity}?since={iso_timestamp}
POST /api/v1/sync/push/{entity}
```

Entidades sincronizables: `clientes`, `productos`, `ventas`, `parcialidades`, `cobros`, `settings`.

---

### Arquitectura Android

**Patrón:** MVVM + StateFlow. Estándar Compose de Google, mínimo boilerplate, adecuado para la escala del proyecto.

**Capas:**

```
UI Layer     →  Composables + ViewModels (StateFlow<UiState>)
Domain Layer →  Use Cases
                  CalcularSaldoClienteUseCase
                  ValidarFolioUseCase
                  DetectarConflictoUseCase
                  GenerarTicketUseCase
Data Layer   →  Repositories
                  ├── Room DAOs (SQLite — fuente de verdad local)
                  └── Retrofit API Services (solo para sync)
Sync Layer   →  SyncManager
                  ├── PushWorker (WorkManager — sube pending)
                  └── PullService (solo en login + manual)
```

La capa Domain aísla toda la lógica de negocio de la UI y del almacenamiento. Los ViewModels solo coordinan — no calculan.

---

### Infraestructura y Deployment

| Aspecto | Decisión |
|---------|----------|
| CI/CD | GitHub Actions básico — build + test en PR |
| Ambientes | dev + prod en Railway (dos servicios independientes) |
| Logging | Serilog → salida estructurada JSON a Railway logs |
| Migraciones | EF Core Migrations auto-apply en startup (v1) |
| Monitoring | Railway metrics built-in para v1 |

### Análisis de Impacto

**Secuencia de implementación sugerida:**
1. Backend: schema control + tenant resolver + auth JWT
2. Backend: endpoints de entidades base (Clientes, Productos)
3. Android: estructura de proyecto + Room schema + Hilt
4. Android: sync engine (SyncManager + WorkManager)
5. Android: flujos de negocio (Ventas, Cobros, Ticket)

**Dependencias cruzadas clave:**
- El SyncManager Android depende de los contratos de los endpoints de sync del backend.
- El UUID de cliente debe ser respetado por el servidor sin reasignación.
- La lógica de conflicto en Android debe coincidir con la lógica de validación en el servidor.

## Estructura de Proyecto y Límites de Componentes

### Raíz del Monorepo

```
sumitrack/
├── android/
├── backend/
├── .github/
│   └── workflows/
│       ├── android-ci.yml
│       └── backend-ci.yml
└── README.md
```

### Árbol Completo — Android

```
android/
├── .github/workflows/android-ci.yml
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── java/com/sumitrack/android/
│       │       ├── SumitrackApp.kt                     ← Application + Hilt init
│       │       ├── MainActivity.kt
│       │       ├── ui/
│       │       │   ├── navigation/
│       │       │   │   ├── NavGraph.kt
│       │       │   │   └── Routes.kt
│       │       │   ├── screens/
│       │       │   │   ├── auth/
│       │       │   │   │   ├── LoginScreen.kt          ← S-01
│       │       │   │   │   └── LoginViewModel.kt
│       │       │   │   ├── orders/
│       │       │   │   │   ├── OrderListScreen.kt      ← S-02
│       │       │   │   │   ├── OrderListViewModel.kt
│       │       │   │   │   ├── ClientSelectScreen.kt   ← S-03
│       │       │   │   │   ├── ClientSelectViewModel.kt
│       │       │   │   │   ├── ItemListScreen.kt       ← S-04
│       │       │   │   │   ├── ItemListViewModel.kt
│       │       │   │   │   ├── VariantSelectorSheet.kt ← S-05
│       │       │   │   │   ├── OrderSummaryScreen.kt   ← S-06
│       │       │   │   │   ├── OrderSummaryViewModel.kt
│       │       │   │   │   ├── PaymentScreen.kt        ← S-07
│       │       │   │   │   ├── PaymentViewModel.kt
│       │       │   │   │   ├── TicketSheet.kt          ← S-08
│       │       │   │   │   ├── OrderDetailScreen.kt    ← S-09
│       │       │   │   │   └── OrderDetailViewModel.kt
│       │       │   │   ├── agenda/
│       │       │   │   │   ├── AgendaScreen.kt         ← S-10
│       │       │   │   │   └── AgendaViewModel.kt
│       │       │   │   ├── clients/
│       │       │   │   │   ├── ClientListScreen.kt     ← S-11
│       │       │   │   │   ├── ClientListViewModel.kt
│       │       │   │   │   ├── ClientProfileScreen.kt  ← S-12
│       │       │   │   │   ├── ClientProfileViewModel.kt
│       │       │   │   │   ├── ClientFormScreen.kt     ← S-13 (alta + edición)
│       │       │   │   │   └── ClientFormViewModel.kt
│       │       │   │   ├── settings/
│       │       │   │   │   ├── SettingsScreen.kt       ← S-14
│       │       │   │   │   └── SettingsViewModel.kt
│       │       │   │   └── conflict/
│       │       │   │       ├── ConflictScreen.kt       ← S-15
│       │       │   │       └── ConflictViewModel.kt
│       │       │   ├── components/
│       │       │   │   ├── OrderCard.kt
│       │       │   │   ├── ClientCard.kt
│       │       │   │   ├── SyncIcon.kt
│       │       │   │   ├── StatusBadge.kt
│       │       │   │   ├── QuantityStepper.kt
│       │       │   │   ├── PaymentMethodRow.kt
│       │       │   │   ├── FilterChipRow.kt
│       │       │   │   └── EmptyState.kt
│       │       │   └── theme/
│       │       │       ├── Color.kt                   ← tokens DESIGN.md
│       │       │       ├── Type.kt
│       │       │       ├── Shape.kt
│       │       │       └── Theme.kt
│       │       ├── domain/
│       │       │   ├── models/
│       │       │   │   ├── Client.kt
│       │       │   │   ├── Product.kt
│       │       │   │   ├── Sale.kt
│       │       │   │   ├── Installment.kt
│       │       │   │   ├── Payment.kt
│       │       │   │   ├── SyncStatus.kt              ← enum: SYNCED|PENDING|CONFLICT
│       │       │   │   └── Conflict.kt
│       │       │   └── usecases/
│       │       │       ├── CalculateClientBalanceUseCase.kt
│       │       │       ├── ValidateFolioUseCase.kt
│       │       │       ├── DetectConflictUseCase.kt
│       │       │       ├── GenerateTicketUseCase.kt   ← PNG en memoria, FR-24/26
│       │       │       ├── ApplyCreditBalanceUseCase.kt
│       │       │       ├── CalculateInstallmentsUseCase.kt
│       │       │       └── RegisterPaymentUseCase.kt
│       │       ├── data/
│       │       │   ├── repositories/
│       │       │   │   ├── ClientRepository.kt
│       │       │   │   ├── ProductRepository.kt
│       │       │   │   ├── SaleRepository.kt
│       │       │   │   ├── InstallmentRepository.kt
│       │       │   │   ├── PaymentRepository.kt
│       │       │   │   └── SettingsRepository.kt
│       │       │   ├── local/
│       │       │   │   ├── SumitrackDatabase.kt       ← @Database Room
│       │       │   │   ├── converters/
│       │       │   │   │   ├── BigDecimalConverter.kt
│       │       │   │   │   └── InstantConverter.kt
│       │       │   │   ├── dao/
│       │       │   │   │   ├── ClientDao.kt
│       │       │   │   │   ├── ProductDao.kt
│       │       │   │   │   ├── SaleDao.kt
│       │       │   │   │   ├── InstallmentDao.kt
│       │       │   │   │   ├── PaymentDao.kt
│       │       │   │   │   └── SettingsDao.kt
│       │       │   │   └── entities/
│       │       │   │       ├── ClientEntity.kt
│       │       │   │       ├── ProductEntity.kt
│       │       │   │       ├── SaleEntity.kt
│       │       │   │       ├── InstallmentEntity.kt
│       │       │   │       ├── PaymentEntity.kt
│       │       │   │       └── SettingsEntity.kt
│       │       │   └── remote/
│       │       │       ├── api/
│       │       │       │   ├── AuthApiService.kt
│       │       │       │   └── SyncApiService.kt
│       │       │       └── dto/
│       │       │           ├── ClientDto.kt
│       │       │           ├── ProductDto.kt
│       │       │           ├── SaleDto.kt
│       │       │           ├── InstallmentDto.kt
│       │       │           ├── PaymentDto.kt
│       │       │           └── SettingsDto.kt
│       │       ├── sync/
│       │       │   ├── SyncManager.kt
│       │       │   └── workers/
│       │       │       └── PushWorker.kt
│       │       └── di/
│       │           ├── DatabaseModule.kt
│       │           ├── NetworkModule.kt
│       │           ├── SyncModule.kt
│       │           └── UseCaseModule.kt
│       ├── test/java/com/sumitrack/android/
│       │   ├── domain/usecases/
│       │   └── data/repositories/
│       └── androidTest/java/com/sumitrack/android/
│           └── data/local/dao/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── local.properties.example
```

### Árbol Completo — Backend .NET

```
backend/
├── src/
│   └── Sumitrack.Api/
│       ├── Sumitrack.Api.csproj
│       ├── Program.cs                              ← startup, DI, Serilog, EF migrations auto
│       ├── appsettings.json
│       ├── appsettings.Development.json
│       ├── appsettings.Production.json
│       ├── Controllers/
│       │   ├── AuthController.cs                   ← FR-1..4
│       │   ├── ClientsController.cs                ← FR-5..9
│       │   ├── ProductsController.cs               ← FR-10..11
│       │   ├── SalesController.cs                  ← FR-12..17
│       │   ├── PaymentsController.cs               ← FR-18..20
│       │   ├── InstallmentsController.cs           ← FR-14, FR-19
│       │   ├── SettingsController.cs               ← FR-27..31
│       │   └── SyncController.cs                   ← FR-32..34
│       ├── Services/
│       │   ├── Auth/
│       │   │   └── AuthService.cs
│       │   ├── Clients/
│       │   │   └── ClientService.cs
│       │   ├── Sales/
│       │   │   ├── SaleService.cs
│       │   │   └── FolioService.cs                 ← último folio por tenant para pull inicial
│       │   ├── Payments/
│       │   │   └── PaymentService.cs
│       │   ├── Sync/
│       │   │   └── SyncService.cs
│       │   └── Conflicts/
│       │       └── ConflictService.cs              ← detección timestamp FR-34
│       ├── Repositories/
│       │   ├── ClientRepository.cs
│       │   ├── ProductRepository.cs
│       │   ├── SaleRepository.cs
│       │   ├── InstallmentRepository.cs
│       │   ├── PaymentRepository.cs
│       │   └── SettingsRepository.cs
│       ├── Models/
│       │   ├── Entities/
│       │   │   ├── Tenant.cs                       ← public.tenants
│       │   │   ├── User.cs
│       │   │   ├── Client.cs
│       │   │   ├── Product.cs
│       │   │   ├── Sale.cs
│       │   │   ├── Installment.cs
│       │   │   ├── Payment.cs
│       │   │   └── Settings.cs
│       │   ├── DTOs/
│       │   │   ├── ClientDto.cs
│       │   │   ├── ProductDto.cs
│       │   │   ├── SaleDto.cs
│       │   │   ├── InstallmentDto.cs
│       │   │   ├── PaymentDto.cs
│       │   │   └── SettingsDto.cs
│       │   ├── Requests/
│       │   │   ├── LoginRequest.cs
│       │   │   ├── CreateClientRequest.cs
│       │   │   ├── CreateSaleRequest.cs
│       │   │   ├── CreatePaymentRequest.cs
│       │   │   └── PushSyncRequest.cs
│       │   └── Responses/
│       │       ├── LoginResponse.cs
│       │       ├── ErrorResponse.cs
│       │       └── PullSyncResponse.cs
│       └── Infrastructure/
│           ├── Data/
│           │   ├── AppDbContext.cs
│           │   ├── TenantSchemaResolver.cs         ← SET search_path TO tenant_{id}
│           │   └── Migrations/
│           ├── Auth/
│           │   ├── JwtMiddleware.cs
│           │   ├── TenantResolver.cs
│           │   └── ITenantContext.cs
│           ├── Sync/
│           │   └── DeltaSyncHelper.cs
│           ├── Logging/
│           │   └── SerilogConfiguration.cs
│           └── Extensions/
│               ├── ServiceCollectionExtensions.cs
│               └── ApplicationBuilderExtensions.cs
├── tests/
│   └── Sumitrack.Api.Tests/
│       ├── Sumitrack.Api.Tests.csproj
│       ├── Services/
│       │   ├── SaleServiceTests.cs
│       │   ├── FolioServiceTests.cs
│       │   └── ConflictServiceTests.cs
│       └── Controllers/
│           └── SalesControllerTests.cs
└── Sumitrack.sln
```

### Mapeo FR → Estructura

| FR Category | Android | Backend |
|-------------|---------|---------|
| FR-1..4 Auth | `screens/auth/`, `remote/api/AuthApiService` | `AuthController`, `Services/Auth/`, `Infrastructure/Auth/` |
| FR-5..9 Clientes | `screens/clients/`, `dao/ClientDao`, `usecases/CalculateClientBalanceUseCase` | `ClientsController`, `Services/Clients/` |
| FR-10..11 Catálogo | `screens/orders/ItemListScreen`, `dao/ProductDao` | `ProductsController` |
| FR-12..17 Ventas | `screens/orders/` (S-03→S-09), `usecases/{varios}`, `dao/SaleDao` | `SalesController`, `Services/Sales/`, `FolioService` |
| FR-18..20 Cobros | `screens/orders/PaymentScreen`, `usecases/RegisterPaymentUseCase` | `PaymentsController`, `Services/Payments/` |
| FR-21..23 Agenda | `screens/agenda/`, `sync/workers/PushWorker` (WorkManager notif.) | Sin endpoint propio — datos de installments |
| FR-24..26 Ticket | `usecases/GenerateTicketUseCase` (PNG en memoria) | No requiere backend |
| FR-27..31 Settings | `screens/settings/`, `dao/SettingsDao` | `SettingsController` |
| FR-32..34 Sync | `sync/SyncManager`, `sync/workers/PushWorker`, `remote/api/SyncApiService` | `SyncController`, `Services/Sync/`, `Services/Conflicts/` |

### Límites de Integración

**App → API** (únicamente auth y sync):
```
AuthApiService  →  POST /api/v1/auth/login
SyncApiService  →  GET  /api/v1/sync/pull/{entity}?since={iso_timestamp}
                   POST /api/v1/sync/push/{entity}
```
Todas las lecturas y operaciones de negocio van a SQLite local — nunca a la red en el camino crítico.

**API → PostgreSQL:**
`TenantResolver` extrae `tenant_id` del JWT → `TenantSchemaResolver` ejecuta `SET search_path TO tenant_{id}` → EF Core opera en el schema correcto sin cambios en queries ni en repositorios.

**Flujo de datos:**
```
Usuario → Composable → ViewModel → UseCase → Repository → Room DAO → SQLite
                                                                ↓ (background)
                                                         SyncManager → PushWorker
                                                                ↓
                                                         SyncApiService → POST /sync/push
                                                                ↓
                                                         SyncController → SyncService → PostgreSQL
```

### Correcciones Post-Validación

Las siguientes entidades se agregaron a la estructura tras la validación de cobertura de requisitos:

**Android — adiciones:**
```
data/local/entities/ProductVariantEntity.kt    ← variantes para S-05 (FR-10)
data/local/dao/ProductVariantDao.kt
data/local/entities/CreditBalanceEntity.kt     ← Crédito a Favor por cliente (FR-18b)
data/local/dao/CreditBalanceDao.kt
data/local/entities/ConflictLogEntity.kt       ← log de auditoría FR-34
data/local/dao/ConflictLogDao.kt
```

**Backend — adiciones:**
```
Models/Entities/ProductVariant.cs
Models/Entities/CreditBalance.cs
Models/Entities/ConflictLog.cs
Controllers/ProductVariantsController.cs
```

**Nota de modelado — CreditBalance:**
Entidad separada (no campo en Client) para preservar historial de movimientos. Campos mínimos: `id`, `fk_client`, `fk_tenant`, `amount NUMERIC(18,6)`, `origin` (enum: `CANCELLATION`|`MANUAL`), `fk_origin_sale`, `applied_at`, `created_at`, `updated_at`, `sync_status`.

## Resultados de Validación de Arquitectura

### Validación de Coherencia ✅

**Compatibilidad de decisiones:** Sin conflictos detectados.
- Schema-per-tenant + EF Core: compatible vía `SET search_path` en Npgsql.
- UUID generado en cliente + SQLite-first: coherente — el servidor nunca asigna IDs.
- Sync timestamp-based + resolución manual S-15: alineación exacta con PRD FR-34.
- WorkManager + offline-first: coherente — WorkManager persiste la cola aunque la app se cierre.
- `BigDecimal` Android + `NUMERIC(18,6)` PostgreSQL: compatibles vía Npgsql decimal mapping.

**Consistencia de patrones:** Los 7 patrones de naming son consistentes entre ambos componentes. La estructura por capas es coherente en Android y .NET.

**Alineación de estructura:** Todas las decisiones arquitectónicas tienen soporte en la estructura de carpetas definida. Los límites entre capas están claramente establecidos.

### Cobertura de Requisitos ✅

| FR Category | Cobertura | Componentes |
|-------------|-----------|-------------|
| FR-1..4 Auth | ✅ | AuthController, JwtMiddleware, TenantResolver, LoginScreen |
| FR-5..9 Clientes | ✅ | ClientsController, CalculateClientBalanceUseCase, ClientProfileScreen |
| FR-10..11 Catálogo | ✅ | ProductsController, ProductVariantsController, ProductVariantEntity |
| FR-12..17 Ventas | ✅ | SalesController, FolioService, OrderList→Payment screens |
| FR-18..20 + 18b | ✅ | PaymentsController, ApplyCreditBalanceUseCase, CreditBalanceEntity |
| FR-21..23 Agenda | ✅ | WorkManager PushWorker, AgendaScreen, AgendaViewModel |
| FR-24..26 Ticket | ✅ | GenerateTicketUseCase (PNG en memoria), TicketSheet |
| FR-27..31 Settings | ✅ | SettingsController, SettingsDao, SettingsScreen |
| FR-32..34 Sync | ✅ | SyncManager, PushWorker, SyncController, ConflictService, ConflictLogEntity |

| NFR | Cobertura |
|-----|-----------|
| NFR-1 Performance <2min/<10sec | ✅ SQLite-first, lecturas siempre locales |
| NFR-2 100% offline / 99.5% API | ✅ Push background, pull bajo demanda |
| NFR-3 Seguridad | ✅ JWT + HTTPS + bcrypt; cifrado SQLite diferido a V2 documentado |
| NFR-4 Multi-tenant sin cambios de código | ✅ Schema-per-tenant + TenantResolver dinámico |
| NFR-5 Android API 26+ | ✅ minSdk=26 |
| NFR-6 Extensibilidad CFDI | ✅ Diferido a V2; campos reservados en stories de entidades |

### Validación de Preparación para Implementación ✅

**Completitud de decisiones:** Todas las decisiones críticas documentadas con versiones verificadas. Stack completo especificado para ambos componentes.

**Completitud de estructura:** Estructura de carpetas completa con 3 entidades adicionales post-validación. Todos los archivos clave nombrados. Mapping FR → estructura documentado.

**Completitud de patrones:** 7 reglas obligatorias. Naming, estructura, formato, proceso y proceso de datos cubiertos. Ejemplos de código incluidos para TypeConverters, EF Core precision y UiState.

### Checklist de Completitud de Arquitectura

**Análisis de Requisitos**
- [x] Contexto del proyecto analizado a fondo
- [x] Escala y complejidad evaluadas (nivel Alto)
- [x] Restricciones técnicas identificadas
- [x] Concerns transversales mapeados (7 identificados)

**Decisiones Arquitectónicas**
- [x] Decisiones críticas documentadas con versiones
- [x] Stack tecnológico completamente especificado
- [x] Patrones de integración definidos
- [x] Consideraciones de rendimiento abordadas (SQLite-first)

**Patrones de Implementación**
- [x] Convenciones de naming establecidas
- [x] Patrones de estructura definidos
- [x] Patrones de comunicación especificados
- [x] Patrones de proceso documentados (UiState, retry, validación, logs)

**Estructura de Proyecto**
- [x] Estructura de directorios completa definida (ambos componentes)
- [x] Límites de componentes establecidos
- [x] Puntos de integración mapeados
- [x] Mapping requisitos → estructura completo

### Evaluación de Preparación

**Estado general:** LISTO PARA IMPLEMENTACIÓN

**Nivel de confianza:** Alto — todos los 16 ítems verificados, sin gaps críticos abiertos.

**Fortalezas clave:**
- Offline-first con SQLite como fuente de verdad — elimina la dependencia de red del camino crítico
- Schema-per-tenant preparado para sharding futuro sin reescritura
- UUID generado en cliente — máxima autonomía offline sin coordinación de secuencias
- Sync asimétrico (push continuo / pull bajo demanda) — balanceo correcto entre frescura de datos y consumo de batería
- Precisión monetaria con `NUMERIC(18,6)` / `BigDecimal` — sin pérdidas por redondeo en cálculos de saldo

**Áreas para mejora futura (V2):**
- Cifrado de SQLite local
- FCM para notificaciones multi-dispositivo
- Refresh automático de token JWT
- Series de folio múltiples y coordinación multi-dispositivo
- Integración CFDI/SAT

### Handoff a Implementación

**Prioridad de implementación:**

1. Backend: schema control + TenantResolver + auth JWT + EF Core migrations
2. Backend: endpoints de entidades base (clients, products, product_variants)
3. Android: estructura de proyecto + Room schema + Hilt + tema visual (DESIGN.md tokens)
4. Android + Backend: contrato de sync (endpoints pull/push) + SyncManager + PushWorker
5. Android: flujos de negocio en orden (ventas → cobros → ticket → agenda → conflictos)

**Para todos los agentes de IA:**
- Este documento es la fuente de verdad arquitectónica — consultar ante cualquier decisión de implementación
- Seguir los patrones de naming exactamente como documentados (snake_case inglés en DB, camelCase en JSON)
- Toda operación escribe en SQLite primero — nunca esperar confirmación de red
- UUID siempre generado en Android antes de persistir
- `BigDecimal` para todo cálculo monetario — nunca `Double` ni `Float`

## Patrones de Implementación y Reglas de Consistencia

### Naming — Base de Datos (PostgreSQL)

**Nomenclatura de base de datos:** `sumitrack_01`. Futuras bases de datos siguen la secuencia `sumitrack_02`, `sumitrack_03` para sharding de tenants por volumen o requerimiento.

| Elemento | Convención | Ejemplo |
|----------|-----------|---------|
| Schema control | `public` | `public.tenants` |
| Schema tenant | `tenant_{uuid_corto}` | `tenant_a3f9` |
| Tablas | `snake_case`, inglés, plural | `sales`, `clients`, `payment_installments` |
| Columnas | `snake_case`, inglés | `updated_at`, `sync_status`, `total_amount` |
| PK | `id UUID PRIMARY KEY` | `id` |
| FK | `fk_{entidad}` | `fk_client`, `fk_sale`, `fk_tenant` |
| Índices | `idx_{tabla}_{columna}` | `idx_sales_fk_client` |
| Funciones | `snake_case`, inglés | `get_tenant_schema()` |

**Mapeo dominio español → identificadores de código:**

| Dominio (PRD) | Código / DB |
|---------------|------------|
| Venta | `sale` |
| Cobro | `payment` |
| Parcialidad | `installment` |
| Cliente | `client` |
| Producto | `product` |
| Folio | `folio` (término fiscal — se mantiene) |
| Crédito a Favor | `credit_balance` |
| Conflicto | `conflict` |

---

### Naming — API Endpoints

Inglés, sustantivos plurales, minúsculas, kebab-case para multi-palabra:

```
GET    /api/v1/clients
POST   /api/v1/clients
GET    /api/v1/clients/{id}
PUT    /api/v1/clients/{id}
GET    /api/v1/sales
POST   /api/v1/sales
GET    /api/v1/sales/{id}
PATCH  /api/v1/sales/{id}/cancel
POST   /api/v1/payments
GET    /api/v1/payment-installments
GET    /api/v1/products
GET    /api/v1/settings
GET    /api/v1/sync/pull/{entity}?since={iso_timestamp}
POST   /api/v1/sync/push/{entity}
```

---

### Formato — JSON

- **Campos:** `camelCase` (serialización por defecto .NET).
- **Montos:** `decimal` con 6 decimales en transmisión y almacenamiento. Ejemplo: `"totalAmount": 1250.000000`.
- **Fechas:** ISO 8601 UTC en API y SQLite. Ejemplo: `"updatedAt": "2026-06-27T16:30:00Z"`.
- **UUID:** minúsculas con guiones.
- **Respuesta directa** — sin wrapper:

```json
// ✅ Objeto directo
{ "id": "uuid", "name": "Ferretería El Clavo", "balance": 4200.000000 }

// ✅ Lista directa
[ { "id": "uuid" }, { "id": "uuid" } ]

// ❌ Wrapper prohibido
{ "data": { ... }, "meta": { ... } }
```

---

### Montos Monetarios — Precisión en Todas las Capas

| Capa | Tipo / Precisión |
|------|-----------------|
| PostgreSQL | `NUMERIC(18, 6)` |
| SQLite (Android) | `TEXT` via `BigDecimal` + `TypeConverter` |
| .NET / EF Core | `decimal` con `[Precision(18, 6)]` |
| JSON | 6 decimales: `1250.000000` |
| Cálculos Android | `BigDecimal` — nunca `Double` ni `Float` |
| Visualización UI | Formateado a 2 decimales: `$1,250.00` |

```kotlin
// Android — TypeConverter Room
class BigDecimalConverter {
    @TypeConverter fun fromBigDecimal(v: BigDecimal?): String? = v?.toPlainString()
    @TypeConverter fun toBigDecimal(v: String?): BigDecimal? = v?.let { BigDecimal(it) }
}
```

```csharp
// .NET — EF Core precision
modelBuilder.Entity<Sale>()
    .Property(s => s.TotalAmount)
    .HasPrecision(18, 6);
```

---

### Estructura — Android (por capa)

```
com.sumitrack.android/
  ui/
    screens/
      auth/           S-01
      orders/         S-02..S-09
      agenda/         S-10
      clients/        S-11..S-13
      settings/       S-14
      conflict/       S-15
    components/       Composables reutilizables: OrderCard, SyncIcon, StatusBadge...
    theme/            MaterialTheme, tokens de DESIGN.md
  domain/
    usecases/         CalculateClientBalanceUseCase, ValidateFolioUseCase,
                      DetectConflictUseCase, GenerateTicketUseCase...
    models/           Modelos de dominio puros (sin Room ni Retrofit)
  data/
    repositories/     ClientRepository, SaleRepository, SyncRepository...
    local/
      dao/            ClientDao, SaleDao, PaymentDao...
      entities/       Room entities — mapean 1:1 con tablas SQLite
    remote/
      api/            Retrofit interfaces: SyncApiService...
      dto/            DTOs de red — distintos de entidades Room
  sync/
    workers/          PushWorker (WorkManager)
    manager/          SyncManager — orquesta pull/push
  di/                 Módulos Hilt: DatabaseModule, NetworkModule, SyncModule...
```

---

### Estructura — .NET API (por capa)

```
Sumitrack.Api/
  Controllers/        SalesController, ClientsController, SyncController...
  Services/           SaleService, ConflictService, FolioService...
  Repositories/       ClientRepository, SaleRepository...
  Models/
    Entities/         EF Core entities — mapean 1:1 con tablas PostgreSQL
    DTOs/             ClientDto, SaleDto, InstallmentDto...
    Requests/         CreateSaleRequest, PushSyncRequest...
    Responses/        Solo cuando difieren del DTO
  Infrastructure/
    Data/             AppDbContext, migraciones EF Core
    Auth/             JWT middleware, TenantResolver
    Sync/             SyncService — lógica pull/push del servidor
    Logging/          Configuración Serilog
  Extensions/         Registro de servicios, pipeline de middleware
```

---

### Patrones de Proceso

**Android — UiState:**
```kotlin
sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
```
Cada ViewModel expone `StateFlow<UiState<T>>`. Composables observan con `collectAsState()`.

**Android — fechas para display:**
```kotlin
fun Instant.toLocalDisplay(): String =
    atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
```

**Retry de sync:** `ExponentialBackoffPolicy` en `PushWorker`. El Worker deja `sync_status = pending` al fallar; WorkManager reintenta automáticamente.

**Validación:** siempre en Use Cases (Android) y Services (.NET). Nunca en ViewModels, Controllers, DAOs ni Composables.

**Logging .NET:** `ILogger<T>` como interfaz, Serilog como implementación. `Information` en prod, `Debug` en dev. Stack trace completo en log; lista de errores conocidos al cliente.

---

### Reglas Obligatorias — Todos los Agentes

1. **Toda FK se nombra `fk_{entidad}`** — nunca `{entidad}_id`.
2. **Toda entidad sincronizable lleva** `id`, `fk_tenant`, `created_at`, `updated_at`, `sync_status`.
3. **Toda operación de usuario escribe primero en SQLite** — nunca espera confirmación de red.
4. **UUID generado en Android** — el servidor no reasigna IDs.
5. **Fechas siempre UTC** en almacenamiento y API; conversión a zona local solo en capa UI.
6. **Montos con `NUMERIC(18,6)` / `BigDecimal`** en almacenamiento y transmisión; 2 decimales solo para display.
7. **Sin lógica de negocio en ViewModels ni Controllers** — Use Cases y Services respectivamente.
