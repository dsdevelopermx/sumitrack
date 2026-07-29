
## Deferred from: code review de 3-5-detalle-de-orden-e-historial-de-cobros (2026-07-29)

- **`tenantId.first()` sin `runCatching` en `OrderDetailViewModel.init` y `onShareTicketClick`** — patrón inconsistente con otros ViewModels del proyecto (algunos envuelven la llamada, otros no; `PaymentViewModel.kt:121` tampoco la envuelve). Si el `Flow<String?>` de `SessionManager.tenantId` llegara a lanzar, la pantalla queda en `isLoading = true` indefinidamente sin estado de error. Requiere una pasada dedicada por todo el codebase para unificar el criterio. [OrderDetailViewModel.kt]
- **`runCatching { }.getOrNull()` sin logging en `OrderDetailViewModel`** (sale detail, client, generación de ticket) — misma familia del patrón de manejo de errores silencioso ya diferido repetidamente desde Historia 2.3. [OrderDetailViewModel.kt]
- **Test `onShareTicketClick generates the ticket once and reuses it` no verifica el conteo real de invocaciones a `generateTicketUseCase`** — solo compara el `TicketData` resultante por igualdad; una regresión que regenere el ticket en cada llamada (de forma determinista) pasaría el test sin detectarse. Requeriría instrumentar `SaleRepository`/`ClientRepository` con un contador o introducir un mock/spy. [OrderDetailViewModelTest.kt]

## Deferred from: code review de 3-4-generacion-y-distribucion-del-ticket (2026-07-26)

- **`GenerateTicketUseCase.getClientById` hereda la falta de tenant-scoping de `ClientRepository.getClientById`** — la firma no acepta `tenantId`. Misma familia del gap ya diferido desde Historias 2.1/2.2 en `ClientDao.getAllAsFlow()`/`searchByNameAsFlow()`; ahora también heredado por `OrderDetailViewModel.kt` (Historia 3.5) al reutilizar `clientRepository.getClientById` directamente — ya son 7 historias mencionando esta clase de gap sin resolverla — cada vez más candidato a una historia dedicada. [ClientRepository.kt, GenerateTicketUseCase.kt, OrderDetailViewModel.kt]
- **Sin limpieza/expiración de `cacheDir/tickets/`** — cada "Compartir" escribe un PNG nuevo que nunca se borra; el sistema operativo purga `cacheDir` bajo presión de almacenamiento como respaldo, pero no hay limpieza explícita. Baja prioridad. [TicketFileWriter.kt]
- **El ticket no muestra el desglose de métodos de pago** — solo usa `payments.firstOrNull()?.paidAt` para la fecha del pago único, descartando el resto de la lista. Gap de producto real para un "comprobante de pago" con métodos mixtos, pero ningún AC lo exige explícitamente. Candidato a una historia futura relacionada con tickets/comprobantes. [GenerateTicketUseCase.kt]
- **Fallos reales al buscar el cliente en `GenerateTicketUseCase` se tragan silenciosamente** (`runCatching{}.getOrNull()` sin registro) — misma familia del patrón de manejo de errores silencioso ya diferido repetidamente desde Historia 2.3 (ahora también en el ticket, mismo bucket que la pérdida de ítems del carrito diferida en Historia 3.3). [GenerateTicketUseCase.kt]
- **Sin timeout/cancelación explícita en `BluetoothSocket.connect()`** — si la impresora está emparejada pero fuera de rango o apagada, `isPrinting` puede quedar en `true` por el tiempo del timeout implícito del SO, sin UI de "cancelar". Ningún AC exige un timeout específico. [AndroidBluetoothTicketPrinter.kt]
- **Un solo comando ESC/POS `GS v 0` sin fragmentar para todo el alto del ticket** — impresoras térmicas baratas con buffers de imagen pequeños (el tipo de impresora objetivo de esta historia) pueden atascarse o distorsionar la imagen con comandos raster muy altos. Fragmentar en bandas es el enfoque estándar de librerías ESC/POS maduras; cambio no trivial que requiere hardware real para validar. [EscPosEncoder.kt]
- **El texto del `Bitmap` renderizado no hace salto de línea** — un nombre de producto/variante largo se corta silenciosamente en la imagen impresa/compartida, mientras la vista previa en pantalla sí hace wrap normalmente. Implementar wrapping manual en `Canvas.drawText` es un cambio no trivial; ningún AC exige que la vista previa y el render final coincidan pixel a pixel. [TicketBitmapRenderer.kt]

## Deferred from: code review de 3-3-resumen-de-orden-y-configuracion-de-pago (2026-07-26)

