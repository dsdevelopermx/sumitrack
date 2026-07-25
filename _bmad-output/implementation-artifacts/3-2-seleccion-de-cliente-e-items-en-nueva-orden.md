---
baseline_commit: 1dfc4c518a6f034d5a0b68365a8d19e493058bd3
---

# Story 3.2: Selección de Cliente e Ítems en Nueva Orden

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero seleccionar un cliente y agregar productos a una nueva orden mientras estoy en campo,
para que pueda iniciar el registro de una venta en menos de 2 minutos.

## Acceptance Criteria

**AC-1 — Búsqueda de cliente (S-03)**

**Dado** que el proveedor toca el FAB "Nueva Orden" en S-02
**Cuando** S-03 se muestra
**Entonces** hay un campo de búsqueda de clientes que filtra en tiempo real desde SQLite; cada resultado muestra nombre y saldo; si el cliente tiene deuda vencida se muestra badge "Vencido" en `status-overdue` con texto visible (ver Dev Notes § Decisión de alcance — el badge "Vencido" no se implementa en esta historia)

**AC-2 — Alta rápida de cliente desde S-03**

**Dado** que el cliente buscado no existe
**Cuando** el proveedor toca "Nuevo cliente"
**Entonces** S-13 se abre en modo alta rápida; al guardar regresa a S-03 con el nuevo cliente pre-seleccionado

**AC-3 — Avance a lista de ítems (S-04)**

**Dado** que el proveedor confirma el cliente y avanza a S-04
**Cuando** S-04 se muestra
**Entonces** lista los productos activos del catálogo con nombre y precio; los productos con variantes muestran chip "Variantes"; hay barra inferior persistente con subtotal acumulado y botón "Revisar Orden"

**AC-4 — Agregar producto sin variantes**

**Dado** que el proveedor toca un producto sin variantes
**Cuando** se registra el toque
**Entonces** el producto se agrega a la orden con cantidad 1; el indicador de contador aparece en el ítem; el subtotal se actualiza

**AC-5 — Agregar producto con variantes (S-05)**

**Dado** que el proveedor toca un producto con variantes
**Cuando** se registra el toque
**Entonces** se abre `VariantSelectorSheet` (S-05) con chips de selección única de variantes y `QuantityStepper`; botón "Agregar" deshabilitado hasta seleccionar variante; al cerrar sin confirmar el foco TalkBack regresa al ítem en S-04

**AC-6 — Abandonar orden con ítems seleccionados**

**Dado** que el proveedor toca Back desde S-04 (o posterior) con ítems seleccionados
**Cuando** se registra la navegación
**Entonces** aparece dialog: "¿Abandonar esta orden? Los ítems seleccionados se perderán." con botones "Sí, salir" / "No, quedarme."

**AC-7 — Catálogo vacío**

**Dado** que el catálogo está vacío
**Cuando** S-04 se muestra
**Entonces** aparece empty state: "Aún no hay productos en el catálogo. Agrégalos en Configuración." con botón "Ir a Configuración"

### Fuera de alcance en esta historia (explícito)

- **Badge "Vencido" en S-03** (AC-1 original, `status-overdue`) — determinar si un cliente tiene deuda **vencida** requiere una fecha de vencimiento por parcialidad (FR-13/FR-14, vive en `Installment`), que **no existe todavía** (Historia 3.3+ crea `Installment`; el registro real de parcialidades con fecha es Historia 3.6). Mismo criterio que Historia 2.3 aplicó al banner de deuda vencida en S-12 y Historia 3.1 al filtro "Atraso": no hay forma honesta de calcular "vencido" en esta historia. No se implementa ningún mecanismo de badge — a diferencia del `FinancialAlertBanner` de Historia 2.3 (que sí se construyó mecánicamente sin datos), aquí no hay AC que exija dejar el mecanismo preparado, así que no se anticipa.
- **S-06 (Resumen de Orden) y todo lo posterior** — Historia 3.3, no existe todavía. El botón "Revisar Orden" en S-04 muestra un Snackbar placeholder ("Resumen de orden — disponible próximamente"), mismo patrón que el FAB de `OrderListScreen` mostraba antes de esta historia.
- **Creación real de una `Sale`** — esta historia solo arma un carrito **en memoria** (`OrderDraftItem`), nunca persiste nada en `sales`. La persistencia real ocurre en Historia 3.3 (al confirmar el pago) o posterior. No crear `SaleRepository.createSale` ni tocar el esquema de `sales`.
- **Edición de cantidad/eliminación de un ítem ya agregado desde S-04** — el AC solo pide que tocar un producto lo agregue (AC-4) o abra el selector de variante (AC-5); no hay AC que pida un mecanismo para reducir/quitar un ítem ya en el carrito desde esta pantalla. Se resuelve en Historia 3.3 (S-06, "Editar" regresa a S-04, pero el detalle de edición de línea es responsabilidad de esa historia).
- **Renombrar/tocar `Installment.kt`/`Payment.kt`** — no crear estos modelos, son de Historia 3.3+.

## Tasks / Subtasks

### Android — Cliente con saldo para búsqueda (S-03), sin heredar el deferred de `ClientDao`

- [x] **T1: `ClientSearchResult` (dominio) y query agregada en `ClientDao`** (AC-1)
  - [x] Crear `domain/models/ClientSearchResult.kt` — proyección **distinta** de `Client` (que no incluye saldo en `getAllClients()`/`searchClients()` a propósito, ver comentario en `ClientRepository.toDomain()` de Historia 2.3):
    ```kotlin
    data class ClientSearchResult(
        val id: String,
        val name: String,
        val phone: String,
        val balance: BigDecimal,
    )
    ```
  - [x] Agregar a `data/local/dao/ClientDao.kt` (**sin tocar** `getAllAsFlow()`/`searchByNameAsFlow()` existentes, que siguen sin scope de tenant — ese deferred de Historia 2.1/2.2 sigue abierto y fuera de alcance de esta historia; ver Dev Notes):
    ```kotlin
    @Query(
        "SELECT c.id AS id, c.name AS name, c.phone AS phone, " +
            "COALESCE(SUM(s.total), 0) AS balance " +
            "FROM clients c " +
            "LEFT JOIN sales s ON s.fk_client = c.id AND s.status IN ('pending', 'partial') " +
            "WHERE c.fk_tenant = :tenantId " +
            "AND (:normalizedQuery = '' OR REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(LOWER(c.name)," +
            "'á','a'),'Á','a'),'é','e'),'É','e'),'í','i'),'Í','i'),'ó','o'),'Ó','o'),'ú','u'),'Ú','u'),'ü','u'),'Ü','u'),'ñ','n'),'Ñ','n') " +
            "LIKE '%' || :normalizedQuery || '%' ESCAPE '\\') " +
            "GROUP BY c.id " +
            "ORDER BY c.name ASC"
    )
    fun searchWithBalanceAsFlow(tenantId: String, normalizedQuery: String): Flow<List<ClientSearchRow>>
    ```
    Agregar `data class ClientSearchRow(val id: String, val name: String, val phone: String, val balance: BigDecimal)` en el mismo archivo (mismo criterio de co-ubicación que `OrderSummaryRow` en `SaleDao.kt`, Historia 3.1).
    **Por qué una query nueva y no reutilizar `CalculateClientBalanceUseCase`:** ese use case calcula el saldo de **un** cliente a la vez (usado por `ClientProfileViewModel`/`ClientRepository.getClientById`); llamarlo por cada fila de una lista de resultados de búsqueda sería el mismo problema N+1 que Historia 2.3 corrigió para `getAllClients()`/`searchClients()`. La agregación `SUM` en una sola query evita el N+1 sin tocar `CalculateClientBalanceUseCase` ni las queries existentes de `ClientDao`.
    **Por qué esta query SÍ nace con scope de tenant (`WHERE c.fk_tenant = :tenantId`) mientras `getAllAsFlow()`/`searchByNameAsFlow()` siguen sin él:** mismo criterio que Historia 3.1 aplicó al JOIN de `SaleDao` — todo código **nuevo** debe nacer aislado por tenant desde el diseño inicial (aprendizaje del code review de Historia 2.4), sin que eso obligue a retrofittear el código viejo en la misma historia (fuera de alcance, sigue como deferred).

