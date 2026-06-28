---
stepsCompleted: [1, 2, 3, 4]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-sumitrack-2026-06-22/prd.md
  - _bmad-output/planning-artifacts/architecture/architecture.md
  - _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/DESIGN.md
  - _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md
---

# sumitrack - Epic Breakdown

## Overview

Este documento proporciona el desglose completo de épicas e historias para Sumitrack, descomponiendo los requisitos del PRD, UX Design y Arquitectura en historias implementables.

## Requirements Inventory

### Functional Requirements

FR-1: El Usuario puede autenticarse con nombre de usuario y contraseña validados contra la nube. Con credenciales válidas y conexión: sesión activa, Settings descargados. Con credenciales incorrectas: mensaje de error claro. Sin conexión con sesión cacheada previa: operación offline habilitada. Sin conexión sin sesión: login falla con mensaje explicativo.

FR-2: La sesión activa persiste en el dispositivo entre aperturas de la app. El Usuario no requiere reingresar credenciales salvo cierre de sesión manual.

FR-3: El Usuario puede cerrar sesión manualmente. Al hacerlo, los datos de sesión locales se borran y el token se invalida.

FR-4: Al autenticarse exitosamente, la app descarga los Settings del Tenant desde la nube y los almacena localmente para uso offline.

FR-5: El Usuario puede registrar un nuevo Cliente con: nombre o razón social, teléfono de contacto, dirección (opcional), RFC (opcional), notas libres. La operación se guarda localmente y se sincroniza al reconectar.

FR-6: El Usuario puede consultar la lista de Clientes con búsqueda por nombre en tiempo real. Cada entrada muestra el Saldo actual del Cliente. La consulta responde en menos de 10 segundos desde datos locales.

FR-7: El perfil de un Cliente muestra: datos de identificación, Saldo total, lista de Ventas abiertas (Pendiente, Parcial) y acceso al historial completo.

FR-8: El Usuario puede editar los datos de identificación y notas de un Cliente en cualquier momento.

FR-9: El Usuario puede agregar o editar notas libres por Cliente para registrar acuerdos, observaciones o alertas informales.

FR-10: El Usuario puede registrar y editar Productos con: nombre, precio unitario, impuesto aplicable (porcentaje configurable, ej. IVA 16%). Incluye soporte de variantes de producto para selección en flujo de nueva venta (S-05).

FR-11: El Usuario puede desactivar un Producto para que no aparezca en nuevas Ventas sin eliminarlo. Los Productos desactivados siguen visibles en el historial de Ventas anteriores.

FR-12: El Usuario puede crear una Venta seleccionando: Cliente, uno o más Productos con cantidades, y condición de pago (pago único o Parcialidades). La Venta se guarda localmente al confirmar independientemente de la conectividad. El Saldo del Cliente se actualiza de forma inmediata. El tiempo de registro de una Venta estándar (Cliente conocido, 1-3 Productos) no debe superar 2 minutos.

FR-13: El Usuario puede definir una Venta con pago en una sola exhibición, con fecha de pago acordada.

FR-14: El Usuario puede definir una Venta con N Parcialidades (1 ≤ N ≤ límite configurado en Settings, máximo 15), asignando a cada Parcialidad un monto y una fecha. La suma de los montos de las Parcialidades debe ser igual al total de la Venta antes de confirmar.

FR-15: Cada Venta tiene un Estatus actualizado automáticamente: `Pendiente` (sin pagos), `Parcial` (al menos un Cobro, saldo abierto), `Liquidado` (todos los montos recibidos), `Cancelado` (anulada).

FR-16: El Usuario puede cancelar una Venta registrada independientemente de su Estatus. La cancelación requiere confirmación explícita. Si la Venta tiene Cobros (Estatus Parcial): el sistema ofrece Opción A (cancelar parcialidades, acuerdo manual) o Opción B (generar Crédito a Favor al Cliente). Los Cobros previamente registrados quedan en historial para auditoría.

FR-17: El Usuario puede consultar Ventas filtradas por Estatus y por Cliente, con vista global y vista por Cliente.

FR-18: El Usuario puede registrar el pago recibido sobre una Venta de pago único. Al registrar el monto total, la Venta pasa a `Liquidado`.

FR-18b: El Usuario puede aplicar el Crédito a Favor de un Cliente como pago parcial o total al registrar una nueva Venta o un Cobro. El sistema muestra el Crédito disponible cuando el Cliente tiene saldo a favor. El Crédito se descuenta del monto aplicado; si cubre el total, la Venta pasa a `Liquidado`.

FR-19: El Usuario puede marcar una Parcialidad como pagada. El sistema recalcula el Estatus de la Venta: `Parcial` si quedan Parcialidades pendientes, `Liquidado` si todas están cubiertas.

FR-20: El Usuario puede consultar el historial completo de Cobros por Venta y por Cliente, con fecha y monto de cada pago.

FR-21: El sistema envía notificaciones push automáticas al dispositivo para alertar al Usuario de Ventas o Parcialidades cuya fecha de pago se aproxima. La notificación incluye: nombre del Cliente, monto pendiente, fecha de vencimiento. Los días de anticipación se toman de Settings. Ventas en `Liquidado` o `Cancelado` no generan recordatorios.

FR-22: Los días de anticipación para recordatorios son configurables en Settings (ver FR-29). El valor se aplica globalmente a todos los recordatorios del Tenant.

FR-23: El Usuario puede consultar una vista de calendario con todos los pagos programados (Parcialidades pendientes y pagos únicos futuros), distinguiendo los próximos a vencer según el valor configurado en Settings.

FR-24: Al confirmar una Venta, el sistema genera un Ticket que incluye: datos fiscales del Tenant, datos del Cliente, listado de Productos con cantidades y precios, subtotal, impuestos, total, condición de pago (fechas de Parcialidades si aplica) y el Folio de la Venta. El Folio se asigna automáticamente con formato `{serie_folio}{número}` (ej. A1, A2); el número es auto-incremental por Tenant y nunca se reutiliza.

FR-25: El Usuario puede imprimir el Ticket en una impresora térmica compatible vía Bluetooth. Si la impresora no está disponible, el sistema ofrece alternativas sin bloquear la Venta ya guardada.

FR-26: El Usuario puede compartir el Ticket como imagen (PNG generado en memoria) utilizando el intent nativo de compartir de Android. La imagen se genera en memoria, no se guarda en el almacenamiento del dispositivo. La opción está disponible inmediatamente después de confirmar la Venta y desde el detalle de cualquier Venta existente.

FR-27: Setting `max_parcialidades` (entero, rango 1–15, default 15). Define el número máximo de Parcialidades permitidas al crear una Venta.

FR-28: Setting `datos_fiscales`: nombre o razón social, RFC, dirección fiscal, teléfono. Estos datos aparecen en todos los Tickets generados.

FR-29: Setting `dias_anticipacion_recordatorio` (entero, rango 1–30). Define cuántos días antes del vencimiento de un pago se envía la notificación push.

FR-30: Setting `serie_folio` (texto, máximo 5 caracteres, default `"A"`). Define el prefijo de los Folios de Venta del Tenant.

FR-31: El Usuario puede modificar cualquier Setting directamente desde la app. Los cambios se guardan localmente de inmediato y se sincronizan con la nube al reconectar.

FR-32: Todas las operaciones de la app (registro de Ventas, Cobros, consultas, edición de Clientes, modificación de Settings) funcionan sin conexión a internet usando los datos locales en SQLite. La pérdida de conexión no interrumpe ningún flujo activo. El indicador de estado de conexión y sync es visible en la app en todo momento.

FR-33: Al detectar conexión a internet disponible, la app inicia automáticamente la sincronización de cambios locales pendientes con la nube, en segundo plano. La sincronización no bloquea la operación del Usuario. El Usuario recibe indicador visual al completar la sincronización.

FR-34: Si durante Sync se detecta que un mismo registro fue modificado localmente y en la nube de forma independiente, el sistema presenta un Conflicto al Usuario. El sistema muestra ambas versiones en pantalla. El Usuario elige: Reemplazar (versión local) o Conservar ambos (duplicado marcado para revisión). No hay resolución automática silenciosa. El log de Conflictos resueltos queda disponible para auditoría.

