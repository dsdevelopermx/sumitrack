---
baseline_commit: 6e1c0bd70d8a5b5defa382bdf0f8298ac0d112ba
---

# Story 2.4: Catálogo de Productos y Variantes

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero gestionar mi catálogo de productos con sus variantes y precios,
para que pueda agregarlos rápidamente a una venta con los datos correctos.

## Acceptance Criteria

**AC-1 — Lista del catálogo**

**Dado** que el proveedor accede a Catálogo dentro de S-14 (Configuración)
**Cuando** la pantalla de productos se muestra
**Entonces** lista los productos con nombre y precio; hay botón para agregar nuevo producto (ver Dev Notes § Decisión de alcance — la lista incluye productos activos e inactivos, no solo activos, para que FR-11 "activar" sea alcanzable desde la UI)

**AC-2 — Alta y edición de producto**

**Dado** que el proveedor crea o edita un producto
**Cuando** guarda
**Entonces** el producto tiene: nombre, precio unitario (`BigDecimal`, almacenado como `NUMERIC(18,6)`), impuesto (%) y estado (activo/inactivo); UUID generado en cliente; `sync_status = pending`

**AC-3 — Variantes**

**Dado** que un producto tiene variantes configuradas
**Cuando** el proveedor las administra
**Entonces** cada variante se persiste como `ProductVariantEntity` vinculada al producto con su propio UUID

**AC-4 — Desactivación**

**Dado** que el proveedor desactiva un producto
**Cuando** lo edita y cambia el estado
**Entonces** el producto desaparece de la lista de ítems en nuevas ventas (S-04)
**Y** sigue visible en el historial de ventas anteriores donde fue usado

### Fuera de alcance en esta historia (explícito)

- **Lista de productos en S-04 (nueva venta)** y el chip "Variantes" que se muestra ahí (`epics.md` Historia 3.2, AC S-04) — Epic 3, no existe todavía `OrderListScreen`/flujo de nueva venta real. Esta historia solo construye la gestión del catálogo (alta/edición/lista) dentro de Configuración (S-14); no filtra por "activos únicamente" en ningún lugar (ver AC-1).
- **`VariantSelectorSheet` (S-05)** — bottom sheet de selección de variante al agregar un producto a una venta (`UX-DR13`). Es UI del flujo de venta (Epic 3, Historia 3.2), no de la gestión del catálogo.
- **Precio o impuesto por variante** — ni `epics.md` (AC-3) ni `architecture.md` (AR-18) mencionan que `ProductVariantEntity` tenga su propio precio; el precio vive únicamente en `ProductEntity`. Una variante es solo un diferenciador (ej. "Chico"/"Grande", "1kg"/"2kg"), no una entidad de precio independiente. No agregar campo de precio a variante.
- **Eliminar producto** — FR-11 y AC-4 solo hablan de desactivar (soft toggle), nunca de borrar. No implementar `DELETE`.
- **Renombrar/editar una variante existente** — AC-3 solo exige que las variantes administradas se persistan con su propio UUID; no hay AC que pida edición de nombre de una variante ya creada. Se implementa alta y baja (agregar/quitar) de variantes, no edición in-place.
- **Integración con S-14 § Datos fiscales / Sincronización / Sesión** — esas secciones de Configuración ya existen (`SettingsScreen.kt`, Historia 1.4) y no se tocan; esta historia solo agrega una entrada de navegación hacia el catálogo.

## Tasks / Subtasks

### Android — Dominio y persistencia

- [x] **T1: Dominio — `Product.kt` y `ProductVariant.kt`** (AC-2, AC-3)
  - [x] Crear `domain/models/Product.kt`:
    ```kotlin
    data class Product(
        val id: String,
        val fkTenant: String,
        val name: String,
        val price: BigDecimal,
        val taxRate: BigDecimal,
        val isActive: Boolean,
        val createdAt: Instant,
        val updatedAt: Instant,
        val syncStatus: SyncStatus,
    )
    ```
    `taxRate` representa el porcentaje directamente (ej. `16.00` = 16%, `0.00` = exento) — mismo tipo `BigDecimal` que `price` por consistencia con AR-17, aunque no sea un monto monetario en sí. Ya anticipado en `architecture.md` línea 395 (`domain/models/Product.kt`), pero sin campos definidos — esta historia los define.
  - [x] Crear `domain/models/ProductVariant.kt`:
    ```kotlin
    data class ProductVariant(
        val id: String,
        val fkTenant: String,
        val fkProduct: String,
        val name: String,
        val createdAt: Instant,
        val updatedAt: Instant,
        val syncStatus: SyncStatus,
    )
    ```
    Sin campo de precio (ver "Fuera de alcance").