- [x] **T2: `ClientRepository.searchClientsWithBalance`** (AC-1)
  - [x] Agregar a `data/repositories/ClientRepository.kt`:
    ```kotlin
    fun searchClientsWithBalance(tenantId: String, query: String): Flow<List<ClientSearchResult>> =
        clientDao.searchWithBalanceAsFlow(tenantId, SearchNormalizer.toLikePattern(query.trim()))
            .map { rows -> rows.map { it.toDomain() } }

    private fun ClientSearchRow.toDomain() = ClientSearchResult(id = id, name = name, phone = phone, balance = balance)
    ```
    `query.trim()` desde el inicio — aprendizaje directo del patch de Historia 3.1 (búsqueda con solo espacios en blanco).

### Android — Catálogo activo + indicador de variantes (S-04)

- [x] **T3: `ProductDao.getActiveAsFlow` y `ProductVariantDao.getProductIdsWithVariants`** (AC-3)
  - [x] Agregar a `data/local/dao/ProductDao.kt`:
    ```kotlin
    @Query("SELECT * FROM products WHERE fk_tenant = :tenantId AND is_active = 1 ORDER BY name ASC")
    fun getActiveAsFlow(tenantId: String): Flow<List<ProductEntity>>
    ```
    Historia 2.4 dejó explícitamente esta query pendiente para "cuando exista el flujo de nueva venta que sí necesita filtrar" (ver Dev Notes de esa historia) — es este momento. **No** tocar `getAllAsFlow()` (sigue usándose tal cual por S-14 › Catálogo, que necesita ver productos activos e inactivos).
  - [x] Agregar a `data/local/dao/ProductVariantDao.kt`:
    ```kotlin
    @Query("SELECT DISTINCT fk_product FROM product_variants WHERE fk_tenant = :tenantId")
    suspend fun getProductIdsWithVariants(tenantId: String): List<String>
    ```
    `suspend` (no `Flow`) — se carga una sola vez junto con el catálogo, mismo criterio que `getVariantsForProduct` de Historia 2.4 (no necesita observarse en tiempo real dentro de esta pantalla).

- [x] **T4: `ProductRepository.getActiveProducts` y `getProductIdsWithVariants`** (AC-3)
  - [x] Agregar a `data/repositories/ProductRepository.kt`:
    ```kotlin
    fun getActiveProducts(tenantId: String): Flow<List<Product>> =
        productDao.getActiveAsFlow(tenantId).map { entities -> entities.map { it.toDomain() } }

    suspend fun getProductIdsWithVariants(tenantId: String): Set<String> =
        productVariantDao.getProductIdsWithVariants(tenantId).toSet()
    ```
    `toDomain()` ya es `private` dentro de la clase — reutilizar tal cual, no duplicar el mapeo.

### Android — Propagar el cliente recién creado desde S-13 de vuelta a S-03

- [x] **T5: `ClientFormViewModel`/`ClientFormScreen` — `navEvent` pasa a emitir el id del cliente** (AC-2)
  - [x] En `ui/screens/clients/ClientFormViewModel.kt`: cambiar `private val _navEvent = Channel<Unit>(Channel.CONFLATED)` a `Channel<String>(Channel.CONFLATED)`. En modo alta (`created.onSuccess { ... _navEvent.send(Unit) }`) cambiar a `_navEvent.send(created.getOrThrow())` — el id ya está disponible como resultado de `createClient(...)`, ajustar la forma en que se captura (`val newId = ...; created.onSuccess { _navEvent.send(newId) }` o equivalente, sin cambiar la lógica de creación). En modo edición (`_navEvent.send(Unit)` tras `updateProduct`... revisar: es `updateClient` en este archivo) cambiar a `_navEvent.send(clientId)` (la variable `clientId` ya existe en el scope de la función, viene de `productId`... **cuidado:** en este archivo la variable se llama `clientId`, no confundir con el patrón de `ProductFormViewModel`).
    **Es un cambio mecánico y no ambiguo**: el canal ahora comunica "qué cliente se guardó", útil tanto para S-11/S-12 (que no lo necesitan y lo ignoran) como para S-03 (que sí lo necesita).
  - [x] En `ui/screens/clients/ClientFormScreen.kt`: cambiar la firma `onSaved: () -> Unit` a `onSaved: (String) -> Unit`; `LaunchedEffect(Unit) { viewModel.navEvent.collect { onSaved() } }` → `viewModel.navEvent.collect { id -> onSaved(id) } }`.
  - [x] En `ui/navigation/NavGraph.kt`: actualizar los **dos** call sites existentes del composable `ClientForm` (llamado desde S-11/FAB→alta, y desde S-12→edición) — ninguno de los dos necesita el id, cambiar `onSaved = { navController.popBackStack() }` a `onSaved = { _ -> navController.popBackStack() }` en ambos. El **tercer** call site (nuevo, desde S-03) sí usa el id — ver T13.

### Android — Modelo de carrito en memoria (sin persistir)

- [x] **T6: `OrderDraftItem` (dominio)** (AC-4, AC-5)
  - [x] Crear `domain/models/OrderDraftItem.kt`:
    ```kotlin
    data class OrderDraftItem(
        val product: Product,
        val variant: ProductVariant?,
        val quantity: Int,
    ) {
        val subtotal: BigDecimal get() = product.price.multiply(BigDecimal(quantity))
    }
    ```
    `variant` es `null` para productos sin variantes o cuando el producto no las requiere. El precio de línea siempre usa `product.price` (las variantes no tienen precio propio — decisión ya tomada en Historia 2.4, "Fuera de alcance": "Precio o impuesto por variante"). No se persiste en Room — vive solo en el estado del ViewModel de S-04 mientras la pantalla está activa.

### Android — Componentes nuevos

- [x] **T7: `QuantityStepper.kt`** (AC-5) — ya anticipado en `architecture.md`, no creado en historias previas
  - [x] Crear `ui/components/QuantityStepper.kt` según `UX-DR11`: dos `IconButton` de 48dp×48dp con círculo visual de 40dp (`Surface` circular, color `primary-variant`), íconos `Icons.Filled.Remove`/`Icons.Filled.Add` en `on-primary`. Valor central en `bodyLarge`/`on-surface`. Botón `−` deshabilitado (color `on-surface-variant`, `onClick` no-op) cuando `quantity == 1`. Sin campo de texto editable (según spec, v1).
    ```kotlin
    @Composable
    fun QuantityStepper(
        quantity: Int,
        onQuantityChange: (Int) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            StepperButton(
                icon = Icons.Filled.Remove,
                contentDescription = "Disminuir cantidad",
                enabled = quantity > 1,
                onClick = { onQuantityChange(quantity - 1) },
            )
            Text(
                text = quantity.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            StepperButton(
                icon = Icons.Filled.Add,
                contentDescription = "Aumentar cantidad",
                enabled = true,
                onClick = { onQuantityChange(quantity + 1) },
            )
        }
    }

    @Composable
    private fun StepperButton(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
        Surface(
            shape = CircleShape,
            color = if (enabled) PrimaryVariant else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(48.dp),
        ) {
            IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
    ```
    Sin límite superior de cantidad — ningún AC lo exige; el `+` no tiene guardia de máximo.