- **Pérdida silenciosa de un ítem del carrito si el producto fue eliminado/desactivado entre S-04 y S-07** — `OrderSummaryViewModel`/`PaymentViewModel` resuelven el carrito codificado vía `productRepository.getProductById(...) ?: return@mapNotNull null`, descartando silenciosamente cualquier línea cuyo producto ya no exista, sin ningún aviso al usuario. Cambia el monto que se le cobrará al cliente sin que se note. Misma familia que el patrón de manejo de errores silencioso ya diferido en Historias 2.3/3.2, pero de mayor prioridad que los demás ítems de ese bucket por el impacto directo en el monto cobrado — priorizar este cuando se haga la pasada dedicada. [OrderSummaryViewModel.kt, PaymentViewModel.kt]
- **Ningún test ejercita el rollback transaccional de `SaleRepository.createSale`** — `FakeTransactionRunner.run` ejecuta el bloque directamente sin simular un fallo a mitad de transacción, así que la atomicidad real (que sí provee `RoomTransactionRunner`/`Room.withTransaction` en producción) nunca se verifica en tests. No bloquea ningún AC; agregar cuando se toque `SaleRepository` de nuevo. [SaleRepositoryTest.kt]

## Deferred from: code review de 3-2-seleccion-de-cliente-e-items-en-nueva-orden (2026-07-19)

- **Carrito en memoria de `ItemListViewModel` sin respaldo de `SavedStateHandle`** — una muerte de proceso a medio armar una orden en campo (escenario que la propia historia usa para justificar el flujo) pierde el carrito sin aviso. Persistir una lista anidada de `OrderDraftItem` (con `BigDecimal`, entidades de dominio) de forma Bundle-safe es una expansión de alcance sustancial, no exigida por ningún AC. [ItemListViewModel.kt]
- **`runCatching{}.getOrDefault(...)` en `ItemListViewModel` traga errores sin ningún indicio al usuario** (carga inicial de `idsWithVariants`, `getVariantsForProduct` por toque) — mismo patrón de manejo de errores silencioso ya diferido en Historia 2.3 (`CancellationException`); requiere una pasada dedicada por todo el codebase. [ItemListViewModel.kt]
- **`ItemListViewModel` no reacciona a cambios de `tenantId` después de la carga inicial** (`tenantId.first()` en vez de un patrón reactivo como `flatMapLatest`, a diferencia de `ClientSelectViewModel`) — impacto práctico bajo, el tenant no cambia sin cerrar sesión. [ItemListViewModel.kt]

## Deferred from: code review de 3-1-historial-de-ordenes (2026-07-19)

- **`.catch { emit(emptyList()) }` en `OrderListViewModel` traga errores y puede dejar el `StateFlow` congelado tras un error real** — por semántica de `Flow.catch` + `stateIn(WhileSubscribed)`, si el flow interno lanza una excepción, `catch` emite el fallback y el flow completa; el `StateFlow` no vuelve a actualizarse hasta que todos los colectores se desconecten y reconecten (ej. background/foreground de la app). Mismo patrón exacto ya usado en `ClientListViewModel` (Historia 2.1) y `ProductListViewModel` (Historia 2.4) — requiere una pasada dedicada por las 3 ViewModels (ya son 3 con el mismo patrón, mismo criterio que el deferred de `CancellationException` de Historia 2.3). [OrderListViewModel.kt, ClientListViewModel.kt, ProductListViewModel.kt]
- **`BackHandler` en `OrderListScreen`/`ClientListScreen` colapsa la búsqueda sin llamar `onSearchClear()`, y el contenido expandido del `SearchBar` es una lambda vacía** — patrón idéntico en ambas pantallas desde Historia 2.1. Verificar en dispositivo real si el overlay expandido oculta la lista subyacente (sospecha no confirmable sin infraestructura de test de Composables); si se confirma, corregir en ambas pantallas a la vez. [OrderListScreen.kt, ClientListScreen.kt]
- **`getOrdersForTenantAsFlow` sin `LIMIT`/paginación** — se re-ejecuta en cada tecleo (tras debounce) con un `JOIN` + cadena de `REPLACE()` anidados y un `LIKE` con comodín inicial no indexable. Sin costo real hoy por volumen bajo; mismo criterio que los índices ya diferidos de `sales`/`clients`/`products`. [SaleDao.kt]
- **`FakeSaleDao.getOrdersForTenantAsFlow` no desescapa el `normalizedQuery` antes de comparar** — diverge del comportamiento real de `LIKE ... ESCAPE '\'` si un nombre de cliente contuviera literalmente `%` o `_`. Gap de fidelidad del fake, sin impacto en producción. [FakeSaleDao.kt]