- [x] **T2: Room — `ProductEntity`, `ProductVariantEntity`, DAOs, migración** (AC-2, AC-3)
  - [x] Crear `data/local/entities/ProductEntity.kt` — campos obligatorios AR-6 (`id`, `fk_tenant`, `created_at`, `updated_at`, `sync_status`) más `name`, `price`, `tax_rate`, `is_active`:
    ```kotlin
    @Entity(tableName = "products")
    data class ProductEntity(
        @PrimaryKey @ColumnInfo(name = "id") val id: String,
        @ColumnInfo(name = "fk_tenant") val fkTenant: String,
        @ColumnInfo(name = "name") val name: String,
        @ColumnInfo(name = "price") val price: BigDecimal,
        @ColumnInfo(name = "tax_rate") val taxRate: BigDecimal,
        @ColumnInfo(name = "is_active") val isActive: Boolean = true,
        @ColumnInfo(name = "created_at") val createdAt: Instant,
        @ColumnInfo(name = "updated_at") val updatedAt: Instant,
        @ColumnInfo(name = "sync_status") val syncStatus: String = "pending",
    )
    ```
    `price`/`taxRate` usan `BigDecimalConverter` (ya registrado a nivel `@Database`). `is_active` es `Boolean` nativo de Room (columna `INTEGER`, sin converter necesario — mismo tratamiento que cualquier `Boolean` de Room).
  - [x] Crear `data/local/entities/ProductVariantEntity.kt`:
    ```kotlin
    @Entity(tableName = "product_variants")
    data class ProductVariantEntity(
        @PrimaryKey @ColumnInfo(name = "id") val id: String,
        @ColumnInfo(name = "fk_tenant") val fkTenant: String,
        @ColumnInfo(name = "fk_product") val fkProduct: String,
        @ColumnInfo(name = "name") val name: String,
        @ColumnInfo(name = "created_at") val createdAt: Instant,
        @ColumnInfo(name = "updated_at") val updatedAt: Instant,
        @ColumnInfo(name = "sync_status") val syncStatus: String = "pending",
    )
    ```
    Sin `@ForeignKey` de Room — mismo criterio que `SaleEntity.fkClient`/`ClientEntity`: `fk_product` es una referencia a nivel de aplicación, no una constraint SQL declarada (ningún `*Entity` del proyecto usa `@ForeignKey` hoy).
  - [x] Crear `data/local/dao/ProductDao.kt`:
    ```kotlin
    @Dao
    interface ProductDao {
        @Query("SELECT * FROM products ORDER BY name ASC")
        fun getAllAsFlow(): Flow<List<ProductEntity>>

        @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
        suspend fun getById(id: String): ProductEntity?

        @Upsert
        suspend fun upsertAll(products: List<ProductEntity>)
    }
    ```
    `getAllAsFlow` retorna TODOS los productos (activos e inactivos) — ver AC-1 y Dev Notes § Decisión de alcance. No agregar una query "solo activos" en esta historia: Historia 3.2 (S-04) la agregará cuando exista el flujo de nueva venta que sí necesita filtrar (mismo criterio que Historia 2.3 con `SaleDao`: no construir queries para un AC que no existe todavía).
  - [x] Crear `data/local/dao/ProductVariantDao.kt`:
    ```kotlin
    @Dao
    interface ProductVariantDao {
        @Query("SELECT * FROM product_variants WHERE fk_product = :productId ORDER BY name ASC")
        suspend fun getForProduct(productId: String): List<ProductVariantEntity>

        @Upsert
        suspend fun upsertAll(variants: List<ProductVariantEntity>)

        @Query("DELETE FROM product_variants WHERE fk_product = :productId")
        suspend fun deleteAllForProduct(productId: String)
    }
    ```
    Primer DAO del proyecto con un `DELETE` — necesario para el patrón "reemplazar todas las variantes" de T3 (ver justificación ahí). `getForProduct` es `suspend` simple (no `Flow`): se carga una sola vez al abrir el formulario de edición, no se observa en tiempo real (mismo criterio que `SaleDao.getOpenSalesForClient` en Historia 2.3).
  - [x] Actualizar `data/local/SumitrackDatabase.kt` — agregar `ProductEntity::class` y `ProductVariantEntity::class` a `entities`, subir `version` de `3` a `4`, agregar `abstract fun productDao(): ProductDao` y `abstract fun productVariantDao(): ProductVariantDao`.
  - [x] Actualizar `data/local/Migrations.kt` — agregar `MIGRATION_3_4` (mismo estilo que `MIGRATION_2_3`, dos `CREATE TABLE IF NOT EXISTS` — `products` y `product_variants` — con las columnas exactas de ambas entidades) y agregarla a `ALL`.
  - [x] Actualizar `di/DatabaseModule.kt` — agregar `provideProductDao(db: SumitrackDatabase): ProductDao = db.productDao()` y `provideProductVariantDao(db: SumitrackDatabase): ProductVariantDao = db.productVariantDao()`.

- [x] **T3: `ProductRepository`** (AC-2, AC-3, AC-4)
  - [x] Crear `data/repositories/ProductRepository.kt` — mismo estilo que `ClientRepository.kt`:
    ```kotlin
    @Singleton
    class ProductRepository @Inject constructor(
        private val productDao: ProductDao,
        private val productVariantDao: ProductVariantDao,
    ) {
        fun getAllProducts(): Flow<List<Product>> =
            productDao.getAllAsFlow().map { entities -> entities.map { it.toDomain() } }

        suspend fun getProductById(id: String): Product? = productDao.getById(id)?.toDomain()

        suspend fun getVariantsForProduct(productId: String): List<ProductVariant> =
            productVariantDao.getForProduct(productId).map { it.toDomain() }

        suspend fun createProduct(
            name: String,
            price: BigDecimal,
            taxRate: BigDecimal,
            variantNames: List<String>,
            fkTenant: String,
        ): String {
            val now = Instant.now()
            val product = ProductEntity(
                id = UUID.randomUUID().toString(),
                fkTenant = fkTenant,
                name = name,
                price = price,
                taxRate = taxRate,
                isActive = true,
                createdAt = now,
                updatedAt = now,
                syncStatus = "pending",
            )
            productDao.upsertAll(listOf(product))
            insertVariants(product.id, fkTenant, variantNames)
            return product.id
        }

        suspend fun updateProduct(
            id: String,
            name: String,
            price: BigDecimal,
            taxRate: BigDecimal,
            isActive: Boolean,
            variantNames: List<String>,
        ): Boolean {
            val existing = productDao.getById(id) ?: return false
            productDao.upsertAll(
                listOf(
                    existing.copy(
                        name = name,
                        price = price,
                        taxRate = taxRate,
                        isActive = isActive,
                        updatedAt = Instant.now(),
                        syncStatus = "pending",
                    )
                )
            )
            // Reemplazo total de variantes — ver Dev Notes § Por qué "reemplazar todo" y no diffing.
            productVariantDao.deleteAllForProduct(id)
            insertVariants(id, existing.fkTenant, variantNames)
            return true
        }

        private suspend fun insertVariants(productId: String, fkTenant: String, names: List<String>) {
            if (names.isEmpty()) return
            val now = Instant.now()
            val entities = names.map { variantName ->
                ProductVariantEntity(
                    id = UUID.randomUUID().toString(),
                    fkTenant = fkTenant,
                    fkProduct = productId,
                    name = variantName,
                    createdAt = now,
                    updatedAt = now,
                    syncStatus = "pending",
                )
            }
            productVariantDao.upsertAll(entities)
        }

        private fun ProductEntity.toDomain() = Product(
            id = id, fkTenant = fkTenant, name = name, price = price, taxRate = taxRate,
            isActive = isActive, createdAt = createdAt, updatedAt = updatedAt,
            syncStatus = SyncStatus.fromString(syncStatus),
        )

        private fun ProductVariantEntity.toDomain() = ProductVariant(
            id = id, fkTenant = fkTenant, fkProduct = fkProduct, name = name,
            createdAt = createdAt, updatedAt = updatedAt, syncStatus = SyncStatus.fromString(syncStatus),
        )
    }
    ```