- [x] **T8: `VariantSelectorSheet.kt`** (AC-5)
  - [x] Crear `ui/screens/orders/VariantSelectorSheet.kt` (mismo paquete que `OrderListScreen`, coherente con el árbol de `architecture.md`) — `ModalBottomSheet` de Compose M3, `shape = MaterialTheme.shapes.large` (28dp superior, ya definido en `Shape.kt` para bottom sheets, reutilizar tal cual):
    ```kotlin
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun VariantSelectorSheet(
        product: Product,
        variants: List<ProductVariant>,
        onDismiss: () -> Unit,
        onConfirm: (variant: ProductVariant, quantity: Int) -> Unit,
    ) {
        var selectedVariantId by remember { mutableStateOf<String?>(null) }
        var quantity by remember { mutableIntStateOf(1) }

        ModalBottomSheet(onDismissRequest = onDismiss, shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                FilterChipRow(
                    chips = variants.map { FilterChipData(it.id, it.name) },
                    selectedChip = selectedVariantId,
                    onChipSelected = { selectedVariantId = it },
                )
                Spacer(Modifier.height(16.dp))
                QuantityStepper(quantity = quantity, onQuantityChange = { quantity = it })
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val variant = variants.first { it.id == selectedVariantId }
                        onConfirm(variant, quantity)
                    },
                    enabled = selectedVariantId != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Agregar a la orden")
                }
            }
        }
    }
    ```
    **Reutiliza `FilterChipRow`** (chips de selección única, ya usado con datos reales por primera vez en Historia 3.1) — no crear un selector de chips nuevo. `onDismiss` (swipe down, Back, o tap fuera) **no** modifica el carrito — el llamador (`ItemListScreen`, T12) es responsable de devolver el foco de TalkBack al ítem que abrió el sheet al recibir `onDismiss` (AC-5).

### Android — S-03 Selección de Cliente

- [x] **T9: `ClientSelectViewModel`** (AC-1, AC-2)
  - [x] Crear `ui/screens/orders/ClientSelectViewModel.kt` — mismo esqueleto que `ClientListViewModel` (`debounce(200)` + `flatMapLatest` + `catch{emit(emptyList())}` + `stateIn`), con `@TenantId` (aislamiento desde el diseño inicial) y usando la nueva query de saldo:
    ```kotlin
    @HiltViewModel
    class ClientSelectViewModel @Inject constructor(
        private val clientRepository: ClientRepository,
        @TenantId private val tenantId: Flow<String?>,
    ) : ViewModel() {

        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        val results: StateFlow<List<ClientSearchResult>> = combine(tenantId, _searchQuery.debounce(200)) { t, q -> t to q }
            .flatMapLatest { (tenant, query) ->
                if (tenant.isNullOrBlank()) flowOf(emptyList()) else clientRepository.searchClientsWithBalance(tenant, query)
            }
            .catch { emit(emptyList()) }
            .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = emptyList())

        fun onSearchQueryChange(query: String) { _searchQuery.value = query }
        fun onSearchClear() { _searchQuery.value = "" }
    }
    ```
    Sin filtro de estado (a diferencia de `OrderListViewModel`) — S-03 no tiene `FilterChipRow`, solo búsqueda (AC-1 no lo pide).

- [x] **T10: `ClientSelectScreen`** (AC-1, AC-2, AC-3)
  - [x] Crear `ui/screens/orders/ClientSelectScreen.kt` — `Scaffold` con `TopAppBar` (título "Nueva orden", back estándar `Icons.AutoMirrored.Filled.ArrowBack`), campo de búsqueda **siempre visible** (no colapsable como `SearchBar` de S-02/S-11 — S-03 es una pantalla dedicada, no una lista con tab persistente; usar `OutlinedTextField` simple con `leadingIcon` de búsqueda, más directo para un flujo de una sola tarea).
    - Lista de resultados: cada fila muestra nombre (`bodyLarge`) + saldo formateado (`titleMedium`/`primary`, misma fórmula `"$" + balance.setScale(2, RoundingMode.HALF_UP).toPlainString()` que `ClientCard`). `onClick` de la fila → `onClientSelected(clientId)` (navega directo a S-04, sin paso de confirmación intermedio — mismo patrón de "un toque navega" que usa toda la app: `ClientCard`, `ProductCard`, `OrderCard`).
    - Botón "Nuevo cliente" (visible siempre debajo de los resultados, o cuando la búsqueda no arroja resultados — visible siempre es más simple y consistente con "el cliente buscado no existe" sin depender de detectar exactamente cuándo "no existe"): `onNewClientClick` → navega a `ClientForm` (T13).
    - Sin empty state dedicado más allá de "sin resultados" — no lo exige ningún AC de esta historia.

### Android — S-04 Lista de Ítems (con S-05 embebido como bottom sheet)

- [x] **T11: `ItemListViewModel`** (AC-3, AC-4, AC-5, AC-6, AC-7)
  - [x] Crear `ui/screens/orders/ItemListViewModel.kt` — recibe `clientId` obligatorio vía `SavedStateHandle` (mismo patrón que `ClientProfileViewModel`):
    ```kotlin
    data class ItemListUiState(
        val isLoading: Boolean = true,
        val products: List<Product> = emptyList(),
        val productIdsWithVariants: Set<String> = emptySet(),
        val cart: List<OrderDraftItem> = emptyList(),
        val variantSheetProduct: Product? = null,     // no-null → mostrar VariantSelectorSheet
        val variantSheetVariants: List<ProductVariant> = emptyList(),
    ) {
        val subtotal: BigDecimal get() = cart.fold(BigDecimal.ZERO) { acc, item -> acc + item.subtotal }
        val quantityByProductId: Map<String, Int> get() =
            cart.groupBy { it.product.id }.mapValues { (_, items) -> items.sumOf { it.quantity } }
    }

    @HiltViewModel
    class ItemListViewModel @Inject constructor(
        savedStateHandle: SavedStateHandle,
        private val productRepository: ProductRepository,
        @TenantId private val tenantId: Flow<String?>,
    ) : ViewModel() {

        private val clientId: String = checkNotNull(savedStateHandle["clientId"])

        private val _uiState = MutableStateFlow(ItemListUiState())
        val uiState: StateFlow<ItemListUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val tenant = tenantId.first() ?: return@launch
                launch {
                    productRepository.getActiveProducts(tenant).collect { products ->
                        _uiState.value = _uiState.value.copy(products = products, isLoading = false)
                    }
                }
                val idsWithVariants = runCatching { productRepository.getProductIdsWithVariants(tenant) }.getOrDefault(emptySet())
                _uiState.value = _uiState.value.copy(productIdsWithVariants = idsWithVariants)
            }
        }

        fun onProductClick(product: Product) {
            if (product.id in _uiState.value.productIdsWithVariants) {
                viewModelScope.launch {
                    val tenant = tenantId.first() ?: return@launch
                    val variants = runCatching { productRepository.getVariantsForProduct(product.id, tenant) }.getOrDefault(emptyList())
                    if (variants.isEmpty()) return@launch
                    _uiState.value = _uiState.value.copy(variantSheetProduct = product, variantSheetVariants = variants)
                }
            } else {
                addOrIncrementItem(product, variant = null)
            }
        }

        fun onVariantSheetDismiss() {
            _uiState.value = _uiState.value.copy(variantSheetProduct = null, variantSheetVariants = emptyList())
        }

        fun onVariantConfirmed(variant: ProductVariant, quantity: Int) {
            val product = _uiState.value.variantSheetProduct ?: return
            addOrIncrementItem(product, variant, quantity)
            onVariantSheetDismiss()
        }

        private fun addOrIncrementItem(product: Product, variant: ProductVariant?, addQuantity: Int = 1) {
            val cart = _uiState.value.cart
            val existingIndex = cart.indexOfFirst { it.product.id == product.id && it.variant?.id == variant?.id }
            val newCart = if (existingIndex >= 0) {
                cart.toMutableList().also {
                    val existing = it[existingIndex]
                    it[existingIndex] = existing.copy(quantity = existing.quantity + addQuantity)
                }
            } else {
                cart + OrderDraftItem(product, variant, addQuantity)
            }
            _uiState.value = _uiState.value.copy(cart = newCart)
        }
    }
    ```
    `checkNotNull(savedStateHandle["clientId"])` — a diferencia de `ClientProfileViewModel`, aquí `clientId` no se usa para cargar datos del cliente en esta pantalla (S-04 no muestra info del cliente, solo el catálogo — el AC no lo pide); se recibe y se mantiene disponible únicamente para pasarlo intacto a S-06 cuando esa historia exista. No cargar `Client`/`ClientRepository` en este ViewModel — mantenerlo fuera de alcance.
    Carga de `products` (reactivo, `Flow`) y `productIdsWithVariants` (una sola vez, `suspend`) en paralelo dentro del mismo `viewModelScope.launch` usando dos `launch` internos — el segundo (`idsWithVariants`) no depende del primero.