### NonFunctional Requirements

NFR-1: Rendimiento — El tiempo de registro de una Venta estándar (Cliente conocido, 1-3 Productos) no debe superar 2 minutos end-to-end. La consulta del Saldo de un Cliente desde datos locales debe responder en menos de 10 segundos. La Sync en background no debe degradar visiblemente la respuesta de la UI.

NFR-2: Disponibilidad — La app opera al 100% offline. La disponibilidad de la API en la nube es un objetivo de 99.5% mensual para operaciones que la requieren (login, sync, descarga de Settings).

NFR-3: Seguridad — Las contraseñas se almacenan con hash seguro (bcrypt o Argon2), nunca en texto plano. Toda comunicación app↔API viaja cifrada por HTTPS/TLS. El token de sesión se invalida en cierre de sesión manual. Los datos locales en SQLite no están cifrados en v1 (cifrado evaluado en V2).

NFR-4: Multi-tenant e Aislamiento — Cada Tenant tiene sus datos completamente aislados; imposible acceder a datos de otro Tenant. La arquitectura soporta onboarding de nuevos Tenants sin cambios de código ni redeployment.

NFR-5: Compatibilidad Android — La app es compatible con Android 8.0 (API 26) y versiones superiores.

NFR-6: Extensibilidad fiscal — El modelo de datos de Ventas y Productos soporta la incorporación futura de integración CFDI/SAT sin refactorización mayor del esquema de base de datos.

### Additional Requirements

- AR-1: Monorepo con dos proyectos: `android/` (Android Studio, "Empty Activity Compose") y `backend/src/Sumitrack.Api/` (`dotnet new webapi --use-controllers`). Sin template de terceros.
- AR-2: Multi-tenancy schema-per-tenant en PostgreSQL. Tabla control `public.tenants` (id UUID, slug, schema_name, created_at). `TenantSchemaResolver` ejecuta `SET search_path TO tenant_{id}` en cada operación de EF Core. Permite migrar tenants a distintos servidores en futuro sin cambios de lógica.
- AR-3: Stack Android — Compose BOM 2026.06.00 (Compose 1.11.3), Room 2.8.4 stable, WorkManager 2.x, Hilt 2.x, Retrofit + OkHttp 2.x/4.x, kotlinx.serialization 1.x, Navigation Compose; minSdk=26, targetSdk=36, Gradle Kotlin DSL + KSP. No usar Room 3.0 (alpha).
- AR-4: Stack Backend — .NET 10, EF Core 10.0.9, Npgsql 10.x, ASP.NET Core controllers, JWT Bearer, Serilog; migraciones auto-apply en startup (v1). Base de datos: `sumitrack_01`.
- AR-5: UUID generado en cliente Android (`UUID.randomUUID()`) antes de persistir en SQLite. El servidor no asigna IDs — actúa como repositorio.
- AR-6: Toda entidad sincronizable lleva: `id` (UUID, PK global), `fk_tenant` (UUID), `created_at` (TIMESTAMPTZ UTC), `updated_at` (TIMESTAMPTZ UTC), `sync_status` (ENUM: `synced`|`pending`|`conflict`).
- AR-7: Push continuo en background con WorkManager (PushWorker): sube registros con `sync_status = pending` mientras hay conexión; al reconectar retoma cola. Pull bajo demanda: solo en login inicial y botón "Sincronizar ahora" en S-14.
- AR-8: Endpoints de sync — `GET /api/v1/sync/pull/{entity}?since={iso_timestamp}` y `POST /api/v1/sync/push/{entity}`. Entidades: `clientes`, `productos`, `ventas`, `parcialidades`, `cobros`, `settings`. Una llamada por tipo de entidad.
- AR-9: Detección de conflicto: `server.updated_at > client.updated_at` para mismo `id`. Push no se ejecuta si hay conflicto; registro pasa a `sync_status = conflict` y se presenta en S-15. Sin resolución automática silenciosa.
- AR-10: Folio — pull inicial descarga último folio confirmado del servidor por tenant; app almacena contador en SQLite y genera consecutivos localmente (`serie + contador++`); folio definitivo desde creación en SQLite.
- AR-11: Notificaciones push con WorkManager local (sin FCM en v1). WorkManager programa alarmas basadas en fechas de Parcialidades almacenadas en SQLite local.
- AR-12: Documentación API con Scalar (no Swagger). Generado desde OpenAPI spec nativo de .NET 10.
- AR-13: Manejo de errores API — `CustomError` response: lista de errores conocidos al consumidor sin detalles internos; stack trace completo solo en Serilog. Formato: `{ "errors": [{ "code": "...", "message": "..." }] }`.
- AR-14: MVVM + 4 capas Android: UI (Composables + ViewModels con StateFlow<UiState>) → Domain (Use Cases) → Data (Repositories + Room DAOs + Retrofit API) → Sync (SyncManager + PushWorker). Sin lógica de negocio en ViewModels ni Controllers.
- AR-15: CI/CD — GitHub Actions básico (build + test en PR). Dos ambientes: dev + prod en Railway (dos servicios independientes). Serilog → JSON estructurado a Railway logs.
- AR-16: Convenciones de naming — DB: `sumitrack_01`, tablas snake_case inglés plural, columnas snake_case inglés, FK `fk_{entidad}`, índices `idx_{tabla}_{columna}`; API endpoints: inglés sustantivos plurales kebab-case; JSON: camelCase, montos 6 decimales, fechas ISO 8601 UTC; respuesta directa (sin wrapper).
- AR-17: Precisión monetaria — `NUMERIC(18,6)` en PostgreSQL; `BigDecimal` + TypeConverter TEXT en SQLite Room; `decimal [Precision(18,6)]` en EF Core; nunca `Double` ni `Float`; display formateado a 2 decimales.
- AR-18: Entidades adicionales post-validación: `ProductVariantEntity` / `ProductVariant.cs` (variantes FR-10/S-05); `CreditBalanceEntity` / `CreditBalance.cs` (Crédito a Favor FR-18b — entidad separada con historial de movimientos, campos: id, fk_client, fk_tenant, amount NUMERIC(18,6), origin, fk_origin_sale, applied_at, created_at, updated_at, sync_status); `ConflictLogEntity` / `ConflictLog.cs` (auditoría FR-34).
- AR-19: Retry de sync — `ExponentialBackoffPolicy` en PushWorker; al fallar, `sync_status` permanece `pending` y WorkManager reintenta automáticamente.
- AR-20: Validación — siempre en Use Cases (Android) y Services (.NET). Nunca en ViewModels, Controllers, DAOs ni Composables.

### UX Design Requirements

UX-DR1: Implementar sistema de tokens de color en `ui/theme/Color.kt` — primary #1A237E, primary-variant #3949AB, on-primary #FFFFFF, background #F0F0F5, surface #FFFFFF, on-surface #1A1A2E, on-surface-variant #6B6B80, outline #E8E8EE, error #B00020; colores de estado: status-paid #2E7D32, status-pending #F57F17, status-overdue #AD1457, status-cancelled #9E9E9E; sync: sync-ok #00BCD4 (exclusivo, solo para sync), sync-pending #FF7043 (solo para íconos, no texto). Todos los pares críticos cumplen WCAG AA.

UX-DR2: Implementar escala tipográfica Roboto en `ui/theme/Type.kt` — display-large (28sp/700), title-large (22sp/700), title-medium (18sp/700), body-large (15sp/600), body-medium (14sp/400), body-small (13sp/400), label-large (12sp/700), label-small (12sp/700). Tamaño mínimo en pantalla: 12sp. La UI respeta `fontScale` del sistema operativo.

UX-DR3: Implementar shapes en `ui/theme/Shape.kt` — cards 16dp, botones 12dp, chips 20dp (completamente ovalados), bottom sheets 28dp arriba/0dp abajo, inputs 10dp, badges 20dp.

UX-DR4: Layout y spacing — margen horizontal de pantalla 16dp en ambos lados; gap entre cards 10dp; padding interno cards 14dp vertical / 16dp horizontal; FAB margin 16dp del borde inferior derecho; altura mínima list items 64dp (targets táctiles cómodos); section gap 24dp; layout una columna en toda la app v1.