## Deferred from: code review de 2-4-catalogo-de-productos-y-variantes (2026-07-19)

- **`.catch { emit(emptyList()) }` en `ProductListViewModel` traga errores de Flow/DB silenciosamente** — indistinguible de un catálogo genuinamente vacío. Patrón preexistente idéntico en `ClientListViewModel` desde Historia 2.1, no introducido por esta historia. [ProductListViewModel.kt]
- **`ProductListScreen` sin estado de carga inicial** — puede mostrar el empty state brevemente antes de la primera emisión del `Flow`. Mismo gap preexistente en `ClientListScreen` desde Historia 2.1. [ProductListScreen.kt]
- **Reemplazo total de variantes en cada edición de producto genera churn de `sync_status`** — `ProductRepository.updateProduct` borra y reinserta todas las variantes en vez de hacer diffing; una variante no tocada por el usuario igual recibe `id`/`created_at` nuevos y `sync_status = pending`. Sin costo hoy (no existe motor de sync). Revisar cuando Epic 4 implemente sync real — evaluar si el tráfico generado justifica un diffing por variante. [ProductRepository.kt]
- **Sin índice en `products`/`product_variants` para `fk_product`/`fk_tenant`** — consistente con la falta de índices ya existente en el resto del esquema (`clients.name`, `sales.fk_client`/`status`/`created_at`, sin índice desde Historias 2.1/2.3). Candidato a una pasada de indexación dedicada cuando el volumen de datos importe. [Migrations.kt]

## Deferred from: code review de 2-3-perfil-de-cliente-con-saldo-y-ordenes-abiertas (2026-07-15)

- **`runCatching` traga `CancellationException` sin relanzarla** — patrón preexistente desde `ClientFormViewModel.kt` (Historia 2.2), replicado en `ClientProfileViewModel.kt`. Rompe la cancelación cooperativa si la pantalla se cierra a medio cargar. Requiere una pasada dedicada por todo el codebase (ya son 2+ ViewModels con el mismo patrón). [ClientFormViewModel.kt, ClientProfileViewModel.kt]
- **`client.balance` y `openSales` son dos lecturas no transaccionales de `sales`** — podrían divergir si el estatus de una venta cambia entre ambas llamadas. Inalcanzable hoy: ningún flujo de la app puede escribir en `sales` todavía. Revisar cuando Epic 3 agregue mutación de ventas. [ClientProfileViewModel.kt]
- **Comparación `status IN ('pending','partial')` en `SaleDao` sensible a mayúsculas/minúsculas** — depende de una convención no forzada por código (todo estatus persistido en minúsculas, igual que `sync_status`). Inalcanzable hoy: no existe flujo de escritura a `sales`. Revisar cuando Epic 3 escriba filas reales. [SaleDao.kt]
- **Sin piso en cero ni manejo de montos negativos en `CalculateClientBalanceUseCase`/`formatAmount`** — hoy inalcanzable porque ningún código produce un `total` negativo; relevante cuando Epic 3 construya el flujo de creación de ventas con su propia validación. [CalculateClientBalanceUseCase.kt, ClientProfileScreen.kt]
- **Sin índice en la tabla `sales` para `fk_client`/`status`/`created_at`** — consistente con la falta de índices ya existente en el resto del esquema (p. ej. `clients.name`, sin índice desde Historia 2.1). Candidato a una pasada de indexación dedicada cuando el volumen de datos importe (Epic 3/4). [Migrations.kt]

## Deferred from: code review de 2-2-alta-y-edicion-de-cliente (2026-07-11)

- **Caché local de `clients` sin scope de tenant y sin purga en logout** — un segundo tenant que inicia sesión en el mismo dispositivo vería (y podría editar) clientes del tenant anterior; tensión con NFR-4. Preexistente desde Historia 2.1 (queries sin filtro de tenant) e Historia 1.4 (`clearToken` no purga Room). Requiere historia propia: queries con scope de tenant + purga de caché en logout. [ClientRepository.kt, SessionManager.kt]
- **`Routes.ClientForm.createRoute` sin URL-encoding del `clientId`** — inofensivo hoy (solo se pasan UUIDs), pero la función acepta cualquier `String?`; futuros llamadores (edición desde Historia 2.3) podrían pasar caracteres reservados de URI. [Routes.kt]
- **Sin diálogo de confirmación al salir de S-13 con cambios sin guardar** — no lo exige ningún AC de esta historia; el patrón de confirmación de `EXPERIENCE.md` está acotado al flujo de nueva orden (S-04+). [ClientFormScreen.kt]

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