- [x] **T12: `ItemListScreen`** (AC-3, AC-4, AC-5, AC-6, AC-7)
  - [x] Crear `ui/screens/orders/ItemListScreen.kt`:
    - `Scaffold` con `TopAppBar` (back estándar).
    - `BackHandler` — si `uiState.cart.isNotEmpty()`, muestra `AlertDialog` con el texto exacto del AC-6 ("¿Abandonar esta orden? Los ítems seleccionados se perderán." / "Sí, salir" / "No, quedarme.", copy exacto de `UX-DR21`) antes de dejar pasar el back; si el carrito está vacío, back normal (`onBackClick()` directo, sin dialog — no hay nada que perder).
    - Si `products.isEmpty()` (y no está cargando) → `EmptyState` con el texto exacto del AC-7 ("Aún no hay productos en el catálogo. Agrégalos en Configuración.") + botón "Ir a Configuración" que navega a `Routes.Settings` (navegación real, la pantalla ya existe desde Historia 1.4/2.4 — **no** es un Snackbar placeholder).
    - Si no está vacío → `LazyColumn` de filas de producto: nombre + precio formateado; chip "Variantes" (`AssistChip` o similar, texto "Variantes") si `product.id in productIdsWithVariants`; contador (`Text` con el valor de `quantityByProductId[product.id]`, solo si > 0) alineado a la derecha. `onClick` de la fila → `viewModel.onProductClick(product)`.
    - Barra inferior persistente (`Column` fija al fondo del `Scaffold`, fuera del `LazyColumn`): subtotal formateado (`titleMedium`/`primary`) + botón "Revisar Orden" (`onClick` → Snackbar placeholder "Resumen de orden — disponible próximamente", ver "Fuera de alcance").
    - Si `uiState.variantSheetProduct != null` → renderizar `VariantSelectorSheet(product = ..., variants = uiState.variantSheetVariants, onDismiss = viewModel::onVariantSheetDismiss, onConfirm = viewModel::onVariantConfirmed)`.
    - Foco TalkBack al cerrar el sheet sin confirmar (AC-5): usar `remember { FocusRequester() }` por ítem de la lista es excesivo para esta historia; **solución pragmática** — mantener un `LazyListState` y, en `onVariantSheetDismiss`, no mover foco explícito más allá del comportamiento por defecto de Compose al cerrar un `ModalBottomSheet` (que ya devuelve el foco al elemento que lo abrió en la mayoría de los casos por el sistema de foco de Compose). **Si la verificación manual en dispositivo muestra que el foco no regresa correctamente, es un hallazgo válido para el code review** — no bloquear la historia por esto, documentarlo como riesgo conocido en Dev Agent Record si no se puede verificar sin `adb`.

### Android — Navegación

- [x] **T13: Rutas y wiring** (AC-1, AC-2, AC-3)
  - [x] Actualizar `ui/navigation/Routes.kt` — agregar:
    ```kotlin
    object NewOrderClientSelect : Routes("new_order_client_select")

    object NewOrderItems : Routes("new_order_items/{clientId}") {
        fun createRoute(clientId: String): String = "new_order_items/$clientId"
    }
    ```
  - [x] Actualizar `ui/navigation/NavGraph.kt`:
    - `OrderListScreen`'s FAB actualmente muestra un Snackbar placeholder ("Nueva orden — disponible próximamente", de Historia 3.1) — **esta historia lo reemplaza por navegación real**. Requiere agregar un parámetro `onNewOrderClick: () -> Unit = {}` a `OrderListScreen` (ver T14) y pasarlo desde `NavGraph`:
      ```kotlin
      composable(Routes.Orders.route) {
          OrderListScreen(
              onNewOrderClick = {
                  navController.navigate(Routes.NewOrderClientSelect.route) { launchSingleTop = true }
              },
          )
      }
      ```
    - Agregar composables:
      ```kotlin
      composable(Routes.NewOrderClientSelect.route) {
          ClientSelectScreen(
              onBackClick = { navController.popBackStack() },
              onClientSelected = { clientId ->
                  navController.navigate(Routes.NewOrderItems.createRoute(clientId)) { launchSingleTop = true }
              },
              onNewClientClick = {
                  navController.navigate(Routes.ClientForm.createRoute()) { launchSingleTop = true }
              },
          )
      }
      composable(
          route = Routes.NewOrderItems.route,
          arguments = listOf(navArgument("clientId") { type = NavType.StringType }),
      ) {
          ItemListScreen(
              onBackClick = { navController.popBackStack() },
              onGoToSettingsClick = {
                  navController.navigate(Routes.Settings.route) { launchSingleTop = true }
              },
          )
      }
      ```
    - **El tercer call site de `ClientForm`** (desde S-03, "Nuevo cliente"): agregar un composable **separado** para esta ruta con un `onSaved` distinto al de los otros dos (T5 ya deja `onSaved: (String) -> Unit`). Dado que `Routes.ClientForm` es una única ruta reutilizada desde 3 orígenes distintos (S-11, S-12, S-03), y Compose Navigation no permite registrar el mismo `route` dos veces con composables diferentes, la forma correcta es: **desde `ClientSelectScreen`, no navegar directo a `Routes.ClientForm`** — en su lugar, usar el resultado vía `SavedStateHandle` del back stack (patrón estándar de Compose Navigation para pasar un resultado de vuelta):
      ```kotlin
      // En el composable existente de Routes.ClientForm (el que ya sirve a S-11/S-12):
      ClientFormScreen(
          onSaved = { newClientId ->
              navController.previousBackStackEntry?.savedStateHandle?.set("newClientId", newClientId)
              navController.popBackStack()
          },
          onCancel = { navController.popBackStack() },
      )
      ```
      Y en el composable de `Routes.NewOrderClientSelect`, leer el resultado cuando la entrada vuelve a estar en primer plano:
      ```kotlin
      composable(Routes.NewOrderClientSelect.route) { backStackEntry ->
          val newClientId = backStackEntry.savedStateHandle?.get<String>("newClientId")
          LaunchedEffect(newClientId) {
              if (newClientId != null) {
                  backStackEntry.savedStateHandle?.remove<String>("newClientId")
                  navController.navigate(Routes.NewOrderItems.createRoute(newClientId)) { launchSingleTop = true }
              }
          }
          ClientSelectScreen(...)
      }
      ```
      Esto resuelve AC-2 completo: S-13 se abre (reutilizando el `ClientForm` ya wireado a S-11/S-12, sin un cuarto composable), y al guardar, S-03 recibe el id y avanza directo a S-04 con el cliente recién creado — interpretación de "pre-seleccionado" que evita un toque redundante (ver Dev Notes § Decisión de alcance).

