
## Deferred from: code review de 2-1-lista-y-busqueda-de-clientes (2026-07-08)

- **`ClientRepository.upsertAll(clients: List<ClientEntity>)` expone el tipo de entidad Room en la API pública del repositorio** — rompe la separación dominio/datos que sigue el resto del archivo; sin caller aún, resolver antes de que Historia 4.x conecte el motor de sincronización. [ClientRepository.kt]

## Deferred from: code review de 1-4-inicio-de-sesion-sesion-persistente-y-cierre-de-sesion (2026-07-06)

- **Token JWT en DataStore sin cifrar** — Considerar `EncryptedSharedPreferences` en Epic 4 cuando se implemente sincronización offline. [SessionManager.kt]
- **Sin interceptor OkHttp para Authorization header** — Auth manual en cada servicio API; refactorizar cuando haya ≥3 servicios autenticados. [NetworkModule.kt]
- **`DbSet<Setting>` + raw DDL mezclados** — Si se corre `dotnet ef migrations add` generará migración conflictiva con tabla ya existente. Consolidar estrategia de migraciones en Epic 4. [TenantDbContext.cs]
- **`expiresAt` del JWT nunca verificado** — App no detecta token expirado en foreground; implementar en Historia 4.2 (Pull inicial y folio del servidor al hacer login). [LoginResponseDto.kt]
- **`upsertAll` sin previa `deleteAll`** — Keys eliminadas en servidor persisten en local DB; diseño de sync a definir en Epic 4 (Historia 4.1). [SettingsRepository.kt]
- **Slug de tenant interpolado en SQL raw sin validar** — Riesgo de inyección si slug contiene `"`; pre-existing, sanitizar con regex `^[a-z0-9_-]+$`. [ApplicationBuilderExtensions.cs]
- **Tabla settings ausente causa 500 no manejado** — Infrastructure concern; agregar try/catch con 503 en SettingsController. [SettingsController.cs]
- **`clearToken()` sin manejo de IOException** — Usuario aparece logueado si DataStore falla al escribir; defensive programming. [SettingsViewModel.kt]

## Deferred from: code review de 1-3-infraestructura-android-estructura-room-hilt-y-tema-visual (2026-06-29)

- **InstantConverter ArithmeticException para Instant extremos** — `toEpochMilli()` lanza para Instants fuera del rango Long-millis (~año 292M). Irrelevante en un POS (fechas 2020-2040), pero proteger si se usan centinelas Instant.MAX/MIN. [InstantConverter.kt]
- **Shapes.large asimétrico** — `large = RoundedCornerShape(topStart=28, topEnd=28, bottom=0)` es correcto para bottom-sheets, pero M3 aplica `Shapes.large` automáticamente a AlertDialog y ModalDrawer. Si se usan esos componentes, quedarán con bordes cuadrados inferiores. Revisar al implementar diálogos. [Shape.kt]
- **Sin dark mode** — `SumitrackTheme` solo define `lightColorScheme`. Dispositivos con dark mode activo en fabricantes con Force Dark pueden producir UI ilegible. Implementar `darkColorScheme` cuando el producto lo requiera. [Theme.kt]
- **Strings hardcodeadas en NavigationBar** — "Órdenes", "Clientes", "Config" en código Kotlin, no en `strings.xml`. Mover a recursos de strings si se agrega soporte multilenguaje. [MainScreen.kt]
- **android:allowBackup=true + sin migración Room** — Backup de schema v1 restaurado en app con schema v2 (sin migración) causa crash en arranque. Mitigar con `<full-backup-content>` excluendo la DB o implementando migrations antes del primer release. [AndroidManifest.xml]
- **isMinifyEnabled=false en release** — APK completamente reversible con jadx/apktool. Para app fiscal con CFDI, habilitar R8 antes de producción. [app/build.gradle.kts] (pre-existing desde Historia 1.1)

## Deferred from: code review de 1-2-infraestructura-backend-api-net-postgresql-multi-tenant (2026-06-28)

- **JWT 365 días sin revocación** — Decisión de diseño v1. Sin refresh token ni blacklist. Revisar cuando se añadan roles o multi-sesión.
- **User.UpdatedAt nunca se actualiza tras INSERT** — Siempre muestra fecha de creación. Requiere trigger BD o SaveChanges interceptor cuando se implemente endpoint de actualización de perfil.
- **Race condition en seed con dos instancias simultáneas** — Solo aplica a horizontal scaling; seed es Development-only en v1. Resolver con transacción SERIALIZABLE cuando se implemente multi-instancia.
- **ExpiresAt es DateTime en vez de DateTimeOffset** — Sin impacto funcional (UTC serializa con Z). Refactorizar si se adopta DateTimeOffset como convención del proyecto.

## Deferred from: code review de 1-1-configuracion-del-monorepo-y-pipeline-ci-cd (2026-06-27)

- **Placeholder `<local_password>` en `appsettings.Development.json`** — se reemplaza con valores reales (Connection string Railway dev) en Historia 1.2
- **`enableEdgeToEdge()` sin tema edge-to-edge** — `themes.xml` usa `android:Theme.Material.Light.NoActionBar` como base; Historia 1.3 actualiza a Material3-compatible con soporte correcto de edge-to-edge