UX-DR5: Componente `OrderCard.kt` — tres filas: (1) folio en body-small/primary-variant + fecha en body-small/on-surface-variant; (2) nombre del cliente en body-large; (3) monto en title-medium/primary + badge de estado + ícono sync alineados derecha. Toque → navega a S-09. `semantics(mergeDescendants = true)`. Badge y SyncIcon se actualizan en tiempo real sin recargar pantalla completa. Elevation 1dp.

UX-DR6: Componente `SyncIcon.kt` — estado sincronizado: nube con checkmark, color sync-ok #00BCD4, `contentDescription` "Sincronizado con la nube."; estado pendiente: nube outline trazo 2dp, color sync-pending #FF7043, `contentDescription` "Pendiente de sincronizar.". Tamaño mínimo 20dp. Exclusivo para indicadores de sincronización — no usar sync-ok en ningún otro contexto.

UX-DR7: Componente `StatusBadge.kt` — chip compacto, texto en label-large (12sp/700), fondo tonal 12% del color de estado. 4 estados: Pagada (status-paid verde, texto "Pagada"), Parcialidades (status-pending ámbar, texto "Parcialidades"), Atraso (status-overdue rosa, texto "Atraso"), Cancelada (status-cancelled gris, texto "Cancelada"). Solo íconos + texto para comunicar estado, nunca solo color.

UX-DR8: FAB `ExtendedFloatingActionButton` "Nueva Orden" con ícono `+` — color primary-variant sólido, elevation 6dp. Se colapsa a FAB circular en scroll down; reaparece en scroll up. Permanece visible en empty state de S-02. `contentDescription`: "Nueva Orden".

UX-DR9: `NavigationBar` M3 con 3 tabs: Órdenes (ícono lista), Clientes (ícono persona), Config (ícono engranaje). Tab activo: color primary-variant + indicador pill + etiqueta visible. Destinos inactivos: on-surface-variant. Tap en tab activo → scroll al tope de la lista. No reabre pantalla.

UX-DR10: `SearchBar` M3 en S-02 y S-11. Al activar, aparecen `FilterChip` M3 horizontales deslizables. Chip activo: fondo primary-variant + texto blanco + `leadingIcon` checkmark (triple canal: color + forma + ícono). Solo un chip activo a la vez. Filtro por fecha abre date picker.

UX-DR11: Componente `QuantityStepper.kt` — dos `IconButton` de 48dp × 48dp, círculo visual 40dp, color primary-variant, ícono −/+ en on-primary. Valor en body-large/on-surface. Botón − deshabilitado (color on-surface-variant) cuando cantidad = 1. Sin campo de texto editable en v1.

UX-DR12: Constructor de Métodos de Pago `PaymentMethodRow` (S-07) — fila horizontal: dropdown tipo (`maxLines=1`, `overflow=Ellipsis`, `minWidth=120dp`), campo monto numérico (`weight(1f)`), botón × (`wrapContentWidth`). Teclado numérico con decimales. `contentDescription` del botón ×: "Eliminar método de pago [tipo]". Método Efectivo no repetible. Contador "Restante por asignar" en title-medium/primary con `LiveRegionMode.Polite`. Botón "Confirmar Pago" habilitado cuando Restante = $0.00.

UX-DR13: `VariantSelectorSheet.kt` (S-05) — bottom sheet 28dp corner, animación deslizamiento desde abajo. Chips de selección única para variantes. Stepper de cantidad. Botón "Agregar a la orden" deshabilitado hasta seleccionar variante. Al cerrar sin confirmar (swipe down o Back), foco TalkBack regresa al ítem en S-04 que disparó la apertura. No modifica la orden al cerrar sin confirmar.

UX-DR14: Implementar las 15 superficies S-01 a S-15 según especificaciones de EXPERIENCE.md. Orientación portrait exclusivamente. Máximo 3 niveles de profundidad en cualquier flujo. Navigation Compose con NavGraph.kt y Routes.kt. App bar: elevation 0dp en scroll top, sombra tonal sutil en scroll down.

UX-DR15: Estados de conectividad — Online normal: sin banner; sync en progreso: `LinearProgressIndicator` en app bar (no bloquea UI); Offline: banner no intrusivo 3 segundos → colapsa a ícono en app bar, texto: "Sin internet. Los cambios se guardarán localmente."; Sync completado: `Snackbar` 3s "Sincronizado correctamente ☁"; Error sync: `Snackbar` con acción "Reintentar".

UX-DR16: Empty states en S-02 ("Aún no hay órdenes. Toca + para empezar."), S-04 ("Aún no hay productos en el catálogo. Agrégalos en Configuración." + botón "Ir a Configuración"), S-10 ("No hay cobros programados. Crea una orden con parcialidades para verlos aquí."), S-11 ("Aún no hay clientes. Toca + para agregar el primero."). Cada estado incluye ícono outlined en on-surface-variant y mensaje en body-large.

UX-DR17: Estados de formulario (S-01, S-13, S-14) — `isError = true` + `supportingText` descriptivo al intentar guardar con campo vacío o inválido. Botón guardar deshabilitado hasta campos obligatorios válidos. Sin validación agresiva en tiempo de escritura — errores solo tras primer intento de guardar. `ImeAction`: campos intermedios → `Next`, último campo → `Done`.

UX-DR18: Skeleton screens (shimmer) en primera carga de datos. El contenedor lleva `contentDescription`: "Cargando..." para TalkBack. Spinners circulares solo en acciones puntuales (guardar, sincronizar manualmente). Respeta `ANIMATOR_DURATION_SCALE` del sistema.

UX-DR19: Microcopia en español es-MX — voz cálida y cercana, sin tecnicismos. Confirmaciones en positivo: "¡Listo! La orden quedó registrada." Errores sin culpar: "No pudimos conectar. Trabajando sin internet." Cantidades formateadas con separador de miles y 2 decimales: `$1,250.00`. Fechas en formato humano: "Hoy, 10:25 a.m.", "Mañana", "Lun 29 jun". Sin códigos de error al usuario. Ver ejemplos completos en EXPERIENCE.md § Voice and Tone.

UX-DR20: Accesibilidad WCAG AA — touch targets mínimo 48dp × 48dp en todos los elementos interactivos. `semantics(mergeDescendants = true)` en cards de orden y cliente. `contentDescription` en todos los íconos sin texto adyacente. Formularios con `ImeAction` Next/Done. Al cerrar cualquier bottom sheet, foco TalkBack regresa al elemento que lo disparó. Al abrir S-15, foco se mueve al título del dialog; al cerrar, regresa a pantalla anterior.

UX-DR21: Confirmación de acciones destructivas con `AlertDialog` — labels descriptivos: "Sí, cancelar orden" / "No, mantenerla". Back en bottom sheets y dialogs antes de salir de pantalla. Dialog confirma si se abandona nueva orden desde S-04 o posterior: "¿Abandonar esta orden? Los ítems seleccionados se perderán." / "Sí, salir" / "No, quedarme." Pull-to-refresh en S-02 y S-11 para forzar recarga. Sin swipe-to-delete ni swipe-to-archive.

UX-DR22: S-10 (Agenda) — cada celda de día en calendario personalizado lleva `contentDescription`: "Lunes 29 de junio, 3 cobros pendientes" o "Martes 30 de junio, sin cobros". Días con cobros: cambio de color de fondo del tile + número superpuesto (canal secundario no dependiente del color). Cobros próximos al umbral configurado: ícono de alerta adicional (campana). Toque en día → lista de cobros; toque en fila → navega a S-09.

UX-DR23: S-12 (Perfil de Cliente) — saldo total en display-large (28sp/700). Banner de alerta financiera: color status-overdue para deudas vencidas (incluye monto en texto), color sync-ok para Crédito a Favor (incluye monto en texto). Geolocalización: toque abre mapa externo.

UX-DR24: S-03 (Selección de Cliente) — cliente con deuda vencida muestra badge "Vencido" con texto y color status-overdue (nunca solo color). Botón "Nuevo cliente" → abre S-13 en modo alta rápida con retorno automático a S-03 al guardar.

### FR Coverage Map