### Android — UI

- [x] **T4: `ProductCard.kt`** (AC-1) — nuevo componente, mismo estilo que `ClientCard.kt`
  - [x] Crear `ui/components/ProductCard.kt`: nombre (`bodyLarge`), precio formateado `$XX.XX` (`titleMedium`/`primary`, misma fórmula `setScale(2, RoundingMode.HALF_UP).toPlainString()` de `ClientCard`/`ClientProfileScreen`). Si `!product.isActive`, mostrar etiqueta pequeña "Inactivo" (`labelLarge`, `colorScheme.onSurfaceVariant`) junto al nombre — es la única señal visual de que un producto está desactivado en esta lista (ver AC-1 y Dev Notes § Decisión de alcance). `ElevatedCard` con `onClick`, `elevation = 1.dp`, `semantics(mergeDescendants = true)` — mismo patrón que `ClientCard`.

- [x] **T5: `ProductListViewModel` + `ProductListScreen`** (AC-1)
  - [x] Crear `ui/screens/products/ProductListViewModel.kt` — expone `products: StateFlow<List<Product>>` desde `productRepository.getAllProducts().stateIn(...)`, mismo patrón que la parte no-búsqueda de `ClientListViewModel` (sin `SearchBar`/`debounce`/`flatMapLatest` — AC-1 no pide búsqueda ni filtros en esta historia).
  - [x] Crear `ui/screens/products/ProductListScreen.kt` — `Scaffold` con `TopAppBar` (`title = "Catálogo de productos"`, `navigationIcon` back `Icons.AutoMirrored.Filled.ArrowBack`, mismo patrón que `ClientProfileScreen` — esta pantalla se alcanza navegando desde S-14, no es un tab de `NavigationBar`), `floatingActionButton` `ExtendedFloatingActionButton` "+" (`contentDescription = "Agregar producto"`, mismo patrón que `ClientListScreen`). Si `products.isEmpty()` → `EmptyState` con ícono `Icons.Outlined.Inventory2` y mensaje "Aún no hay productos en el catálogo. Toca + para agregar el primero." (mismo tono que `UX-DR16`, pero **no reutilizar textualmente** el mensaje de S-04 "Agrégalos en Configuración." — esta historia YA ES Configuración). Si no está vacío → `LazyColumn` de `ProductCard`, `onClick` navega a edición.

- [x] **T6: `ProductFormViewModel`** (AC-2, AC-3, AC-4)
  - [x] Crear `ui/screens/products/ProductFormViewModel.kt` — mismo esqueleto que `ClientFormViewModel` (recibe `productId` opcional vía `SavedStateHandle`, normaliza `""` a `null` — modo alta):
    ```kotlin
    data class ProductFormUiState(
        val isEditMode: Boolean = false,
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val name: String = "",
        val price: String = "",
        val taxRate: String = "",
        val isActive: Boolean = true,
        val variantNames: List<String> = emptyList(),
        val newVariantName: String = "",
        val nameError: Boolean = false,
        val priceError: Boolean = false,
        val errorMessage: String? = null,
    ) {
        val isSaveEnabled: Boolean get() = name.isNotBlank() && price.isNotBlank() && !isSaving
    }
    ```
    Variantes se mantienen como una lista en memoria (`variantNames: List<String>`) en el estado del formulario, NO se persisten hasta tocar "Guardar" — ver Dev Notes § Por qué las variantes no se persisten al agregarlas. `newVariantName` es el texto del campo temporal antes de tocar "Agregar variante".
  - [x] `init`: si `productId != null`, carga el producto (`productRepository.getProductById`) y sus variantes (`productRepository.getVariantsForProduct`) en paralelo o secuencial simple; puebla `name`, `price = product.price.toPlainString()`, `taxRate = product.taxRate.toPlainString()`, `isActive = product.isActive`, `variantNames = variants.map { it.name }`. Mismo manejo de error que `ClientProfileViewModel`/`ClientFormViewModel` (`runCatching` + `errorMessage` si el producto no existe).
  - [x] `onNameChange`/`onPriceChange`/`onTaxRateChange`/`onActiveToggle`/`onNewVariantNameChange` — mismo patrón que `ClientFormViewModel.onXChange` (limpian el error correspondiente + `errorMessage`).
  - [x] `onAddVariantClick()`: si `newVariantName.trim()` no está vacío, agrega el valor recortado a `variantNames` y limpia `newVariantName`. Sin validación de duplicados (no lo exige ningún AC).
  - [x] `onRemoveVariantClick(index: Int)`: quita el elemento de `variantNames` en esa posición.
  - [x] `onSaveClick()`:
    - Valida `name`/`price` no vacíos (mismo patrón `nameError`/`priceError` que `ClientFormViewModel`).
    - Parsea `price` a `BigDecimal` con `runCatching { BigDecimal(state.price.trim()) }` — si falla o es negativo, `priceError = true` + `errorMessage = "Ingresa un precio válido"`.
    - Parsea `taxRate`: si está en blanco, usa `BigDecimal.ZERO` (impuesto opcional, ej. productos exentos); si no está en blanco y no parsea a `BigDecimal` válido ≥ 0, `errorMessage = "Ingresa un impuesto válido"` (sin campo de error dedicado — reutiliza `errorMessage` genérico, ya que AC-2 no exige un impuesto obligatorio, solo un valor plausible cuando se captura algo).
    - Modo alta: requiere `fkTenant` (mismo patrón `@TenantId Flow<String?>` + `SessionModule.kt` que `ClientFormViewModel`, **reutilizar el qualifier `TenantId` existente**, no crear uno nuevo) → `productRepository.createProduct(...)`.
    - Modo edición: `productRepository.updateProduct(...)`, maneja `false` (producto ya no existe) igual que `ClientFormViewModel.onSaveClick` maneja `updateClient` → `false`.
    - Envía `_navEvent` (mismo canal `Channel<Unit>(Channel.CONFLATED)` que `ClientFormViewModel`) al terminar con éxito.