- [x] **T14: `OrderListScreen` — reemplazar el Snackbar placeholder del FAB** (AC-1)
  - [x] Actualizar `ui/screens/orders/OrderListScreen.kt` — agregar parámetro `onNewOrderClick: () -> Unit = {}`; el FAB "Nueva Orden" llama a `onNewOrderClick()` en vez de mostrar el Snackbar placeholder ("Nueva orden — disponible próximamente", de Historia 3.1). El Snackbar de `OrderCard.onClick` ("Detalle de orden...") **no cambia** — S-09 sigue sin existir (Historia 3.5).

### Review Findings

- [x] [Review][Patch] Lectura no reactiva de `SavedStateHandle` en `NewOrderClientSelect`. **Resuelto:** `getStateFlow<String?>("newClientId", null).collectAsStateWithLifecycle()` reemplaza el `.get()` de una sola vez, patrón oficial de Compose Navigation. [NavGraph.kt]
- [x] [Review][Patch] Carrera al tocar dos productos con variantes en sucesión rápida. **Resuelto:** `ItemListViewModel` trackea el `Job` en curso (`variantLoadJob`) y lo cancela antes de lanzar uno nuevo en cada toque; `VariantSelectorSheet` además usa `firstOrNull` en vez de `first` como defensa adicional contra el `NoSuchElementException`. Test agregado (`onProductClick cancels a pending variant load...`). [ItemListViewModel.kt, VariantSelectorSheet.kt, ItemListViewModelTest.kt]
- [x] [Review][Patch] JOIN sin scope de tenant en la tabla unida. **Resuelto:** `AND s.fk_tenant = :tenantId` agregado al `LEFT JOIN`; mismo ajuste reflejado en `FakeClientDao`; test de defensa agregado (`excludes a sale whose fkTenant does not match...`). [ClientDao.kt, ClientListViewModelTest.kt, ClientRepositoryTest.kt]
- [x] [Review][Patch] `ItemListViewModel` sin `.catch{}` en `getActiveProducts`. **Resuelto:** agregado `.catch { emit(emptyList()) }`, consistente con `ClientSelectViewModel`. [ItemListViewModel.kt]
- [x] [Review][Patch] Mutaciones no atómicas de `_uiState`. **Resuelto:** todas las mutaciones (`init`, `onProductClick`, `onVariantSheetDismiss`, `addOrIncrementItem`) usan `_uiState.update { ... }`. [ItemListViewModel.kt]
- [x] [Review][Patch] `ItemListScreen` no mostraba indicador de carga. **Resuelto:** rama `if (uiState.isLoading)` con `CircularProgressIndicator` antes de la rama de empty-state/catálogo. [ItemListScreen.kt]
- [x] [Review][Patch] `onSearchClear()` sin afordancia de UI. **Resuelto:** ícono de limpiar (`trailingIcon`) agregado al campo de búsqueda de `ClientSelectScreen`, mismo patrón que `ClientListScreen`/`OrderListScreen`. [ClientSelectScreen.kt]
- [x] [Review][Patch] Botón "Nuevo cliente" mal ubicado. **Resuelto:** movido después del `LazyColumn` (con `weight(1f)` para que el botón quede anclado al fondo), tal como especifica T10. [ClientSelectScreen.kt]
- [x] [Review][Patch] Dev Agent Record impreciso sobre T5/T13. **Resuelto:** Completion Notes corregido para describir con precisión que `Routes.ClientForm` mantiene un único composable compartido por los 3 orígenes, no 2 call sites + un cuarto nuevo. [historia 3.2 § Completion Notes List]
- [x] [Review][Patch] `quantityByProductId` recalculado por fila. **Resuelto:** hoisted a una sola variable local antes del `LazyColumn`, calculado una vez por composición en vez de una vez por fila. [ItemListScreen.kt]
- [x] [Review][Patch] Lógica de abandono duplicada. **Resuelto:** extraída a una lambda compartida `onBackRequested`, usada tanto por `BackHandler` como por el ícono de la `TopAppBar`. [ItemListScreen.kt]
- [x] [Review][Patch] `contentDescription` inconsistente entre pantallas. **Resuelto:** `ClientSelectScreen` cambiado de "Cancelar" a "Regresar", igual que `ItemListScreen`. [ClientSelectScreen.kt]
- [x] [Review][Defer] Carrito en memoria sin respaldo de `SavedStateHandle` — una muerte de proceso a medio armar una orden (escenario que la propia historia enmarca como "en campo") pierde el carrito sin aviso. Implementar persistencia Bundle-safe para una lista anidada de `OrderDraftItem` es una expansión de alcance sustancial, no exigida por ningún AC. [ItemListViewModel.kt] — deferred, no exigido por AC
- [x] [Review][Defer] `runCatching{}.getOrDefault(...)` en `ItemListViewModel` (carga inicial de `idsWithVariants`, `getVariantsForProduct` por toque) traga errores sin ningún indicio al usuario — mismo patrón de manejo de errores silencioso ya diferido en Historia 2.3 (`CancellationException`) y creciendo en el proyecto; requiere una pasada dedicada. [ItemListViewModel.kt] — deferred, pre-existing
- [x] [Review][Defer] `ItemListViewModel` no reacciona a cambios de `tenantId` después de la carga inicial (`tenantId.first()` en vez de `flatMapLatest` reactivo como `ClientSelectViewModel`) — impacto práctico bajo (el tenant no cambia sin cerrar sesión, lo que destruye la pantalla de todas formas). [ItemListViewModel.kt] — deferred, bajo impacto

## Dev Notes

### Decisión de alcance: "pre-seleccionado" tras crear cliente desde S-03 = avance automático a S-04

AC-2 dice "al guardar regresa a S-03 con el nuevo cliente pre-seleccionado", mientras que otro AC (AC-3, aplicado a cualquier cliente) dice "el proveedor confirma el cliente y avanza a S-04" como un solo toque. Toda la app usa el patrón "un toque en una fila = navegar" (`ClientCard`, `ProductCard`, `OrderCard`) — no existe precedente de un paso de "selección" seguido de un botón de "confirmar" separado. Se interpreta "pre-seleccionado" como: tras guardar en S-13, la app avanza automáticamente a S-04 con el cliente recién creado, sin exigir un toque adicional en S-03 (el usuario ya expresó su intención al buscar-no-encontrar-crear). Si se prefiere la alternativa (volver a S-03 y exigir un toque explícito sobre el nuevo cliente ya visible en la lista), es un cambio menor en T13 — confirmar con Josemtz si el comportamiento implementado no es el esperado tras la verificación manual.