| FR | Épica | Descripción |
|----|-------|-------------|
| FR-1 | Epic 1 | Login con usuario y contraseña |
| FR-2 | Epic 1 | Persistencia de sesión |
| FR-3 | Epic 1 | Cierre de sesión |
| FR-4 | Epic 1 | Descarga de Settings al login |
| FR-5 | Epic 2 | Alta de Cliente |
| FR-6 | Epic 2 | Consulta y búsqueda de Clientes |
| FR-7 | Epic 2 | Perfil de Cliente |
| FR-8 | Epic 2 | Edición de datos de Cliente |
| FR-9 | Epic 2 | Notas de seguimiento |
| FR-10 | Epic 2 | Alta y edición de Producto (incl. variantes) |
| FR-11 | Epic 2 | Activar / desactivar Producto |
| FR-12 | Epic 3 | Registro de nueva Venta |
| FR-13 | Epic 3 | Condición de pago — pago único |
| FR-14 | Epic 3 | Condición de pago — Parcialidades |
| FR-15 | Epic 3 | Estatus de Venta automático |
| FR-16 | Epic 3 | Cancelación de Venta |
| FR-17 | Epic 3 | Listado de Ventas |
| FR-18 | Epic 3 | Cobro sobre Venta de pago único |
| FR-18b | Epic 3 | Aplicación de Crédito a Favor |
| FR-19 | Epic 3 | Cobro sobre Parcialidad |
| FR-20 | Epic 3 | Historial de Cobros |
| FR-21 | Epic 5 | Notificaciones push para pagos próximos |
| FR-22 | Epic 5 | Configuración de días de anticipación |
| FR-23 | Epic 5 | Agenda / calendario de pagos |
| FR-24 | Epic 3 | Generación de Ticket |
| FR-25 | Epic 3 | Impresión vía Bluetooth |
| FR-26 | Epic 3 | Compartir Ticket como imagen PNG |
| FR-27 | Epic 5 | Setting max_parcialidades |
| FR-28 | Epic 5 | Setting datos_fiscales para Ticket |
| FR-29 | Epic 5 | Setting dias_anticipacion_recordatorio |
| FR-30 | Epic 5 | Setting serie_folio |
| FR-31 | Epic 5 | Modificación de Settings desde la app |
| FR-32 | Epic 4 | Operación offline completa |
| FR-33 | Epic 4 | Sincronización automática al reconectar |
| FR-34 | Epic 4 | Detección y resolución manual de Conflictos |

## Epic List

### Epic 1: Fundación del Proyecto e Inicio de Sesión
El proveedor puede acceder a la app desde cualquier dispositivo Android con sus credenciales. El equipo tiene la base técnica completa: monorepo, CI/CD, backend multi-tenant, schema PostgreSQL, app Android con Room/Hilt/tema visual y navegación.
**FRs cubiertos:** FR-1, FR-2, FR-3, FR-4

### Epic 2: Gestión de Clientes y Catálogo de Productos
El proveedor puede mantener un directorio de clientes actualizado y un catálogo de productos con variantes, disponibles para usar en campo sin internet.
**FRs cubiertos:** FR-5, FR-6, FR-7, FR-8, FR-9, FR-10, FR-11

### Epic 3: Registro de Ventas, Cobros y Ticket
El proveedor puede registrar una venta completa en campo (sin internet), cobrar parcialidades, gestionar su historial de órdenes y generar el ticket imprimible o compartible.
**FRs cubiertos:** FR-12, FR-13, FR-14, FR-15, FR-16, FR-17, FR-18, FR-18b, FR-19, FR-20, FR-24, FR-25, FR-26

### Epic 4: Sincronización Offline y Resolución de Conflictos
El proveedor puede trabajar sin internet con total confianza, ver qué está sincronizado y qué no, y resolver cualquier conflicto de datos de forma explícita al reconectar.
**FRs cubiertos:** FR-32, FR-33, FR-34

### Epic 5: Agenda de Cobros, Notificaciones y Configuración
El proveedor puede planificar su ruta de cobros del día mediante un calendario, recibir alertas automáticas de vencimientos y personalizar los parámetros operativos de su tenant.
**FRs cubiertos:** FR-21, FR-22, FR-23, FR-27, FR-28, FR-29, FR-30, FR-31

---

## Epic 1: Fundación del Proyecto e Inicio de Sesión

El proveedor puede acceder a la app desde cualquier dispositivo Android con sus credenciales. El equipo tiene la base técnica completa: monorepo, CI/CD, backend multi-tenant, schema PostgreSQL, app Android con Room/Hilt/tema visual y navegación.

### Historia 1.1: Configuración del Monorepo y Pipeline CI/CD

Como desarrollador,
quiero el monorepo configurado con pipelines de CI/CD,
para que cada cambio sea validado automáticamente antes de integrarse.

**Criterios de Aceptación:**

**Dado** que el repositorio está inicializado
**Cuando** se crea un Pull Request
**Entonces** el pipeline `android-ci.yml` ejecuta el build de Android y los tests unitarios
**Y** el pipeline `backend-ci.yml` ejecuta el build de .NET y los tests

**Dado** la estructura del monorepo
**Cuando** se revisa el árbol de directorios
**Entonces** existen `android/`, `backend/`, `.github/workflows/`, `.gitignore` completo y `README.md`

---

### Historia 1.2: Infraestructura Backend — API .NET + PostgreSQL Multi-Tenant

Como desarrollador,
quiero el backend .NET configurado con PostgreSQL, schema-per-tenant y JWT,
para que todos los endpoints futuros tengan una base segura y con aislamiento de datos garantizado.

**Criterios de Aceptación:**

**Dado** que el servidor recibe `POST /api/v1/auth/login` con credenciales válidas
**Cuando** `AuthService` valida las credenciales (contraseña hasheada con bcrypt)
**Entonces** devuelve un JWT que incluye `tenant_id` y `user_id`
**Y** la respuesta es directa sin wrapper: `{ "token": "...", "expiresAt": "..." }`

**Dado** que se establece la conexión a PostgreSQL
**Cuando** la API inicia
**Entonces** EF Core aplica las migraciones automáticamente (tabla `public.tenants` + schema `tenant_{id}` con tabla `users`)
**Y** `TenantSchemaResolver` ejecuta `SET search_path TO tenant_{id}` en cada operación de EF Core

**Dado** que el entorno está configurado
**Cuando** se accede a `/scalar/v1`
**Entonces** la documentación Scalar muestra los endpoints disponibles

**Dado** que ocurre un error en cualquier endpoint
**Cuando** el servidor genera la respuesta
**Entonces** el log de Serilog contiene el stack trace completo
**Y** el cliente recibe `{ "errors": [{ "code": "...", "message": "..." }] }` sin detalles internos

---

### Historia 1.3: Infraestructura Android — Estructura, Room, Hilt y Tema Visual

Como desarrollador,
quiero el proyecto Android configurado con Room, Hilt, el tema visual de DESIGN.md y la navegación base,
para que todas las pantallas futuras tengan una base coherente y compilable.

**Criterios de Aceptación:**

**Dado** que el proyecto Android está inicializado (Compose BOM 2026.06.00, Room 2.8.4, minSdk=26, KSP)
**Cuando** se ejecuta el build
**Entonces** el proyecto compila sin errores y los módulos Hilt están inyectados correctamente