- [x] **T7: `ProductFormScreen`** (AC-2, AC-3, AC-4)
  - [x] Crear `ui/screens/products/ProductFormScreen.kt` — mismo esqueleto que `ClientFormScreen` (`Scaffold` + `TopAppBar` con back que llama `onCancel`, deshabilitado mientras `isSaving`; `BackHandler(enabled = !uiState.isSaving)`; `Column` con `verticalScroll` + `imePadding`; botón "Guardar" al fondo con `CircularProgressIndicator` mientras `isSaving`).
  - [x] Campos, en orden: `OutlinedTextField` nombre (`isError = nameError`, `ImeAction.Next`) → `OutlinedTextField` precio (`keyboardType = KeyboardType.Decimal`, `isError = priceError`, `ImeAction.Next`) → `OutlinedTextField` impuesto % (`keyboardType = KeyboardType.Decimal`, `ImeAction.Done`, `label = "Impuesto % (opcional)"`) → `Row` con `Switch` + `Text("Activo")` (solo tiene efecto visible en modo edición; en modo alta siempre `true` y deshabilitado, ya que un producto recién creado no tiene razón de nacer inactivo — no hay AC que lo exija).
  - [x] Sección "Variantes" (AC-3): `OutlinedTextField` (`newVariantName`) + `IconButton`/`Button` "Agregar" al lado (deshabilitado si el campo está en blanco) que llama `onAddVariantClick`. Debajo, cada variante de `variantNames` en un `Row` simple: `Text(name)` + `IconButton` con `Icons.Filled.Close` (`contentDescription = "Quitar variante"`) que llama `onRemoveVariantClick(index)`. Sin `LazyColumn` (lista corta, ya está dentro de la `Column` con scroll del formulario — mismo criterio que la lista de ventas abiertas en `ClientProfileScreen`, Historia 2.3).
  - [x] Mensaje de error genérico (`errorMessage`) igual que `ClientFormScreen` (solo se muestra si no hay error específico de campo ya visible vía `supportingText`).

### Android — Navegación

- [x] **T8: Rutas y wiring** (AC-1)
  - [x] Actualizar `ui/navigation/Routes.kt` — agregar:
    ```kotlin
    object ProductList : Routes("product_list")

    object ProductForm : Routes("product_form?productId={productId}") {
        fun createRoute(productId: String? = null): String =
            if (productId != null) "product_form?productId=$productId" else "product_form"
    }
    ```
    `ProductForm` replica exactamente el patrón de `ClientForm` (query param opcional para alta/edición en la misma ruta).
  - [x] Actualizar `ui/navigation/NavGraph.kt` — agregar los dos composables (mismo patrón que `ClientForm`/`ClientProfile`):
    ```kotlin
    composable(Routes.ProductList.route) {
        ProductListScreen(
            onBackClick = { navController.popBackStack() },
            onAddProductClick = {
                navController.navigate(Routes.ProductForm.createRoute()) { launchSingleTop = true }
            },
            onProductClick = { productId ->
                navController.navigate(Routes.ProductForm.createRoute(productId)) { launchSingleTop = true }
            },
        )
    }
    composable(
        route = Routes.ProductForm.route,
        arguments = listOf(navArgument("productId") { type = NavType.StringType; nullable = true }),
    ) {
        ProductFormScreen(
            onSaved = { navController.popBackStack() },
            onCancel = { navController.popBackStack() },
        )
    }
    ```
  - [x] Actualizar `ui/screens/settings/SettingsScreen.kt` — agregar parámetro `onCatalogClick: () -> Unit = {}`; insertar un elemento navegable (`ListItem` o `TextButton`/`Row` clickeable, estilo consistente con el resto de S-14: `bodyLarge`, ícono `Icons.Outlined.Inventory2`) con texto "Catálogo de productos" entre el título "Configuración" y el `HorizontalDivider` existente, o inmediatamente después del divider — colocarlo antes del botón "Cerrar sesión" (que debe seguir siendo la última acción, es destructiva). **No** tocar `SettingsViewModel.kt` — la navegación se maneja igual que `ClientListScreen.onAddClientClick`, vía callback desde `NavGraph`, sin lógica en el ViewModel.
  - [x] Actualizar `NavGraph.kt` composable de `Routes.Settings.route` para pasar `onCatalogClick = { navController.navigate(Routes.ProductList.route) { launchSingleTop = true } }` a `SettingsScreen`.

### Review Findings