### Decisión de alcance: sin badge "Vencido" en S-03

Ver "Fuera de alcance". A diferencia de Historia 2.3 (que sí construyó `FinancialAlertBanner` mecánicamente sin datos reales, porque esa historia lo dejaba preparado para S-12), aquí no hay ninguna razón similar para adelantar trabajo — no se crea ningún componente de badge de vencimiento en esta historia.

### Por qué `ClientDao` gana una query nueva con scope de tenant sin arreglar las viejas

Ver T1. Resumen: `getAllAsFlow()`/`searchByNameAsFlow()` (Historia 2.1) siguen sin `WHERE fk_tenant = :tenantId` — deferred abierto documentado en `deferred-work.md` desde Historia 2.1/2.2, todavía sin una historia dedicada que lo resuelva. Esta historia **no** es esa historia dedicada (alcance distinto: selección de cliente para una nueva venta, no el fix de aislamiento de S-11). La query nueva de T1 nace correctamente aislada porque es código nuevo (mismo criterio aplicado en Historia 3.1 al JOIN de `SaleDao`), pero retrofittear las dos queries viejas de `ClientDao` queda fuera de esta historia — considerar priorizarlo pronto, dado que ya son 3 historias seguidas (2.4, 3.1, 3.2) tocando el tema del aislamiento por tenant sin cerrar el gap original.

### Arquitectura: por qué S-05 es un bottom sheet dentro de S-04, no una ruta de navegación separada

`architecture.md` anticipa `VariantSelectorSheet.kt` en el mismo paquete que `ItemListScreen.kt`, no como una entrada independiente de `Routes.kt`. Esto es deliberado: el proyecto no tiene ningún precedente de ViewModel compartido entre pantallas de un mismo flujo (cada `hiltViewModel()` se scopea 1:1 a su propio `NavBackStackEntry`). Si S-05 fuera una ruta de navegación separada, necesitaría acceso al carrito en construcción de S-04 — forzando a introducir un ViewModel compartido a nivel de un grafo de navegación anidado, un patrón nuevo y más complejo que el AC no exige. En cambio, `VariantSelectorSheet` es un `ModalBottomSheet` renderizado condicionalmente dentro de `ItemListScreen`, alimentado y controlado por el mismo `ItemListViewModel` — sin necesidad de compartir estado entre ViewModels ni pantallas.

### Archivos existentes relevantes — estado actual y cambio en esta historia

| Archivo | Estado actual | Cambio en esta historia |
|---------|---------------|--------------------------|
| `ClientDao.kt` | `getAllAsFlow()`, `searchByNameAsFlow()` (Historia 2.1, sin scope de tenant) | + `searchWithBalanceAsFlow()` (T1, con scope de tenant) |
| `ClientRepository.kt` | `getAllClients()`, `searchClients()`, CRUD (Historia 2.1/2.2) | + `searchClientsWithBalance()` (T2) |
| `ProductDao.kt` | `getAllAsFlow()` (todos, activos+inactivos, Historia 2.4) | + `getActiveAsFlow()` (T3) |
| `ProductVariantDao.kt` | `getForProduct()`, `upsertAll()`, `deleteAllForProduct()` (Historia 2.4) | + `getProductIdsWithVariants()` (T3) |
| `ProductRepository.kt` | `getAllProducts()`, CRUD (Historia 2.4) | + `getActiveProducts()`, `getProductIdsWithVariants()` (T4) |
| `ClientFormViewModel.kt`/`ClientFormScreen.kt` | `navEvent`/`onSaved` sin payload (Historia 2.2) | `navEvent`/`onSaved` pasan a emitir el id del cliente guardado (T5) |
| `OrderListScreen.kt` | FAB "Nueva Orden" muestra Snackbar placeholder (Historia 3.1) | FAB navega de verdad a S-03 (T14) |
| `Routes.kt`/`NavGraph.kt` | 8 rutas (Historia 3.1) | + `NewOrderClientSelect`, `NewOrderItems`; 2 call sites de `ClientForm` actualizados, 1 nuevo mecanismo de resultado vía `SavedStateHandle` |

**NO tocar:**
- `SaleDao.kt`, `SaleRepository.kt`, `OrderListViewModel.kt` — sin relación con esta historia (Historia 3.1 ya cerrada).
- `ClientDao.getAllAsFlow()`/`searchByNameAsFlow()` — el deferred de tenant sigue abierto, fuera de alcance (ver Dev Notes).
- `ProductDao.getAllAsFlow()` — sigue usándose tal cual por S-14 › Catálogo.
- `StatusBadge.kt` — no se usa en esta historia (el badge "Vencido" está fuera de alcance).
- No crear `Installment.kt`/`Payment.kt`/`SaleRepository.createSale` — Historia 3.3+.

### Testing

Mismo patrón establecido en Historias 2.1-3.1: **sin Robolectric**, tests JVM puros con Fake DAOs, correr con el JDK de Android Studio (`./gradlew :app:testDebugUnitTest`).