**Dado** que la app está abierta
**Cuando** se navega entre tabs
**Entonces** la `NavigationBar` M3 muestra 3 tabs (Órdenes, Clientes, Config) con colores de DESIGN.md: `primary-variant` (#3949AB) activo con indicador pill, `on-surface-variant` (#6B6B80) inactivos

**Dado** que `Color.kt`, `Type.kt` y `Shape.kt` están implementados
**Cuando** cualquier Composable usa `MaterialTheme.colorScheme` o `MaterialTheme.typography`
**Entonces** los valores corresponden exactamente a los tokens de DESIGN.md (primary `#1A237E`, display-large 28sp/700, cards 16dp, bottom-sheet 28dp, etc.)

**Dado** que `SumitrackDatabase` está inicializado con Room
**Cuando** la app arranca
**Entonces** la base de datos SQLite `sumitrack_01` se crea sin errores
**Y** `BigDecimalConverter` e `InstantConverter` están registrados como TypeConverters

**Dado** que el sistema operativo tiene `fontScale` mayor a 1.0 o animaciones reducidas
**Cuando** la app se abre
**Entonces** los textos escalan respetando el `fontScale` y las transiciones respetan `ANIMATOR_DURATION_SCALE`

---

### Historia 1.4: Inicio de Sesión, Sesión Persistente y Cierre de Sesión

Como proveedor,
quiero iniciar sesión con mis credenciales y mantenerme conectado entre aperturas de la app,
para que pueda operar desde cualquier dispositivo Android sin autenticarme en cada uso.

**Criterios de Aceptación:**

**Dado** que el proveedor abre la app por primera vez (sin sesión cacheada)
**Cuando** se muestra S-01 (Login)
**Entonces** hay campos de usuario (`ImeAction.Next`) y contraseña (`ImeAction.Done`) y un botón "Entrar"

**Dado** que el proveedor ingresa credenciales válidas con conexión
**Cuando** toca "Entrar"
**Entonces** la app descarga los Settings del tenant (FR-4), persiste el token JWT localmente y navega a S-02

**Dado** que el proveedor ingresa credenciales incorrectas
**Cuando** toca "Entrar"
**Entonces** los campos muestran `isError = true` y `supportingText` "Usuario o contraseña incorrectos. Inténtalo de nuevo." (sin códigos de error al usuario)

**Dado** que el proveedor tiene sesión cacheada y abre la app
**Cuando** la app se inicializa
**Entonces** se omite S-01 y navega directamente a S-02

**Dado** que el proveedor está sin conexión y tiene sesión cacheada previa
**Cuando** abre la app
**Entonces** puede operar normalmente con los datos locales en SQLite

**Dado** que el proveedor sin sesión previa intenta abrir la app sin conexión
**Cuando** la app se inicializa
**Entonces** S-01 muestra un mensaje explicativo de que se requiere conexión para el primer acceso

**Dado** que el proveedor toca "Cerrar sesión" en Config y confirma el dialog destructivo
**Cuando** se ejecuta el cierre
**Entonces** el token local se invalida, los datos de sesión se borran y la app navega a S-01

---

## Epic 2: Gestión de Clientes y Catálogo de Productos

El proveedor puede mantener un directorio de clientes actualizado y un catálogo de productos con variantes, disponibles para usar en campo sin internet.

### Historia 2.1: Lista y Búsqueda de Clientes

Como proveedor,
quiero ver todos mis clientes en una lista y buscar por nombre,
para que pueda encontrar a cualquier cliente en segundos mientras estoy en campo.

**Criterios de Aceptación:**

**Dado** que el proveedor navega al tab Clientes
**Cuando** S-11 se muestra
**Entonces** la lista muestra todos los clientes con nombre, teléfono, saldo actual e `SyncIcon` (visual); hay `SearchBar` M3 en la parte superior con `FilterChipRow` expansible; hay botón "+" para alta

**Dado** que el proveedor escribe en el `SearchBar`
**Cuando** ingresa caracteres
**Entonces** la lista filtra en tiempo real por nombre desde SQLite local sin requerir conexión

**Dado** que el proveedor no tiene clientes registrados
**Cuando** S-11 se muestra
**Entonces** se muestra empty state: ícono outlined en `on-surface-variant`, mensaje "Aún no hay clientes. Toca + para agregar el primero."

**Dado** que la consulta se ejecuta desde SQLite local
**Cuando** el proveedor abre la lista
**Entonces** la respuesta con saldo por cliente se obtiene en menos de 10 segundos

---

### Historia 2.2: Alta y Edición de Cliente

Como proveedor,
quiero registrar y editar clientes desde mi teléfono,
para que siempre tenga un directorio actualizado de mis compradores.

**Criterios de Aceptación:**

**Dado** que el proveedor toca "+" en S-11 o "Editar" en S-12
**Cuando** S-13 se muestra (modo alta o edición)
**Entonces** hay campos: nombre/razón social (obligatorio), teléfono (obligatorio), RFC (opcional), dirección (opcional), notas (opcional); `ImeAction.Next` entre campos, `ImeAction.Done` en notas

**Dado** que el proveedor intenta guardar con campos obligatorios vacíos
**Cuando** toca "Guardar"
**Entonces** los campos vacíos muestran `isError = true` y `supportingText` descriptivo ("El nombre es obligatorio"); el botón "Guardar" permanece deshabilitado hasta que los campos obligatorios tengan valor

**Dado** que el proveedor completa el formulario y toca "Guardar"
**Cuando** se confirma
**Entonces** el cliente se persiste en SQLite con UUID generado en cliente y `sync_status = pending`; aparece de inmediato en S-11

**Dado** que S-13 se abre en modo alta rápida (desde S-03 en nueva orden)
**Cuando** el proveedor guarda el nuevo cliente
**Entonces** regresa automáticamente a S-03 con el nuevo cliente pre-seleccionado

---

### Historia 2.3: Perfil de Cliente con Saldo y Órdenes Abiertas

Como proveedor,
quiero ver el perfil de un cliente con su saldo total y sus ventas abiertas,
para que sepa en segundos cuánto me debe sin buscar en ningún lado.

**Criterios de Aceptación:**

**Dado** que el proveedor toca un cliente en S-11
**Cuando** S-12 se muestra
**Entonces** la cabecera muestra el nombre del cliente y el saldo total en `display-large` (28sp/700)

**Dado** que el cliente tiene deudas vencidas
**Cuando** S-12 se muestra
**Entonces** aparece banner de alerta con color `status-overdue` (#AD1457) que incluye el monto vencido en texto (nunca solo color)

**Dado** que el cliente tiene Crédito a Favor
**Cuando** S-12 se muestra
**Entonces** aparece banner con color `sync-ok` (#00BCD4) que muestra el monto disponible en texto

**Dado** que hay ventas en estado Pendiente o Parcial para ese cliente
**Cuando** S-12 se muestra
**Entonces** se listan las ventas abiertas con folio, monto y `StatusBadge`

**Dado** que `CalculateClientBalanceUseCase` calcula el saldo
**Cuando** procesa los montos
**Entonces** usa `BigDecimal` (nunca `Double` ni `Float`) y suma solo ventas en estado Pendiente o Parcial

---

### Historia 2.4: Catálogo de Productos y Variantes

Como proveedor,
quiero gestionar mi catálogo de productos con sus variantes y precios,
para que pueda agregarlos rápidamente a una venta con los datos correctos.

**Criterios de Aceptación:**

**Dado** que el proveedor accede a Catálogo dentro de S-14 (Configuración)
**Cuando** la pantalla de productos se muestra
**Entonces** lista todos los productos activos con nombre y precio; hay botón para agregar nuevo producto

**Dado** que el proveedor crea o edita un producto
**Cuando** guarda
**Entonces** el producto tiene: nombre, precio unitario (`BigDecimal`, almacenado como `NUMERIC(18,6)`), impuesto (%) y estado (activo/inactivo); UUID generado en cliente; `sync_status = pending`

**Dado** que un producto tiene variantes configuradas
**Cuando** el proveedor las administra
**Entonces** cada variante se persiste como `ProductVariantEntity` vinculada al producto con su propio UUID

**Dado** que el proveedor desactiva un producto
**Cuando** lo edita y cambia el estado
**Entonces** el producto desaparece de la lista de ítems en nuevas ventas (S-04)
**Y** sigue visible en el historial de ventas anteriores donde fue usado

---

## Epic 3: Registro de Ventas, Cobros y Ticket

El proveedor puede registrar una venta completa en campo sin internet, cobrar parcialidades, gestionar su historial de órdenes y generar el ticket imprimible o compartible.

### Historia 3.1: Historial de Órdenes

Como proveedor,
quiero ver todas mis ventas en una lista con su estado actual,
para que pueda consultar en segundos el panorama completo de mis créditos activos.

**Criterios de Aceptación:**

**Dado** que el proveedor navega al tab Órdenes
**Cuando** S-02 se muestra
**Entonces** la lista de `OrderCard` muestra las ventas en orden cronológico descendente; cada card incluye folio (body-small/primary-variant), fecha (body-small/on-surface-variant), nombre del cliente (body-large), monto total (title-medium/primary), `StatusBadge` e `SyncIcon`; hay `SearchBar` con `FilterChipRow` por estado; FAB "Nueva Orden" visible

**Dado** que el proveedor activa un chip de filtro
**Cuando** selecciona un estado (ej. "Atraso")
**Entonces** el chip activo muestra fondo primary-variant + texto blanco + leadingIcon checkmark; la lista se filtra mostrando solo ventas de ese estado

**Dado** que el proveedor no tiene ventas registradas
**Cuando** S-02 se muestra
**Entonces** aparece empty state: "Aún no hay órdenes. Toca + para empezar." con FAB visible

**Dado** que el proveedor toca una `OrderCard`
**Cuando** se registra el toque
**Entonces** navega a S-09 (Detalle de Orden); la card implementa `semantics(mergeDescendants = true)` para TalkBack

---

### Historia 3.2: Selección de Cliente e Ítems en Nueva Orden

Como proveedor,
quiero seleccionar un cliente y agregar productos a una nueva orden mientras estoy en campo,
para que pueda iniciar el registro de una venta en menos de 2 minutos.

**Criterios de Aceptación:**

**Dado** que el proveedor toca el FAB "Nueva Orden" en S-02
**Cuando** S-03 se muestra
**Entonces** hay un campo de búsqueda de clientes que filtra en tiempo real desde SQLite; cada resultado muestra nombre y saldo; si el cliente tiene deuda vencida se muestra badge "Vencido" en `status-overdue` con texto visible

**Dado** que el cliente buscado no existe
**Cuando** el proveedor toca "Nuevo cliente"
**Entonces** S-13 se abre en modo alta rápida; al guardar regresa a S-03 con el nuevo cliente pre-seleccionado

**Dado** que el proveedor confirma el cliente y avanza a S-04
**Cuando** S-04 se muestra
**Entonces** lista los productos activos del catálogo con nombre y precio; los productos con variantes muestran chip "Variantes"; hay barra inferior persistente con subtotal acumulado y botón "Revisar Orden"

**Dado** que el proveedor toca un producto sin variantes
**Cuando** se registra el toque
**Entonces** el producto se agrega a la orden con cantidad 1; el indicador de contador aparece en el ítem; el subtotal se actualiza

**Dado** que el proveedor toca un producto con variantes
**Cuando** se registra el toque
**Entonces** se abre `VariantSelectorSheet` (S-05) con chips de selección única de variantes y `QuantityStepper`; botón "Agregar" deshabilitado hasta seleccionar variante; al cerrar sin confirmar el foco TalkBack regresa al ítem en S-04

**Dado** que el proveedor toca Back desde S-04 (o posterior) con ítems seleccionados
**Cuando** se registra la navegación
**Entonces** aparece dialog: "¿Abandonar esta orden? Los ítems seleccionados se perderán." con botones "Sí, salir" / "No, quedarme."

**Dado** que el catálogo está vacío
**Cuando** S-04 se muestra
**Entonces** aparece empty state: "Aún no hay productos en el catálogo. Agrégalos en Configuración." con botón "Ir a Configuración"

---

### Historia 3.3: Resumen de Orden y Configuración de Pago

Como proveedor,
quiero revisar el resumen de la orden y definir las condiciones de pago acordadas con el cliente,
para que quede registrado exactamente lo que se pactó en el momento.

**Criterios de Aceptación:**

**Dado** que el proveedor toca "Revisar Orden" en S-04
**Cuando** S-06 se muestra
**Entonces** lista todos los ítems con subtotal por línea; sección fija al fondo con subtotal, impuestos y total; nombre del cliente en la cabecera; botón "Editar" regresa a S-04; botón "Ir a Pagar"

**Dado** que el proveedor avanza a S-07
**Cuando** selecciona modo "Pago inmediato"
**Entonces** aparece el constructor de `PaymentMethodRow`: dropdown de tipo (minWidth=120dp), campo de monto (`weight(1f)`), botón × con `contentDescription` "Eliminar método de pago [tipo]"; el método Efectivo no puede repetirse; botón "+ Agregar método" disponible

**Dado** que el proveedor agrega o modifica métodos de pago
**Cuando** cambia los montos
**Entonces** el contador "Restante por asignar" se actualiza en title-medium/primary con `LiveRegionMode.Polite` para TalkBack; botón "Confirmar Pago" habilitado solo cuando Restante = $0.00

**Dado** que el proveedor selecciona modo "Parcialidades"
**Cuando** ingresa el número de parcialidades (1 ≤ N ≤ max_parcialidades de Settings)
**Entonces** el sistema sugiere fechas según la periodicidad seleccionada (semanal/quincenal/mensual); el proveedor puede ajustar fechas y montos; el sistema valida que la suma de parcialidades iguale exactamente el total antes de habilitar "Confirmar Pago"


**Dado** que el proveedor confirma el pago
**Cuando** se ejecuta `ValidateFolioUseCase`
**Entonces** el folio se asigna usando el contador local (`serie_folio` + número auto-incremental); el folio es definitivo y nunca se reutiliza incluso si la venta se cancela después

---

### Historia 3.4: Generación y Distribución del Ticket

Como proveedor,
quiero generar y compartir o imprimir el ticket inmediatamente después de confirmar una venta,
para que el cliente reciba su comprobante en el momento.

**Criterios de Aceptación:**

**Dado** que el proveedor confirma el pago en S-07
**Cuando** la venta se persiste en SQLite (`sync_status = pending`, folio asignado)
**Entonces** se muestra S-08 (TicketSheet) con la vista previa del ticket que incluye: datos fiscales del tenant (nombre, RFC, dirección, teléfono desde Settings), datos del cliente, ítems con cantidades y precios, subtotal/impuestos/total, condición de pago (fechas de parcialidades si aplica) y folio

**Dado** que el proveedor toca "Imprimir vía Bluetooth"
**Cuando** `GenerateTicketUseCase` genera el PNG en memoria
**Entonces** la app intenta conectar a la impresora térmica vía Bluetooth e imprime el ticket

**Dado** que la impresora no está disponible
**Cuando** falla la conexión Bluetooth
**Entonces** aparece Snackbar: "No encontramos la impresora. La orden ya está guardada — puedes compartir el ticket después."; el bottom sheet permanece abierto para retry o para usar "Compartir"

**Dado** que el proveedor toca "Compartir"
**Cuando** `GenerateTicketUseCase` genera el PNG en memoria
**Entonces** se dispara el Android share intent con la imagen PNG; la imagen NO se guarda en el almacenamiento del dispositivo

**Dado** que el proveedor cierra S-08
**Cuando** toca fuera del sheet o hace swipe down
**Entonces** navega a S-02 (Historial); el foco de TalkBack regresa al FAB en S-02; la nueva orden aparece al tope de la lista con su `StatusBadge` e `SyncIcon` en estado pendiente

---

### Historia 3.5: Detalle de Orden e Historial de Cobros

Como proveedor,
quiero ver todos los detalles de una orden y su historial de pagos registrados,
para que tenga un registro completo que pueda consultar o mostrarle al cliente.

**Criterios de Aceptación:**

**Dado** que el proveedor toca una `OrderCard` en S-02
**Cuando** S-09 se muestra
**Entonces** muestra: folio, fecha, nombre del cliente, `StatusBadge`, listado de ítems con cantidades y precios, condición de pago (fechas de parcialidades o fecha de pago único), historial de cobros con fecha y monto de cada pago registrado

**Dado** que la orden tiene parcialidades
**Cuando** S-09 se muestra
**Entonces** cada parcialidad muestra fecha, monto y estado (pagada/pendiente/vencida)

**Dado** que el proveedor toca "Compartir Ticket"
**Cuando** se registra la acción
**Entonces** `GenerateTicketUseCase` genera el PNG en memoria y dispara el Android share intent

**Dado** que el proveedor toca "Cancelar Orden" (para ventas que no estén ya canceladas)
**Cuando** se registra la acción
**Entonces** aparece `AlertDialog` de confirmación: "¿Cancelar esta orden? Esto no se puede deshacer." con botones "Sí, cancelar orden" / "No, mantenerla"

---

### Historia 3.6: Registro de Cobros sobre Ventas y Parcialidades

Como proveedor,
quiero registrar los pagos que recibo contra una venta o parcialidad,
para que el saldo del cliente se actualice automáticamente sin cálculo manual.

**Criterios de Aceptación:**

**Dado** que la orden es de pago único y el proveedor registra el cobro desde S-09
**Cuando** `RegisterPaymentUseCase` ejecuta con el monto completo
**Entonces** la venta pasa a estado `Liquidado`; el cobro queda en historial con fecha y monto; el saldo del cliente se actualiza de inmediato en SQLite

**Dado** que la orden tiene parcialidades y el proveedor marca una como pagada
**Cuando** `RegisterPaymentUseCase` ejecuta
**Entonces** si quedan parcialidades pendientes: estado de la venta → `Parcial`; si todas están cubiertas: estado → `Liquidado`; el cobro queda en historial

**Dado** que se registra cualquier cobro
**Cuando** `RegisterPaymentUseCase` persiste el pago
**Entonces** el `PaymentEntity` se guarda en SQLite con UUID generado en cliente, `sync_status = pending`, fecha y monto en `BigDecimal`; el saldo en S-12 se recalcula automáticamente

---

### Historia 3.7: Crédito a Favor y Cancelación de Venta con Cobros

Como proveedor,
quiero cancelar una venta y gestionar correctamente los pagos ya recibidos,
para que mi contabilidad quede exacta y ningún dinero se pierda sin registro.

**Criterios de Aceptación:**

**Dado** que el proveedor cancela una venta sin cobros registrados
**Cuando** confirma en el `AlertDialog`
**Entonces** la venta pasa a `Cancelado`; las parcialidades pendientes se cancelan; el saldo del cliente se actualiza

**Dado** que el proveedor cancela una venta con cobros (estado Parcial)
**Cuando** confirma en el dialog
**Entonces** aparece un segundo dialog con dos opciones: "Cancelar parcialidades" (Opción A — acuerdo manual, cobros quedan en historial) y "Generar Crédito a Favor" (Opción B — se crea `CreditBalanceEntity` por el monto total cobrado)

**Dado** que el proveedor elige Opción B (Crédito a Favor)
**Cuando** se ejecuta `ApplyCreditBalanceUseCase`
**Entonces** se crea `CreditBalanceEntity` con: UUID en cliente, `fk_client`, `fk_tenant`, monto en `BigDecimal`/`NUMERIC(18,6)`, `origin = CANCELLATION`, `fk_origin_sale`, `sync_status = pending`; el banner de Crédito a Favor aparece en S-12

**Dado** que el cliente tiene Crédito a Favor y el proveedor crea una nueva venta
**Cuando** llega a S-07 (Pantalla de Pago)
**Entonces** aparece chip informativo con el monto de Crédito disponible; el proveedor puede aplicarlo como método de pago

**Dado** que la `CreditBalanceEntity` ya existe (creada en esta historia) y la `PaymentScreen` (S-07) tiene el hook visual del chip
**Cuando** el chip de Crédito a Favor se integra en S-07
**Entonces** el chip muestra el monto exacto disponible para ese cliente; al seleccionarlo se suma como método de pago en el constructor de `PaymentMethodRow`

**Dado** que el Crédito a Favor cubre el total de la venta
**Cuando** se aplica
**Entonces** la venta pasa directamente a `Liquidado`; el Crédito se descuenta del saldo disponible

**Dado** que se cancela una venta con cobros (cualquier opción elegida)
**Cuando** finaliza el proceso
**Entonces** todos los `PaymentEntity` previos quedan en historial para auditoría; nunca se borran

---

## Epic 4: Sincronización Offline y Resolución de Conflictos

El proveedor puede trabajar sin internet con total confianza, ver qué está sincronizado y qué no, y resolver cualquier conflicto de datos de forma explícita al reconectar.

### Historia 4.1: Motor de Sincronización Push en Background

Como proveedor,
quiero que mis cambios se sincronicen automáticamente con la nube cuando tenga señal,
para que mis datos siempre estén respaldados sin que yo tenga que hacer nada.

**Criterios de Aceptación:**

**Dado** que la app tiene registros con `sync_status = pending` y hay conexión disponible
**Cuando** `PushWorker` ejecuta en background via WorkManager
**Entonces** envía los registros pendientes a `POST /api/v1/sync/push/{entity}` por tipo de entidad (clientes, productos, ventas, parcialidades, cobros, settings)
**Y** al recibir respuesta exitosa actualiza `sync_status = synced` en SQLite

**Dado** que el push falla por error de red
**Cuando** WorkManager reintenta
**Entonces** aplica `ExponentialBackoffPolicy`; el registro permanece con `sync_status = pending`; no se pierde ningún dato

**Dado** que la app pierde conexión mientras hay registros pendientes
**Cuando** se recupera la señal
**Entonces** WorkManager retoma automáticamente la cola de pendientes sin intervención del usuario

**Dado** que el push ocurre en background
**Cuando** el proveedor opera la app simultáneamente
**Entonces** la UI no se bloquea ni degrada visiblemente (NFR-1)

**Dado** que el servidor recibe `POST /api/v1/sync/push/{entity}`
**Cuando** el `SyncController` procesa el payload
**Entonces** persiste los registros en el schema del tenant correcto (via `TenantSchemaResolver`); devuelve confirmación por registro; el UUID del cliente nunca se reasigna

---

### Historia 4.2: Pull Inicial y Folio del Servidor al Hacer Login

Como proveedor,
quiero que al iniciar sesión todos mis datos se descarguen al teléfono,
para que pueda operar offline de inmediato con mi historial completo disponible.

**Criterios de Aceptación:**

**Dado** que el proveedor hace login exitoso (con conexión)
**Cuando** la sesión se establece
**Entonces** `PullService` descarga el delta de cada entidad via `GET /api/v1/sync/pull/{entity}?since={last_sync_at}` (clientes, productos, ventas, parcialidades, cobros, settings)
**Y** se muestra un indicador de progreso durante la descarga sin bloquear la navegación

**Dado** que es el primer login (sin `last_sync_at` almacenado)
**Cuando** ejecuta el pull
**Entonces** descarga la totalidad de los datos del tenant desde el servidor

**Dado** que el pull initial completa exitosamente
**Cuando** termina la descarga
**Entonces** el último folio confirmado del servidor se almacena en SQLite como contador local de folios
**Y** los folios generados offline desde ese momento siguen la secuencia `{serie_folio}{contador++}`

**Dado** que `last_sync_at` está almacenado por entidad en SQLite
**Cuando** el proveedor ejecuta un pull manual posterior
**Entonces** solo se descargan registros modificados en el servidor después de `last_sync_at` (pull incremental/delta)

**Dado** que el servidor recibe `GET /api/v1/sync/pull/{entity}?since={iso_timestamp}`
**Cuando** `SyncController` procesa la solicitud
**Entonces** devuelve solo los registros con `updated_at > since` del schema del tenant; respuesta directa como lista sin wrapper

---

### Historia 4.3: Indicadores de Sincronización en la UI

Como proveedor,
quiero ver claramente qué registros están sincronizados y cuáles están pendientes,
para que siempre sepa el estado real de mis datos sin entrar en ningún menú.

**Criterios de Aceptación:**

**Dado** que un registro tiene `sync_status = synced`
**Cuando** aparece en una `OrderCard` o `ClientCard`
**Entonces** `SyncIcon` muestra nube con checkmark en `sync-ok` (#00BCD4) con `contentDescription` "Sincronizado con la nube."

**Dado** que un registro tiene `sync_status = pending`
**Cuando** aparece en una card
**Entonces** `SyncIcon` muestra nube outline (trazo 2dp) en `sync-pending` (#FF7043) con `contentDescription` "Pendiente de sincronizar."
**Y** el color `sync-pending` se usa solo en íconos, nunca como color de texto

**Dado** que hay sync en progreso
**Cuando** `PushWorker` está ejecutando
**Entonces** aparece `LinearProgressIndicator` en la parte superior de la app bar (no bloquea la UI)

**Dado** que el dispositivo pierde conexión
**Cuando** se detecta el cambio de estado
**Entonces** aparece banner no intrusivo: "Sin internet. Los cambios se guardarán localmente." que se colapsa a ícono en la app bar después de 3 segundos

**Dado** que el sync completa exitosamente
**Cuando** `PushWorker` termina
**Entonces** aparece `Snackbar` de 3 segundos: "Sincronizado correctamente ☁" sin acción adicional

**Dado** que el sync falla con error
**Cuando** se agota el backoff
**Entonces** aparece `Snackbar` con acción "Reintentar" que dispara un nuevo intento de push inmediato

---

### Historia 4.4: Detección y Resolución de Conflictos

Como proveedor,
quiero ser notificado cuando se detecta un conflicto de datos y poder resolverlo yo mismo,
para que nunca se pierda información silenciosamente y la decisión siempre sea mía.

**Criterios de Aceptación:**

**Dado** que `PushWorker` intenta subir un registro
**Cuando** `ConflictService` detecta que `server.updated_at > client.updated_at` para el mismo `id`
**Entonces** el push NO se ejecuta para ese registro; el `sync_status` cambia a `conflict`; se crea un `ConflictLogEntity` con ambas versiones del registro

**Dado** que existe al menos un registro en `conflict`
**Cuando** el proveedor lo detecta (por el ícono o accediendo a S-14 § Sincronización)
**Entonces** puede abrir S-15 (Resolución de Conflicto) que muestra ambas versiones del registro lado a lado

**Dado** que S-15 se abre
**Cuando** el dialog aparece en pantalla
**Entonces** el foco de TalkBack se mueve automáticamente al título "Conflicto detectado"; hay dos opciones: "Usar versión local" y "Conservar ambas"

**Dado** que el proveedor elige "Usar versión local"
**Cuando** confirma
**Entonces** la versión local reemplaza la del servidor en el siguiente push; `sync_status` vuelve a `pending`; el conflicto queda marcado como resuelto en `ConflictLog`

**Dado** que el proveedor elige "Conservar ambas"
**Cuando** confirma
**Entonces** se crea un segundo registro marcado como duplicado para revisión posterior; ambos quedan en `pending` para push

**Dado** que el proveedor cierra S-15 sin elegir (swipe down o Back)
**Cuando** el dialog se cierra
**Entonces** el conflicto permanece en cola con `sync_status = conflict`; se reofrece en el próximo sync; el foco TalkBack regresa a la pantalla anterior

**Dado** que el proveedor accede a S-14 § Sincronización
**Cuando** consulta el log
**Entonces** puede ver el historial de conflictos resueltos con entidad, fecha de detección y resolución elegida

---

## Epic 5: Agenda de Cobros, Notificaciones y Configuración

El proveedor puede planificar su ruta de cobros del día mediante un calendario, recibir alertas automáticas de vencimientos y personalizar los parámetros operativos de su tenant.

### Historia 5.1: Pantalla de Configuración del Tenant

Como proveedor,
quiero configurar los datos de mi empresa y los parámetros de operación desde la app,
para que mis tickets muestren mi información correcta y el sistema se comporte según mis preferencias.

**Criterios de Aceptación:**

**Dado** que el proveedor navega al tab Config
**Cuando** S-14 se muestra
**Entonces** la pantalla está organizada en secciones: Datos Fiscales · Catálogo de Productos · Parámetros de Venta · Sincronización · Sesión

**Dado** que el proveedor edita los Datos Fiscales
**Cuando** guarda
**Entonces** persisten en `SettingsEntity` en SQLite: nombre/razón social, RFC, dirección fiscal, teléfono (FR-28); `sync_status = pending`; estos datos aparecen en todos los tickets generados por `GenerateTicketUseCase`

**Dado** que el proveedor modifica `max_parcialidades` (rango 1–15) o `serie_folio` (máximo 5 caracteres)
**Cuando** guarda
**Entonces** los nuevos valores se aplican inmediatamente a las siguientes ventas nuevas; `serie_folio` actualiza el prefijo del contador local de folios (FR-27, FR-30)

**Dado** que el proveedor modifica `dias_anticipacion_recordatorio` (rango 1–30)
**Cuando** guarda
**Entonces** el WorkManager de notificaciones reprograma todas las alarmas pendientes con el nuevo valor (FR-29)

**Dado** que el proveedor toca "Sincronizar ahora" en la sección Sincronización
**Cuando** se ejecuta
**Entonces** `PullService` inicia un pull manual inmediato con el `last_sync_at` almacenado (FR-31)

**Dado** que el proveedor toca "Cerrar sesión" en la sección Sesión
**Cuando** se registra el toque
**Entonces** aparece `AlertDialog` destructivo: "¿Cerrar sesión?" con botones descriptivos; al confirmar borra token y navega a S-01

**Dado** que cualquier setting se modifica
**Cuando** se guarda localmente
**Entonces** el cambio es inmediato en la UI; se marca `sync_status = pending` y se sincroniza con la nube al reconectar (FR-31)

---

### Historia 5.2: Notificaciones Push Locales para Recordatorios de Cobro

Como proveedor,
quiero recibir notificaciones automáticas antes de que venza un pago,
para que nunca olvide una visita de cobro y no pierda ningún ingreso por descuido.

**Criterios de Aceptación:**

**Dado** que existe una venta en estado Pendiente o Parcial con fecha de pago futura
**Cuando** `PushWorker` programa las alarmas de WorkManager
**Entonces** se programa una notificación para `fecha_pago - dias_anticipacion_recordatorio` días

**Dado** que llega el momento programado
**Cuando** WorkManager dispara la alarma
**Entonces** aparece una notificación push local con: nombre del cliente, monto pendiente y fecha de vencimiento (FR-21)

**Dado** que una venta pasa a `Liquidado` o `Cancelado`
**Cuando** se actualiza el estado
**Entonces** la notificación programada para esa venta/parcialidad se cancela automáticamente

**Dado** que el proveedor cambia `dias_anticipacion_recordatorio` en Settings
**Cuando** se guarda el nuevo valor
**Entonces** todas las alarmas pendientes se reprograman con el nuevo adelanto (FR-22)

**Dado** que el dispositivo no tiene conexión a internet
**Cuando** WorkManager dispara la alarma
**Entonces** la notificación se muestra igualmente usando solo los datos de SQLite local (cero dependencia de red)

---

### Historia 5.3: Agenda y Calendario de Cobros

Como proveedor,
quiero ver todos mis cobros programados en un calendario,
para que pueda planificar mi ruta del día de un vistazo sin buscar en cada orden.

**Criterios de Aceptación:**

**Dado** que el proveedor toca el ícono de calendario en la app bar de S-02
**Cuando** S-10 se muestra
**Entonces** se muestra el mes actual en un calendario personalizado con Compose

**Dado** que un día tiene cobros programados
**Cuando** se renderiza la celda del día
**Entonces** el tile muestra: fondo de color diferente al resto + número superpuesto con la cantidad de cobros (canal visual secundario, no solo color); los cobros dentro del umbral de `dias_anticipacion_recordatorio` muestran adicionalmente un ícono de campana

**Dado** que cada celda de día se renderiza
**Cuando** TalkBack la anuncia
**Entonces** la `contentDescription` dice "Lunes 29 de junio, 3 cobros pendientes" o "Martes 30 de junio, sin cobros" (FR-23, UX-DR22)

**Dado** que el proveedor toca un día con cobros
**Cuando** se registra el toque
**Entonces** se despliega la lista de cobros de ese día con: nombre del cliente, monto, folio y estado de vencimiento

**Dado** que el proveedor toca una fila de cobro en la lista del día
**Cuando** se registra el toque
**Entonces** navega a S-09 (Detalle de Orden) de la venta correspondiente

**Dado** que no hay cobros programados en ningún día
**Cuando** S-10 se muestra
**Entonces** aparece empty state: "No hay cobros programados. Crea una orden con parcialidades para verlos aquí."

**Dado** que el proveedor está offline
**Cuando** S-10 se muestra
**Entonces** el calendario usa los datos de SQLite local; no aparece banner adicional de offline (los datos offline son confiables para la agenda)