- [x] [Review][Patch] Impuesto (%) sin cota superior — `ProductFormViewModel.onSaveClick` aceptaba cualquier `taxRate ≥ 0`, incluyendo valores como `9999`. Decisión confirmada por Josemtz: acotar a `0-100%`. **Resuelto:** `parseBoundedDecimal` + tope `MAX_TAX_RATE = BigDecimal(100)`; mensaje "Ingresa un impuesto válido (0-100%)". [ProductFormViewModel.kt]
- [x] [Review][Patch] Falta aislamiento por tenant en toda la cadena de Producto — `ProductDao.getAllAsFlow()`/`getById()` y `ProductVariantDao.getForProduct()` no filtraban por `fk_tenant`; `ProductRepository.updateProduct()` tampoco verificaba que el producto perteneciera al tenant de la sesión. Mismo patrón que se corrigió en Historia 2.3 para `SaleDao`/`SaleRepository`. **Resuelto:** los 3 DAOs/repositorio ahora reciben `tenantId` y filtran `AND fk_tenant = :tenantId`; `ProductListViewModel`/`ProductFormViewModel` lo resuelven vía el qualifier `TenantId` existente. Tests de aislamiento agregados en `ProductRepositoryTest`/`ProductListViewModelTest`/`ProductFormViewModelTest`. [ProductDao.kt, ProductVariantDao.kt, ProductRepository.kt, ProductListViewModel.kt, ProductFormViewModel.kt]
- [x] [Review][Patch] `ProductRepository.createProduct` no era atómico — `upsertAll` + `insertVariants` en dos escrituras Room separadas sin transacción. **Resuelto:** nuevo `TransactionRunner` (abstrae `SumitrackDatabase.withTransaction` para mantener testeabilidad JVM pura sin Robolectric, mismo criterio que el qualifier `TenantId`); ambas escrituras envueltas en `transactionRunner.run { ... }`. [ProductRepository.kt, TransactionRunner.kt]
- [x] [Review][Patch] `ProductRepository.updateProduct` no era atómico — `deleteAllForProduct` + `insertVariants` sin transacción, riesgo de pérdida de variantes si fallaba a medio camino. **Resuelto:** mismo `transactionRunner.run { ... }` que `createProduct`. [ProductRepository.kt]
- [x] [Review][Patch] Precio/impuesto sin cota de precisión/escala — aceptaban notación científica y valores con más de 6 decimales o 18 dígitos totales, contradiciendo AC-2 (`NUMERIC(18,6)`). **Resuelto:** `parseBoundedDecimal` rechaza `'e'/'E'`, `scale() > 6` y `precision() > 18`. [ProductFormViewModel.kt]
- [x] [Review][Patch] Repositorio sin validación propia (defensa en profundidad) — `createProduct`/`updateProduct` confiaban ciegamente en datos ya validados por el ViewModel. **Resuelto:** `require(name.isNotBlank())`/`require(price >= BigDecimal.ZERO)` en el repositorio; `insertVariants` ahora recorta, descarta blancos y deduplica (`distinct()`) antes de persistir. [ProductRepository.kt]
- [x] [Review][Patch] `ProductFormUiState.isSaveEnabled` evalúa `name`/`price` sin `trim()` — **verificado, sin defecto real:** `String.isNotBlank()` de Kotlin ya excluye cadenas de solo espacios (`"   ".isNotBlank() == false`), así que el botón "Guardar" ya se deshabilita correctamente ante nombre/precio en blanco. El hallazgo del Blind Hunter era un falso positivo; no se modificó código.
- [x] [Review][Patch] `ProductListViewModelTest` — el caso "includes active and inactive products" solo verificaba `size == 2`. **Resuelto:** reescrito para asertar explícitamente `isActive` de cada producto, más nuevos casos de aislamiento por tenant. [ProductListViewModelTest.kt]
- [x] [Review][Patch] Registrar en `deferred-work.md` los hallazgos que la historia ya anticipaba documentar. **Resuelto:** 4 entradas agregadas bajo `## Deferred from: code review de 2-4-catalogo-de-productos-y-variantes (2026-07-19)`. [deferred-work.md]
- [x] [Review][Patch] Dev Agent Record con conteos de test incorrectos por archivo y headline "AC-4 ✅" que sobrestimaba cobertura. **Resuelto:** ver Change Log/Completion Notes actualizados abajo con los conteos reales post-patches y el headline de AC-4 corregido.
- [x] [Review][Defer] `.catch { emit(emptyList()) }` en `ProductListViewModel` traga cualquier error de Flow/DB silenciosamente, indistinguible de un catálogo genuinamente vacío. Patrón preexistente idéntico en `ClientListViewModel` desde Historia 2.1, no introducido por esta historia. [ProductListViewModel.kt] — deferred, pre-existing
- [x] [Review][Defer] `ProductListScreen` no tiene estado de carga inicial — puede mostrar el empty state brevemente antes de la primera emisión del `Flow`. Mismo gap preexistente en `ClientListScreen` desde Historia 2.1. [ProductListScreen.kt] — deferred, pre-existing

## Dev Notes

### Decisión de alcance: la lista de catálogo (AC-1) muestra productos activos e inactivos

El texto literal de AC-1 dice "lista todos los productos activos". Sin embargo, FR-11 (`epics.md` línea 40, título "Activar / desactivar Producto") es explícito en que la acción es reversible en ambos sentidos. Si la pantalla de gestión del catálogo solo mostrara productos activos, un producto desactivado desaparecería permanentemente de la UI sin ninguna forma de reactivarlo — contradice el propio nombre de FR-11. Se resuelve mostrando TODOS los productos (activos e inactivos) en `ProductListScreen`, con una etiqueta "Inactivo" en `ProductCard` para los que no están activos (T4). Esto no viola AC-1 (los productos activos siguen listados con nombre y precio) y hace que "activar" sea alcanzable. **Si se prefiere el comportamiento literal de AC-1** (ocultar inactivos también en la gestión), la alternativa requeriría diseñar por separado cómo reactivar un producto — no se toma esa alternativa aquí porque dejaría un flujo roto sin resolución. Confirmar este enfoque si se prefiere lo contrario antes de correr `dev-story`.

### Por qué las variantes no se persisten al agregarlas, sino hasta "Guardar"

AC-3 dice "cada variante se persiste... con su propio UUID", que podría leerse como persistencia inmediata por cada acción de administrar. Se optó por mantenerlas en memoria (`variantNames` en el `UiState`) y persistir todo junto (producto + variantes) al tocar "Guardar", por consistencia con el único patrón de formulario que ya existe en el proyecto (`ClientFormScreen`/`ClientFormViewModel`, Historia 2.2): nada se escribe en Room hasta el guardado explícito. La alternativa (persistir cada variante al tocarse "Agregar") crearía filas huérfanas de `ProductVariantEntity` si el proveedor abandona el formulario de alta sin guardar el producto padre (no hay `@ForeignKey` que lo prevenga, pero sí sería un dato basura persistente). AC-3 se sigue cumpliendo: al guardar, cada variante SÍ se persiste como `ProductVariantEntity` con su propio UUID.

### Por qué "reemplazar todo" el set de variantes en edición, y no hacer diffing

`ProductRepository.updateProduct` borra todas las variantes existentes del producto (`deleteAllForProduct`) y reinserta el set completo recibido del formulario, en vez de calcular qué variantes son nuevas/eliminadas/sin cambios. Es la opción más simple dado que el proyecto no tiene todavía motor de sincronización (Epic 4): el costo de este enfoque es que una variante que el proveedor no tocó igual se le asigna un `id`/`created_at` nuevos y `sync_status = pending` en cada edición del producto — churn innecesario una vez que exista sync. **Deferred** (agregar a `deferred-work.md` en el code review de esta historia): cuando Epic 4 exista, evaluar si este reemplazo total genera tráfico de sync excesivo y si vale la pena un diffing real por variante.