- **`FakeClientDao`** (existente, definido dentro de `ClientListViewModelTest.kt`) — extender para implementar `searchWithBalanceAsFlow`. Dado que ya existe `FakeSaleDao` con almacenamiento `MutableStateFlow<List<SaleEntity>>`, el fake de `searchWithBalanceAsFlow` puede calcular el saldo en memoria replicando la agregación SQL (`sum de sales.total donde status in pending/partial y fk_client coincide`) — requiere que el fake tenga acceso también a una lista de `SaleEntity` de prueba (agregar un parámetro/setter nuevo si `FakeClientDao` no tiene forma de recibirlas, o construir el fake de `ClientRepository` con ambos fakes conectados en el test).
- **`ClientRepositoryTest`** (existente) — agregar casos para `searchClientsWithBalance`: mapea correctamente incluyendo `balance`; excluye clientes de otro tenant; búsqueda con y sin acentos; cliente sin ventas → `balance = BigDecimal.ZERO`; `trim()` de búsqueda con solo espacios.
- **`ProductRepositoryTest`** (existente) — agregar casos para `getActiveProducts` (excluye inactivos, a diferencia de `getAllProducts`) y `getProductIdsWithVariants` (incluye solo productos con al menos una variante, excluye tenant ajeno).
- **`ClientSelectViewModelTest`** (nuevo, `test/java/.../ui/screens/orders/`) — resultados vacíos inicialmente; refleja resultados del tenant activo; búsqueda filtra y actualiza `balance`; `tenantId` nulo → lista vacía.
- **`ItemListViewModelTest`** (nuevo, mismo paquete) — carga inicial puebla `products`/`productIdsWithVariants`; `onProductClick` de un producto sin variantes agrega al carrito con cantidad 1 (AC-4); tocar el mismo producto de nuevo incrementa la cantidad existente en vez de duplicar la fila; `onProductClick` de un producto con variantes puebla `variantSheetProduct`/`variantSheetVariants` (AC-5) sin tocar el carrito todavía; `onVariantConfirmed` agrega al carrito con la variante y cantidad seleccionadas y cierra el sheet; `onVariantSheetDismiss` cierra el sheet sin modificar el carrito; `subtotal` calcula correctamente sobre múltiples ítems; `quantityByProductId` suma correctamente cantidades del mismo producto con variantes distintas.
- Sin test de Composable/UI (`ClientSelectScreen`/`ItemListScreen`/`VariantSelectorSheet`/`QuantityStepper`) — mismo criterio que historias previas, el proyecto no tiene infraestructura de test de Composables. El comportamiento de foco de TalkBack al cerrar `VariantSelectorSheet` (AC-5) **no puede verificarse con las herramientas de este entorno** — documentar como pendiente de verificación manual.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Historia 3.2: Selección de Cliente e Ítems en Nueva Orden] (líneas 505-538) — AC originales
- [Source: _bmad-output/planning-artifacts/epics.md#UX Design Requirements] UX-DR11 (`QuantityStepper`), UX-DR13 (`VariantSelectorSheet`), UX-DR21 (dialog de abandono, copy exacto)
- [Source: _bmad-output/planning-artifacts/architecture/architecture.md] árbol Android (líneas 340-360): `ClientSelectScreen.kt`/`ClientSelectViewModel.kt` (S-03), `ItemListScreen.kt`/`ItemListViewModel.kt` (S-04), `VariantSelectorSheet.kt` (S-05) ya anticipados en `ui/screens/orders/`; `QuantityStepper.kt` en `ui/components/`
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md#S-03/S-04/S-05] (líneas 52-56) descripciones de pantalla
- [Source: _bmad-output/implementation-artifacts/3-1-historial-de-ordenes.md] aprendizajes aplicados: aislamiento por tenant desde el diseño inicial en toda query nueva, `trim()` de búsqueda desde el inicio, primer JOIN cross-entidad como precedente para la query agregada de saldo de esta historia
- [Source: _bmad-output/implementation-artifacts/2-4-catalogo-de-productos-y-variantes.md] `getActiveAsFlow` explícitamente pendiente para esta historia; sin precio/impuesto por variante
- [Source: android/app/src/main/java/com/sumitrack/android/data/repositories/ClientRepository.kt] comentario explícito sobre por qué `getAllClients()`/`searchClients()` omiten el saldo real (evitar N+1) — misma razón que motiva la query nueva de esta historia
- [Source: android/app/src/main/java/com/sumitrack/android/ui/theme/Shape.kt] `SumitrackShapes.large` — shape de bottom sheet ya definido, reutilizar tal cual en `VariantSelectorSheet`
- [Source: android/app/src/main/java/com/sumitrack/android/ui/components/FilterChipRow.kt, StatusBadge.kt, EmptyState.kt] componentes existentes a reutilizar sin modificar

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (`claude-sonnet-5`)

### Debug Log References

Sin desviaciones respecto a lo especificado en Tasks/Subtasks — las decisiones de alcance más delicadas (badge "Vencido" fuera de alcance, S-05 como bottom sheet dentro de S-04 en vez de ruta separada, "pre-seleccionado" = avance automático a S-04, mecanismo de retorno del id de cliente vía `SavedStateHandle`) ya venían resueltas en la historia y se siguieron tal cual. Build limpio sin warnings desde la primera compilación (a diferencia de Historia 3.1, que tuvo 3 warnings de deprecación que corregir).

### Completion Notes List

Historia implementada completa. 149 tests ✅ (0 fallos, +23 sobre Historia 3.1). `BUILD SUCCESSFUL` (`assembleDebug` + `testDebugUnitTest`, sin warnings, JDK de Android Studio).

- AC-1 ✅: `ClientSelectScreen` (S-03) con campo de búsqueda siempre visible + lista de resultados (nombre + saldo real, vía nueva query agregada `ClientDao.searchWithBalanceAsFlow` sin N+1). Badge "Vencido" explícitamente fuera de alcance (ver Dev Notes/Fuera de alcance) — no hay forma de calcular deuda vencida sin datos de vencimiento (Historia 3.3+).
- AC-2 ✅: botón "Nuevo cliente" navega a `ClientForm` (reutilizado, Historia 2.2); el id del cliente creado se propaga a S-03 vía `previousBackStackEntry.savedStateHandle` y S-03 avanza automáticamente a S-04 (interpretación de "pre-seleccionado" documentada en Dev Notes).
- AC-3 ✅: `ItemListScreen` (S-04) lista productos activos (`ProductDao.getActiveAsFlow`, nueva query — Historia 2.4 la dejó pendiente explícitamente para esta historia) con chip "Variantes" (vía `ProductVariantDao.getProductIdsWithVariants`, bulk, sin N+1); barra inferior con subtotal + botón "Revisar Orden" (Snackbar placeholder, S-06 no existe).
- AC-4 ✅: `onProductClick` de un producto sin variantes agrega al carrito con cantidad 1; tocar el mismo producto de nuevo incrementa la cantidad en vez de duplicar la fila; contador visible en la card; subtotal se recalcula automáticamente (`ItemListUiState.subtotal` derivado).
- AC-5 ✅: `VariantSelectorSheet` (bottom sheet embebido en S-04, no ruta de navegación separada — ver Dev Notes de arquitectura) con `FilterChipRow` (chips de variante) + `QuantityStepper` (nuevo componente); botón "Agregar a la orden" deshabilitado hasta seleccionar variante. Retorno de foco TalkBack al cerrar sin confirmar: se dejó el comportamiento por defecto de `ModalBottomSheet` de Compose — **pendiente de verificación manual**, no se pudo confirmar sin `adb`.
- AC-6 ✅: `BackHandler` (gesto/botón físico) y el ícono de back de la `TopAppBar` interceptan la salida cuando `cart.isNotEmpty()`, mostrando el `AlertDialog` con el copy exacto de `UX-DR21`.
- AC-7 ✅: catálogo vacío muestra el empty state exacto + botón "Ir a Configuración" con navegación real (no Snackbar) a `Routes.Settings`, ya existente desde Historia 1.4/2.4.
- **Cambio en código de Historia 2.2:** `ClientFormViewModel`/`ClientFormScreen` — el canal `navEvent`/callback `onSaved` pasó de `Unit` a emitir el id del cliente guardado (`String`). Cambio mecánico, sin alterar la lógica de guardado; 2 tests de `ClientFormViewModelTest` actualizados al nuevo tipo. **Corrección post-review:** `Routes.ClientForm` sigue teniendo un único composable en `NavGraph.kt` (compartido por S-11/alta, S-12/edición y S-03/alta rápida desde esta historia, tal como ya existía antes de esta historia) — no se crearon 2 call sites separados con `{ _ -> ... }` ni un cuarto composable nuevo; el único `onSaved` existente ahora escribe `newClientId` a `previousBackStackEntry.savedStateHandle` incondicionalmente para los 3 orígenes (los que no lo necesitan simplemente no lo leen). El comportamiento de AC-2 es correcto; la primera versión de esta nota describía T5/T13 de forma imprecisa.
- Sin test de Composable/UI (`ClientSelectScreen`/`ItemListScreen`/`VariantSelectorSheet`/`QuantityStepper`) — mismo criterio que historias previas, el proyecto no tiene infraestructura de test de Composables.
- **Pendiente:** verificación manual en emulador/dispositivo físico — este entorno no tiene `adb` ni emulador Android disponible (mismo pendiente que en Historias 2.1-3.1). Recomendado antes de mergear: tab Órdenes → FAB "Nueva Orden" → buscar/seleccionar cliente (o crear uno nuevo y confirmar que avanza directo a S-04) → tocar producto sin variantes (contador aparece) → tocar producto con variantes (sheet abre, seleccionar variante + cantidad, confirmar) → subtotal correcto → Back con carrito no vacío (dialog de abandono) → "Revisar Orden" (Snackbar) → catálogo vacío (empty state + "Ir a Configuración").

### File List

**Archivos creados (NEW):**
- `android/app/src/main/java/com/sumitrack/android/domain/models/ClientSearchResult.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/models/OrderDraftItem.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/components/QuantityStepper.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/VariantSelectorSheet.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/ClientSelectViewModel.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/ClientSelectScreen.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/ItemListViewModel.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/ItemListScreen.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/ClientSelectViewModelTest.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/ItemListViewModelTest.kt`

**Archivos modificados (UPDATE):**
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/ClientDao.kt` — + `ClientSearchRow`, + `searchWithBalanceAsFlow`
- `android/app/src/main/java/com/sumitrack/android/data/repositories/ClientRepository.kt` — + `searchClientsWithBalance`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/ProductDao.kt` — + `getActiveAsFlow`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/ProductVariantDao.kt` — + `getProductIdsWithVariants`
- `android/app/src/main/java/com/sumitrack/android/data/repositories/ProductRepository.kt` — + `getActiveProducts`, + `getProductIdsWithVariants`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientFormViewModel.kt` — `navEvent` emite el id del cliente
- `android/app/src/main/java/com/sumitrack/android/ui/screens/clients/ClientFormScreen.kt` — `onSaved: (String) -> Unit`
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/Routes.kt` — + `NewOrderClientSelect`, `NewOrderItems`
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/NavGraph.kt` — + composables nuevos; resultado de `ClientForm` vía `SavedStateHandle`; `OrderListScreen` recibe `onNewOrderClick`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderListScreen.kt` — FAB navega de verdad, ya no Snackbar placeholder
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientListViewModelTest.kt` — `FakeClientDao` + `searchWithBalanceAsFlow`, `setSales`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/ClientFormViewModelTest.kt` — 2 tests actualizados al nuevo tipo de `navEvent`
- `android/app/src/test/java/com/sumitrack/android/data/repositories/ClientRepositoryTest.kt` — + 5 casos de `searchClientsWithBalance`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/products/FakeProductDao.kt` — + `getActiveAsFlow`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/products/FakeProductVariantDao.kt` — + `getProductIdsWithVariants`
- `android/app/src/test/java/com/sumitrack/android/data/repositories/ProductRepositoryTest.kt` — + 4 casos de `getActiveProducts`/`getProductIdsWithVariants`

## Change Log

- **2026-07-19** — Historia 3.2 implementada completa (Status: review)
  - NEW: `ClientSearchResult`/`ClientSearchRow` + `ClientDao.searchWithBalanceAsFlow` (query agregada `SUM`, tenant-safe desde el diseño), `ClientRepository.searchClientsWithBalance`
  - NEW: `ProductDao.getActiveAsFlow`, `ProductVariantDao.getProductIdsWithVariants` (pendientes explícitos de Historia 2.4)
  - UPDATE: `ClientFormViewModel`/`ClientFormScreen` — `navEvent`/`onSaved` emiten el id del cliente guardado
  - NEW: `OrderDraftItem` (carrito en memoria), `QuantityStepper`, `VariantSelectorSheet` (bottom sheet embebido en S-04)
  - NEW: `ClientSelectViewModel`/`ClientSelectScreen` (S-03), `ItemListViewModel`/`ItemListScreen` (S-04)
  - UPDATE: `Routes`/`NavGraph` — rutas `new_order_client_select`/`new_order_items`; resultado de alta rápida de cliente vía `SavedStateHandle`; `OrderListScreen` FAB navega de verdad
  - NEW: tests — `ClientSelectViewModelTest` (6), `ItemListViewModelTest` (8), + 5 casos en `ClientRepositoryTest`, + 4 casos en `ProductRepositoryTest`
  - Build: 149 tests ✅ (0 fallos, +23 sobre Historia 3.1), `BUILD SUCCESSFUL` sin warnings (`assembleDebug` + `testDebugUnitTest`, JDK de Android Studio)
  - Pendiente: verificación manual en dispositivo/emulador (sin `adb` en este entorno); code review todavía no ejecutado

- **2026-07-19** — Code review: 0 decisiones + 12 patches aplicados (Status: done)
  - PATCH: `NewOrderClientSelect` — lectura de `SavedStateHandle` cambiada a `getStateFlow().collectAsStateWithLifecycle()` (patrón oficial), reemplaza un `.get()` de una sola vez que arriesgaba que AC-2 no funcionara de forma confiable en dispositivo
  - PATCH: `ItemListViewModel.onProductClick` — cancela el `Job` de carga de variantes en curso antes de lanzar uno nuevo, evita que dos toques rápidos sobre productos con variantes distintos sobreescriban el sheet abierto o provoquen `NoSuchElementException` en `VariantSelectorSheet` (que además ahora usa `firstOrNull` como defensa adicional)
  - PATCH: `ClientDao.searchWithBalanceAsFlow` — agregado `AND s.fk_tenant = :tenantId` al `LEFT JOIN`, defensa en profundidad (hoy inalcanzable por invariante de datos, igual que Historia 3.1)
  - PATCH: `.catch{}` agregado a `ItemListViewModel.getActiveProducts`, consistente con `ClientSelectViewModel`
  - PATCH: mutaciones de `_uiState` en `ItemListViewModel` migradas a `.update{}` atómico (antes lectura-modificación-escritura entre 2 corrutinas)
  - PATCH: `ItemListScreen` — indicador de carga (`CircularProgressIndicator`) agregado, antes `isLoading` se declaraba pero nunca se usaba
  - PATCH: ícono de limpiar búsqueda agregado a `ClientSelectScreen` (código muerto de `onSearchClear()` ahora tiene afordancia de UI)
  - PATCH: botón "Nuevo cliente" movido después de la lista de resultados, tal como especificaba T10
  - PATCH: Dev Agent Record corregido — la narrativa original sobre T5/T13 describía 2 call sites + un cuarto composable que nunca se crearon; en realidad `Routes.ClientForm` mantiene un único composable compartido por los 3 orígenes
  - PATCH: `quantityByProductId` hoisted fuera del `LazyColumn` (antes recalculaba el agrupamiento completo del carrito por cada fila)
  - PATCH: lógica de abandono de `BackHandler`/ícono de back unificada en una sola lambda compartida
  - PATCH: `contentDescription` del ícono de back de `ClientSelectScreen` estandarizado a "Regresar"
  - Descartados (8): fórmula de saldo "sobrestima" ventas parciales (Acceptance Auditor confirmó que es idéntica a `CalculateClientBalanceUseCase`), falta de semántica TalkBack en el contador de cantidad, AC-5 marcado `[x]` pese a verificación manual pendiente (convención establecida en todas las historias previas), duplicación de `formatBalance`/`formatAmount` (patrón ya validado dos veces), y 4 más de menor severidad
  - Deferred (3, ver `deferred-work.md`): carrito sin respaldo de `SavedStateHandle` (pérdida en muerte de proceso), manejo de errores silencioso en `ItemListViewModel` (crece la lista de Historia 2.3), `ItemListViewModel` no reactivo a cambios de `tenantId`
  - NEW: tests — `onProductClick cancels a pending variant load...` (`ItemListViewModelTest`), `excludes a sale whose fkTenant does not match...` (`ClientRepositoryTest`)
  - Build verificado: **151 tests ✅ (0 fallos)**, `BUILD SUCCESSFUL` sin warnings (`assembleDebug` + `testDebugUnitTest`, JDK de Android Studio)
  - Pendiente: verificación manual en dispositivo/emulador (sin `adb` en este entorno, incluye el foco TalkBack de AC-5); code review todavía no ejecutado