### Ubicación en el árbol de navegación

`architecture.md` (líneas 372-374) solo anticipa `ui/screens/settings/SettingsScreen.kt` para S-14, sin pantallas propias de catálogo — el árbol de archivos no llegó a este nivel de detalle para Historia 2.4. Se crea un paquete nuevo `ui/screens/products/` (mismo nivel que `ui/screens/clients/`) con `ProductListScreen.kt`/`ProductListViewModel.kt`/`ProductFormScreen.kt`/`ProductFormViewModel.kt`, siguiendo el mismo patrón de organización por feature que ya usa `clients/`. `ProductListScreen` NO es un tab de `NavigationBar` (`UX-DR9` define solo 3 tabs: Órdenes, Clientes, Config) — se alcanza navegando desde dentro de `SettingsScreen`, como una pantalla hija de Configuración (mismo patrón de "pantalla hija alcanzada por navegación, no por tab" que ya usan `ClientForm`/`ClientProfile` respecto de `Clients`).

### Impuesto (%) — por qué `BigDecimal` y no `Int`/`Double`

AC-2 no exige explícitamente `BigDecimal` para el impuesto (solo lo exige para el precio), pero un porcentaje configurable (`ej. IVA 16%`, `FR-10`) puede tener decimales en otros contextos fiscales (ej. tasas fraccionarias). Usar `BigDecimal` consistente con `price` evita mezclar tipos numéricos en la misma entidad y reutiliza el `BigDecimalConverter` ya registrado — no requiere justificación adicional de AR-17 porque no es un monto monetario, es una decisión de consistencia interna.

### Archivos existentes relevantes — estado actual y cambio en esta historia

| Archivo | Estado actual | Cambio en esta historia |
|---------|---------------|--------------------------|
| `SettingsScreen.kt` / `SettingsViewModel.kt` | S-14 con secciones "Configuración" (placeholder) + botón "Cerrar sesión" (Historia 1.4) | + entrada de navegación "Catálogo de productos"; **sin cambios** en `SettingsViewModel.kt` |
| `Routes.kt` | 6 rutas (`Login`, `Orders`, `Clients`, `Settings`, `ClientForm`, `ClientProfile`) | + `ProductList`, `ProductForm` |
| `NavGraph.kt` | Sin rutas de catálogo | + composables `ProductList`/`ProductForm`; `SettingsScreen` recibe `onCatalogClick` |
| `SumitrackDatabase.kt` | `version = 3`, entidades `[SettingsEntity, ClientEntity, SaleEntity]` | `version = 4`, + `ProductEntity`, `ProductVariantEntity`, + `productDao()`, `productVariantDao()` |
| `Migrations.kt` | `MIGRATION_1_2`, `MIGRATION_2_3` | + `MIGRATION_3_4` |
| `DatabaseModule.kt` | Provee `SettingsDao`, `ClientDao`, `SaleDao` | + `provideProductDao`, `provideProductVariantDao` |
| `SessionModule.kt` (qualifier `TenantId`) | Usado por `ClientFormViewModel` | Reutilizado tal cual por `ProductFormViewModel` — **no** crear un qualifier nuevo |

**NO tocar:**
- `ClientRepository.kt`, `ClientDao.kt`, `SaleRepository.kt`, `SaleDao.kt`, `CalculateClientBalanceUseCase.kt` — sin relación con esta historia.
- `ClientCard.kt`, `ClientListScreen.kt`, `ClientFormScreen.kt`, `ClientProfileScreen.kt` — solo se usan como referencia de patrón, no se modifican.
- `EmptyState.kt`, `SyncIcon.kt` — se reutilizan tal cual.
- `OrderListScreen.kt` (S-02) — sigue siendo placeholder de Epic 3.
- `SettingsRepository.kt`/`SessionManager.kt` — sin relación con catálogo de productos.

### Testing

Mismo patrón establecido en Historias 2.1-2.3: **sin Robolectric**, tests JVM puros con Fake DAOs, correr con el JDK de Android Studio (`./gradlew :app:testDebugUnitTest`).

- **`FakeProductDao`** (nuevo, `test/java/.../ui/screens/products/FakeProductDao.kt`) — implementa `ProductDao` con una `MutableList<ProductEntity>` interna; `upsertAll` hace upsert real por `id` (no repetir el bug original de `FakeClientDao.upsertAll` de Historia 2.1, ya corregido en 2.2).
- **`FakeProductVariantDao`** (nuevo, mismo paquete) — implementa `ProductVariantDao` con una `MutableList<ProductVariantEntity>` interna; `getForProduct` filtra por `fkProduct`; `deleteAllForProduct` remueve todas las filas con ese `fkProduct`.
- **`ProductRepositoryTest`** (nuevo, `test/java/.../data/repositories/`) — con ambos fakes: `createProduct` persiste producto + variantes con UUIDs propios (AC-2, AC-3); `updateProduct` reemplaza el set de variantes (variantes viejas ya no están, nuevas sí); `updateProduct` sobre un `id` inexistente retorna `false`; `getAllProducts` incluye productos inactivos (AC-1); toggle `isActive` persiste correctamente (AC-4).
- **`ProductFormViewModelTest`** (nuevo, `test/java/.../ui/screens/products/`) — casos: alta válida navega (evento en `navEvent`); nombre/precio vacío → error de campo, no guarda; precio no numérico → `priceError`; `onAddVariantClick`/`onRemoveVariantClick` mutan `variantNames` en memoria sin llamar al repositorio; edición carga producto + variantes existentes en el estado inicial; edición de producto inexistente → `errorMessage` sin crash.
- **`ProductListViewModelTest`** (nuevo, mismo paquete) — `products` refleja lo emitido por `productRepository.getAllProducts()` (activos e inactivos incluidos).
- Sin test de Composable/UI — mismo criterio que historias previas (sin infraestructura de test de Composables en el proyecto).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Historia 2.4: Catálogo de Productos y Variantes] (líneas 448-472) — AC originales
- [Source: _bmad-output/planning-artifacts/epics.md#Requirements Inventory] FR-10 (alta/edición de producto + variantes, línea 38), FR-11 (activar/desactivar, línea 40), AR-6 (campos obligatorios de entidad sincronizable), AR-17 (precisión monetaria), AR-18 (`ProductVariantEntity` confirmada post-validación, línea 123)
- [Source: _bmad-output/planning-artifacts/architecture/architecture.md] árbol Android (líneas 370-455): `Product.kt`/`ProductRepository.kt`/`ProductDao.kt`/`ProductEntity.kt`/`ProductDto.kt` ya anticipados en la estructura, `SettingsScreen.kt` como única pantalla prevista para S-14 (sin desglose de catálogo)
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md#S-14 — Configuración] (línea 100-101) — "Catálogo de Productos" como sección dentro de S-14; sin mockup ni spec visual dedicados (gap de UX, resuelto por analogía con `ClientListScreen`/`ClientFormScreen`)
- [Source: _bmad-output/planning-artifacts/epics.md#UX-DR16] empty state de S-04 (no reutilizar textualmente, es de otra pantalla); [Source: epics.md#UX-DR9] `NavigationBar` de 3 tabs (catálogo no es un tab)
- [Source: _bmad-output/implementation-artifacts/2-3-perfil-de-cliente-con-saldo-y-ordenes-abiertas.md] patrones reutilizados: esquema mínimo de entidad nueva con migración propia, `toDomain()` privado en repository, convención de testing sin Robolectric
- [Source: _bmad-output/implementation-artifacts/2-2-alta-y-edicion-de-cliente.md] patrón de formulario completo: `SavedStateHandle` opcional para alta/edición, qualifier `TenantId`, guardado atómico al tocar "Guardar", canal `navEvent`
- [Source: android/app/src/main/java/com/sumitrack/android/data/repositories/ClientRepository.kt, ClientFormViewModel.kt, ClientFormScreen.kt, ClientListScreen.kt, ClientListViewModel.kt] — patrones de código fuente a replicar
- [Source: android/app/src/main/java/com/sumitrack/android/ui/screens/settings/SettingsScreen.kt, SettingsViewModel.kt] — estado actual de S-14 antes de esta historia

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (`claude-sonnet-5`)

### Debug Log References

Sin desviaciones de diseño respecto a lo especificado en Tasks/Subtasks y Dev Notes — la historia ya dejó las decisiones de alcance resueltas por adelantado (lista con activos+inactivos, variantes en memoria hasta "Guardar", reemplazo total de variantes en edición), así que la implementación las siguió tal cual sin ajustes durante el desarrollo.

### Completion Notes List

Historia implementada completa + code review con 1 decisión resuelta y 10 patches aplicados. 109 tests ✅ (0 fallos, +37 sobre Historia 2.3). `BUILD SUCCESSFUL` (`assembleDebug` + `testDebugUnitTest`, JDK de Android Studio).

- AC-1 ✅: `ProductListScreen` (dentro de S-14 › Catálogo) lista productos con nombre y precio, filtrados por tenant de sesión; botón "+" navega a alta. Incluye productos activos e inactivos (decisión de alcance documentada en Dev Notes) con etiqueta "Inactivo" en `ProductCard` para los desactivados.
- AC-2 ✅: alta/edición persiste nombre, precio (`BigDecimal`/`NUMERIC(18,6)` vía `BigDecimalConverter`, acotado a escala ≤6 y precisión ≤18 tras el code review), impuesto % (`BigDecimal`, 0-100%, opcional → `BigDecimal.ZERO` si se deja en blanco) y estado activo/inactivo; UUID generado en cliente (`UUID.randomUUID()`); `sync_status = "pending"` en alta y edición; escritura atómica (producto + variantes) vía `TransactionRunner`.
- AC-3 ✅: variantes administradas en el formulario (agregar/quitar, en memoria) se persisten como `ProductVariantEntity` con su propio UUID al guardar el producto, recortadas/deduplicadas en el repositorio. Cubierto en `ProductRepositoryTest` (variantes con IDs propios, sanitización) y `ProductFormViewModelTest` (mutación en memoria sin tocar el repositorio hasta "Guardar").
- AC-4 **parcial** — el switch "Activo" en modo edición persiste el toggle vía `ProductRepository.updateProduct(isActive=...)`, con aislamiento por tenant verificado. **La desaparición del producto desactivado en S-04 (nueva venta) no está implementada en esta historia** — es explícitamente fuera de alcance (Epic 3, `OrderListScreen`/flujo de nueva venta no existen todavía) y así queda documentado en la sección "Fuera de alcance". El dato queda correctamente marcado `isActive = false` para que Historia 3.2 lo consuma con su propia query filtrada.
- Reemplazo total de variantes en edición (`deleteAllForProduct` + reinserción) implementado tal como se documentó en Dev Notes, envuelto en transacción tras el code review; trade-off de churn de `sync_status` diferido a Epic 4 (registrado en `deferred-work.md`).
- Migración Room `MIGRATION_3_4` aplicada (`version` 3→4, tablas `products` y `product_variants`); `app/schemas/.../4.json` generado automáticamente por KSP al compilar.
- Entrada de navegación "Catálogo de productos" agregada a `SettingsScreen` (S-14) sin modificar `SettingsViewModel.kt`, tal como especificaba T8.
- **Code review** (2026-07-19): 3 agentes en paralelo (Blind Hunter, Edge Case Hunter, Acceptance Auditor) → 1 decisión resuelta (impuesto acotado a 0-100%, confirmado por Josemtz) + 10 patches aplicados (aislamiento por tenant en toda la cadena de Producto — mismo patrón que Historia 2.3 para `Sale` —, transacciones atómicas vía nuevo `TransactionRunner`, cotas de precisión/escala en precio e impuesto, validación de defensa en profundidad en el repositorio, test reforzado, correcciones de documentación). 2 hallazgos descartados por coincidir con patrones ya deliberados del proyecto (TEXT vs NUMERIC en SQLite per AR-17; duplicación de `formatPrice` per precedente de `ClientCard`). 4 hallazgos diferidos a `deferred-work.md`.
- Sin test de Composable/UI (`ProductListScreen`/`ProductFormScreen`) — mismo criterio que historias previas, el proyecto no tiene infraestructura de test de Composables.
- **Pendiente:** verificación manual en emulador/dispositivo físico — este entorno no tiene `adb` ni emulador Android disponible (mismo pendiente que en Historias 2.1-2.3). Recomendado antes de mergear: Configuración → Catálogo de productos → "+" → crear producto con 2 variantes → confirmar que aparece en la lista → tocarlo → editar (cambiar nombre, quitar una variante, agregar otra, desactivar) → guardar → confirmar que los cambios persisten al reabrir.

### File List

**Archivos creados (NEW):**
- `android/app/src/main/java/com/sumitrack/android/domain/models/Product.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/models/ProductVariant.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/entities/ProductEntity.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/entities/ProductVariantEntity.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/ProductDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/ProductVariantDao.kt`
- `android/app/src/main/java/com/sumitrack/android/data/repositories/ProductRepository.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/components/ProductCard.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/products/ProductListViewModel.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/products/ProductListScreen.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/products/ProductFormViewModel.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/products/ProductFormScreen.kt`
- `android/app/schemas/com.sumitrack.android.data.local.SumitrackDatabase/4.json` (autogenerado por KSP)
- `android/app/src/main/java/com/sumitrack/android/data/local/TransactionRunner.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/products/FakeProductDao.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/products/FakeProductVariantDao.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/products/FakeTransactionRunner.kt`
- `android/app/src/test/java/com/sumitrack/android/data/repositories/ProductRepositoryTest.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/products/ProductListViewModelTest.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/products/ProductFormViewModelTest.kt`

**Archivos modificados (UPDATE):**
- `android/app/src/main/java/com/sumitrack/android/data/local/SumitrackDatabase.kt` — `version` 3→4, + `ProductEntity`, `ProductVariantEntity`, + `productDao()`, `productVariantDao()`
- `android/app/src/main/java/com/sumitrack/android/data/local/Migrations.kt` — + `MIGRATION_3_4`
- `android/app/src/main/java/com/sumitrack/android/di/DatabaseModule.kt` — + `provideProductDao`, `provideProductVariantDao`, `provideTransactionRunner`
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/Routes.kt` — + `ProductList`, `ProductForm`
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/NavGraph.kt` — + composables `ProductList`/`ProductForm`; `SettingsScreen` recibe `onCatalogClick`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/settings/SettingsScreen.kt` — + entrada de navegación "Catálogo de productos"
- `_bmad-output/implementation-artifacts/deferred-work.md` — 4 hallazgos diferidos agregados

## Change Log

- **2026-07-19** — Historia 2.4 implementada completa (Status: review)
  - NEW: esquema de Producto — `Product`/`ProductVariant` (dominio), `ProductEntity`/`ProductVariantEntity`/`ProductDao`/`ProductVariantDao` (Room), `ProductRepository`; migración `MIGRATION_3_4` (`version` 3→4)
  - NEW: `ProductCard`, `ProductListViewModel`/`ProductListScreen` (lista de catálogo, activos+inactivos), `ProductFormViewModel`/`ProductFormScreen` (alta/edición con gestión de variantes en memoria)
  - UPDATE: `Routes`/`NavGraph` — rutas `product_list`/`product_form`; `SettingsScreen` (S-14) gana entrada de navegación "Catálogo de productos"
  - NEW: tests — `ProductRepositoryTest` (8), `ProductListViewModelTest` (2), `ProductFormViewModelTest` (13), `FakeProductDao`, `FakeProductVariantDao`
  - Build: 95 tests ✅ (0 fallos, +23 sobre Historia 2.3), `BUILD SUCCESSFUL` (`assembleDebug` + `testDebugUnitTest`, JDK de Android Studio)
  - Pendiente: verificación manual en dispositivo/emulador (sin `adb` en este entorno); code review todavía no ejecutado

- **2026-07-19** — Code review: 1 decisión resuelta + 10 patches aplicados (Status: review, sigue en review pendiente de próximos pasos)
  - RESUELTO (decisión): impuesto (%) sin cota superior — confirmado por Josemtz, acotar a 0-100%
  - PATCH: aislamiento por tenant agregado a `ProductDao`/`ProductVariantDao`/`ProductRepository`/`ProductListViewModel`/`ProductFormViewModel` — mismo patrón que Historia 2.3 aplicó a `Sale*`, esta historia no lo había replicado
  - PATCH: nuevo `TransactionRunner` (abstracción testeable de `SumitrackDatabase.withTransaction`) — `createProduct`/`updateProduct` ahora son atómicos
  - PATCH: `parseBoundedDecimal` en `ProductFormViewModel` — rechaza notación científica y valores fuera de `NUMERIC(18,6)`; impuesto acotado a 0-100%
  - PATCH: `ProductRepository.createProduct`/`updateProduct` con `require()` de defensa en profundidad + `insertVariants` recorta/descarta blancos/deduplica
  - PATCH: `ProductListViewModelTest` reforzado (asertaba solo `size`, ahora verifica `isActive` explícitamente) + casos de aislamiento por tenant
  - VERIFICADO SIN CAMBIO: `isSaveEnabled` sin `trim()` — falso positivo, `String.isNotBlank()` de Kotlin ya excluye cadenas de solo espacios
  - Descartados (2): `TEXT` vs `NUMERIC` en SQLite (coincide con AR-17/`ClientEntity`/`SaleEntity`); duplicación de `formatPrice` en `ProductCard` (coincide con precedente deliberado de `ClientCard`/Historia 2.3)
  - Deferred (4, ver `deferred-work.md`): `.catch` silencioso en `ProductListViewModel` (preexistente de `ClientListViewModel`), sin loading state en `ProductListScreen` (preexistente), churn de `sync_status` por reemplazo total de variantes, sin índice en `products`/`product_variants`
  - NEW: tests de aislamiento por tenant y de cotas de precisión/impuesto en `ProductRepositoryTest`/`ProductFormViewModelTest`/`ProductListViewModelTest`; nuevo `FakeTransactionRunner`
  - Build verificado: **109 tests ✅ (0 fallos)**, `BUILD SUCCESSFUL` (`assembleDebug` + `testDebugUnitTest`, JDK de Android Studio)
