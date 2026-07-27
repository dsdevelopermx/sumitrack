---
baseline_commit: 9d159938d92ee006c202347cd2aa5a540263c572
---

# Story 3.4: Generación y Distribución del Ticket

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

Como proveedor,
quiero generar y compartir o imprimir el ticket inmediatamente después de confirmar una venta,
para que el cliente reciba su comprobante en el momento.

## Acceptance Criteria

**AC-1 — Vista previa del ticket (S-08)**

**Dado** que el proveedor confirma el pago en S-07
**Cuando** la venta se persiste en SQLite (`sync_status = pending`, folio asignado)
**Entonces** se muestra S-08 (TicketSheet) con la vista previa del ticket que incluye: datos fiscales del tenant (nombre, RFC, dirección, teléfono desde Settings), datos del cliente, ítems con cantidades y precios, subtotal/impuestos/total, condición de pago (fechas de parcialidades si aplica) y folio

**AC-2 — Imprimir vía Bluetooth**

**Dado** que el proveedor toca "Imprimir vía Bluetooth"
**Cuando** `GenerateTicketUseCase` genera el PNG en memoria
**Entonces** la app intenta conectar a la impresora térmica vía Bluetooth e imprime el ticket

**AC-3 — Impresora no disponible**

**Dado** que la impresora no está disponible
**Cuando** falla la conexión Bluetooth
**Entonces** aparece Snackbar: "No encontramos la impresora. La orden ya está guardada — puedes compartir el ticket después."; el bottom sheet permanece abierto para retry o para usar "Compartir"

**AC-4 — Compartir**

**Dado** que el proveedor toca "Compartir"
**Cuando** `GenerateTicketUseCase` genera el PNG en memoria
**Entonces** se dispara el Android share intent con la imagen PNG; la imagen NO se guarda en el almacenamiento del dispositivo

**AC-5 — Cierre y regreso a Historial**

**Dado** que el proveedor cierra S-08
**Cuando** toca fuera del sheet o hace swipe down
**Entonces** navega a S-02 (Historial); el foco de TalkBack regresa al FAB en S-02; la nueva orden aparece al tope de la lista con su `StatusBadge` e `SyncIcon` en estado pendiente

### Fuera de alcance en esta historia (explícito)

- **UI de configuración de datos fiscales del tenant (`negocio_nombre`/`negocio_rfc`/`negocio_direccion`/`negocio_telefono`)** — Historia 5.1, no existe todavía, y el backend tampoco siembra estas claves (a diferencia de `serie_folio`/`max_parcialidades`, confirmado revisando `ApplicationBuilderExtensions.cs` — cero coincidencias de `rfc|nombre|direccion|telefono|fiscal` en todo el backend). Esta historia solo **lee** esas 4 claves nuevas vía `SettingsRepository.getValue(key)` (ya existe desde Historia 3.3) con fallback a cadena vacía si no existen — el ticket debe renderizarse igual de bien con datos fiscales en blanco que con datos reales; no se bloquea la generación del ticket por esto, mismo criterio que FR-25 ya establece para la impresora no disponible ("no bloquea la Venta ya guardada").
- **Selector de impresora Bluetooth / emparejamiento dentro de la app** — sin infraestructura de pairing hoy (nunca se pidió un permiso Bluetooth en este proyecto), construir una pantalla de selección de dispositivo es una expansión de alcance sustancial no exigida por ningún AC (AC-2 solo dice "intenta conectar", no "el usuario elige entre varias"). Esta historia asume que la impresora térmica ya fue emparejada por el proveedor desde los Ajustes de Bluetooth del sistema operativo Android (flujo estándar para impresoras POS baratas) y **autoconecta al primer dispositivo ya emparejado (`bondedDevices`)**. Si el proveedor tiene más de una impresora emparejada o ninguna, ver Dev Notes.
- **S-09 (Detalle de Orden) y su botón "Compartir Ticket"** — Historia 3.5, no existe todavía. `GenerateTicketUseCase` se diseña deliberadamente reutilizable por `saleId` (no acoplado a "la venta recién creada") precisamente para que Historia 3.5 lo reutilice sin tocarlo — pero la pantalla S-09 en sí no se construye aquí.
- **Registro histórico de "el ticket se compartió/imprimió N veces"** — ningún AC lo pide; ni FR-24/25/26 mencionan analítica o auditoría de impresión.
- **Reimpresión con distinto formato de papel (58mm vs 80mm) configurable** — se fija un ancho estándar de 384px (58mm a 203dpi, el tamaño más común en impresoras térmicas portátiles de bajo costo) sin UI para cambiarlo. Ningún AC lo exige.
- **`BLUETOOTH_SCAN`** — solo se necesita `BLUETOOTH_CONNECT` (API 31+) para leer `bondedDevices` y abrir un socket a un dispositivo ya emparejado; `BLUETOOTH_SCAN` es para descubrir dispositivos nuevos (`startDiscovery()`), que esta historia explícitamente no hace (ver punto de "selector de impresora" arriba).

## Tasks / Subtasks

### Android — Gaps de dominio previos a esta historia

- [x] **T1: `Sale` (dominio) — agregar `subtotal`/`tax`** (AC-1)
  - [x] **Por qué:** `SaleEntity` ganó `subtotal`/`tax` en Historia 3.3, pero `SaleRepository.toDomain()` nunca los mapeó al modelo de dominio `Sale` — un gap real, no descubierto hasta ahora porque ninguna historia anterior necesitaba leerlos de vuelta (3.3 solo los escribía). El ticket sí necesita mostrar subtotal e impuestos por separado.
  - [x] Actualizar `domain/models/Sale.kt` — agregar `val subtotal: BigDecimal` y `val tax: BigDecimal`.
  - [x] Actualizar `SaleRepository.toDomain()` (`SaleEntity.toDomain()`) para mapear `subtotal`/`tax`.
  - [x] Actualizar todos los sitios que construyen `Sale(...)` en tests (`SaleRepositoryTest.kt`, cualquier otro) con los nuevos parámetros — usar `BigDecimal.ZERO` como default en los helpers de test donde el valor no importe para el caso probado.

- [x] **T2: `SaleDao.getById` + `SaleRepository.getSaleDetail`** (AC-1, AC-2, AC-4)
  - [x] Agregar a `data/local/dao/SaleDao.kt`:
    ```kotlin
    @Query("SELECT * FROM sales WHERE id = :id AND fk_tenant = :tenantId LIMIT 1")
    suspend fun getById(id: String, tenantId: String): SaleEntity?
    ```
  - [x] Crear `domain/models/SaleDetail.kt`:
    ```kotlin
    data class SaleDetail(
        val sale: Sale,
        val items: List<SaleItem>,
        val payments: List<Payment>,
        val installments: List<Installment>,
    )
    ```
  - [x] Agregar a `SaleRepository.kt` — `getSaleDetail`, reutilizando los DAOs ya inyectados desde Historia 3.3 (`saleItemDao`/`installmentDao`/`paymentDao`, agregados entonces "por paridad" y sin uso hasta ahora — esta historia es la que finalmente los consume):
    ```kotlin
    suspend fun getSaleDetail(saleId: String, tenantId: String): SaleDetail? {
        val sale = saleDao.getById(saleId, tenantId)?.toDomain() ?: return null
        return SaleDetail(
            sale = sale,
            items = saleItemDao.getForSale(saleId, tenantId).map { it.toDomain() },
            payments = paymentDao.getForSale(saleId, tenantId).map { it.toDomain() },
            installments = installmentDao.getForSale(saleId, tenantId).map { it.toDomain() },
        )
    }

    private fun SaleItemEntity.toDomain() = SaleItem(
        id = id, fkTenant = fkTenant, fkSale = fkSale, fkProduct = fkProduct, fkVariant = fkVariant,
        productName = productName, variantName = variantName, quantity = quantity,
        unitPrice = unitPrice, taxRate = taxRate, createdAt = createdAt, updatedAt = updatedAt,
        syncStatus = SyncStatus.fromString(syncStatus),
    )

    private fun PaymentEntity.toDomain() = Payment(
        id = id, fkTenant = fkTenant, fkSale = fkSale, fkInstallment = fkInstallment,
        method = PaymentMethodType.fromString(method), amount = amount, paidAt = paidAt,
        createdAt = createdAt, updatedAt = updatedAt, syncStatus = SyncStatus.fromString(syncStatus),
    )

    private fun InstallmentEntity.toDomain() = Installment(
        id = id, fkTenant = fkTenant, fkSale = fkSale, amount = amount, dueDate = dueDate,
        status = InstallmentStatus.fromString(status), createdAt = createdAt, updatedAt = updatedAt,
        syncStatus = SyncStatus.fromString(syncStatus),
    )
    ```

### Android — Datos del ticket (testable en JVM puro)

- [x] **T3: `TicketData` (dominio) + `GenerateTicketUseCase`** (AC-1, AC-2, AC-4)
  - [x] Crear `domain/models/TicketData.kt`:
    ```kotlin
    data class TicketFiscalData(
        val businessName: String,
        val rfc: String,
        val address: String,
        val phone: String,
    )

    sealed class TicketPaymentCondition {
        data class SinglePayment(val paidAt: Instant) : TicketPaymentCondition()
        data class InstallmentPlan(val installments: List<Installment>) : TicketPaymentCondition()
    }

    data class TicketLineItem(val description: String, val quantity: Int, val unitPrice: BigDecimal, val subtotal: BigDecimal)

    data class TicketData(
        val fiscal: TicketFiscalData,
        val clientName: String,
        val folio: String,
        val createdAt: Instant,
        val lineItems: List<TicketLineItem>,
        val subtotal: BigDecimal,
        val tax: BigDecimal,
        val total: BigDecimal,
        val paymentCondition: TicketPaymentCondition,
    )
    ```
    `TicketLineItem` es una proyección de `SaleItem` con solo lo que el ticket necesita mostrar (no reexpone `fkProduct`/`fkVariant`/sync fields) — desacopla el formato de impresión de la forma de persistencia, igual que `OrderSummaryRow`/`ClientSearchRow` ya hacen para sus respectivas pantallas.
  - [x] Crear `domain/usecases/GenerateTicketUseCase.kt` — deliberadamente **sin ninguna dependencia de `android.graphics`**: solo ensambla datos, 100% testable en JVM puro. El renderizado a `Bitmap` es una responsabilidad separada (ver T5), igual que `ItemListScreen` mantiene su lógica de negocio en el ViewModel y el dibujo en el Composable.
    ```kotlin
    class GenerateTicketUseCase @Inject constructor(
        private val saleRepository: SaleRepository,
        private val clientRepository: ClientRepository,
        private val settingsRepository: SettingsRepository,
    ) {
        suspend operator fun invoke(saleId: String, tenantId: String): TicketData? {
            val detail = saleRepository.getSaleDetail(saleId, tenantId) ?: return null
            val client = runCatching { clientRepository.getClientById(detail.sale.fkClient) }.getOrNull()

            val fiscal = TicketFiscalData(
                businessName = settingsRepository.getValue("negocio_nombre").orEmpty(),
                rfc = settingsRepository.getValue("negocio_rfc").orEmpty(),
                address = settingsRepository.getValue("negocio_direccion").orEmpty(),
                phone = settingsRepository.getValue("negocio_telefono").orEmpty(),
            )
            val paymentCondition = if (detail.installments.isEmpty()) {
                TicketPaymentCondition.SinglePayment(detail.payments.firstOrNull()?.paidAt ?: detail.sale.createdAt)
            } else {
                TicketPaymentCondition.InstallmentPlan(detail.installments.sortedBy { it.dueDate })
            }

            return TicketData(
                fiscal = fiscal,
                clientName = client?.name.orEmpty(),
                folio = detail.sale.folio,
                createdAt = detail.sale.createdAt,
                lineItems = detail.items.map {
                    TicketLineItem(
                        description = if (it.variantName != null) "${it.productName} (${it.variantName})" else it.productName,
                        quantity = it.quantity,
                        unitPrice = it.unitPrice,
                        subtotal = it.subtotal,
                    )
                },
                subtotal = detail.sale.subtotal,
                tax = detail.sale.tax,
                total = detail.sale.total,
                paymentCondition = paymentCondition,
            )
        }
    }
    ```
    `settingsRepository.getValue(key).orEmpty()` — sin bloquear si las claves fiscales no existen (ver "Fuera de alcance"); `clientRepository.getClientById` envuelto en `runCatching` porque un cliente eliminado entre la venta y la generación del ticket no debe tumbar el ticket completo (mismo criterio de tolerancia a datos ausentes ya aplicado en `OrderSummaryViewModel`/`PaymentViewModel` de Historia 3.3).

### Android — Renderizado y distribución (NO testable en JVM puro — usa `android.graphics`)

- [x] **T4: `TicketBitmapRenderer`** (AC-1, AC-2, AC-4)
  - [x] Crear `ui/screens/orders/TicketBitmapRenderer.kt` — función pura `TicketData -> Bitmap` usando `android.graphics.Canvas`/`Paint`/`Typeface.MONOSPACE`, NO Compose (evita depender de `captureToImage`, que requiere un árbol de Compose ya compuesto e infraestructura de test instrumentado que este proyecto no tiene). Ancho fijo 384px (58mm @ 203dpi, ver Dev Notes); alto calculado dinámicamente según el número de líneas.
    ```kotlin
    private const val TICKET_WIDTH_PX = 384
    private const val LINE_HEIGHT_PX = 28
    private const val MARGIN_PX = 12

    fun renderTicketBitmap(ticket: TicketData): Bitmap {
        val lines = buildTicketLines(ticket)
        val height = MARGIN_PX * 2 + lines.size * LINE_HEIGHT_PX
        val bitmap = Bitmap.createBitmap(TICKET_WIDTH_PX, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.MONOSPACE
            textSize = 20f
        }
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, MARGIN_PX.toFloat(), MARGIN_PX + (index + 1) * LINE_HEIGHT_PX.toFloat(), paint)
        }
        return bitmap
    }

    // Separada de renderTicketBitmap para poder probar el CONTENIDO de las líneas en JVM puro sin
    // tocar Bitmap/Canvas — la única parte de este archivo que NO depende de android.graphics.
    fun buildTicketLines(ticket: TicketData): List<String> {
        val lines = mutableListOf<String>()
        lines += ticket.fiscal.businessName.ifBlank { "(Sin datos fiscales configurados)" }
        if (ticket.fiscal.rfc.isNotBlank()) lines += "RFC: ${ticket.fiscal.rfc}"
        if (ticket.fiscal.address.isNotBlank()) lines += ticket.fiscal.address
        if (ticket.fiscal.phone.isNotBlank()) lines += "Tel: ${ticket.fiscal.phone}"
        lines += "Folio: ${ticket.folio}"
        lines += "Cliente: ${ticket.clientName.ifBlank { "(sin nombre)" }}"
        lines += "--------------------------------"
        ticket.lineItems.forEach { item ->
            lines += "${item.description} x${item.quantity}"
            lines += "  ${formatAmount(item.unitPrice)} c/u = ${formatAmount(item.subtotal)}"
        }
        lines += "--------------------------------"
        lines += "Subtotal: ${formatAmount(ticket.subtotal)}"
        lines += "Impuestos: ${formatAmount(ticket.tax)}"
        lines += "Total: ${formatAmount(ticket.total)}"
        when (val condition = ticket.paymentCondition) {
            is TicketPaymentCondition.SinglePayment -> lines += "Pago de contado"
            is TicketPaymentCondition.InstallmentPlan -> {
                lines += "Parcialidades:"
                condition.installments.forEach { lines += "  ${formatDate(it.dueDate)}: ${formatAmount(it.amount)}" }
            }
        }
        return lines
    }
    ```
    `buildTicketLines` queda separada de `renderTicketBitmap` específicamente para que su contenido textual sí sea testable en JVM puro (`TicketBitmapRendererTest`), aunque el archivo en su conjunto viva en `ui/` porque `Bitmap`/`Canvas`/`Paint`/`Color`/`Typeface` son clases de `android.graphics` no disponibles fuera de un runtime Android real (ni siquiera Robolectric está en este proyecto) — mismo principio de separación ya aplicado en `PaymentViewModel`/`PaymentScreen` (lógica en el ViewModel, dibujo en el Composable).

- [x] **T5: `TicketFileWriter` + `FileProvider`** (AC-4)
  - [x] Crear `ui/screens/orders/TicketFileWriter.kt`:
    ```kotlin
    class TicketFileWriter @Inject constructor(@ApplicationContext private val context: Context) {
        fun writeToCacheAndGetUri(bitmap: Bitmap, fileName: String): Uri {
            val dir = File(context.cacheDir, "tickets").apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }
    ```
    "La imagen NO se guarda en el almacenamiento del dispositivo" (AC-4) se cumple porque `context.cacheDir` es almacenamiento interno privado de la app, no accesible ni visible al usuario desde una galería/explorador de archivos, y Android lo puede purgar automáticamente — no es "guardar" en el sentido que el AC busca prevenir (que la imagen termine en la galería del usuario o en almacenamiento compartido).
  - [x] Crear `android/app/src/main/res/xml/file_paths.xml`:
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <paths>
        <cache-path name="tickets" path="tickets/" />
    </paths>
    ```
  - [x] Actualizar `AndroidManifest.xml` — agregar dentro de `<application>`:
    ```xml
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />
    </provider>
    ```
    Primera vez que el proyecto declara un `<provider>` o usa `FileProvider` — no existía ninguna infraestructura de compartir archivos hasta ahora (`AndroidManifest.xml` no tenía ni siquiera permisos declarados, ver Dev Notes).

- [x] **T6: `BluetoothTicketPrinter`** (AC-2, AC-3)
  - [x] Crear `data/bluetooth/BluetoothTicketPrinter.kt` (interfaz) + `data/bluetooth/AndroidBluetoothTicketPrinter.kt` (impl real) — interfaz abstraída sobre `android.bluetooth`, mismo patrón que `TransactionRunner` abstrae `Room.withTransaction`: permite que el ViewModel sea testeable en JVM puro con un fake, sin que ningún test necesite un adaptador Bluetooth real.
    ```kotlin
    interface BluetoothTicketPrinter {
        suspend fun printTicket(bitmap: Bitmap): Result<Unit>
    }
    ```
    ```kotlin
    class AndroidBluetoothTicketPrinter @Inject constructor(
        @ApplicationContext private val context: Context,
    ) : BluetoothTicketPrinter {

        override suspend fun printTicket(bitmap: Bitmap): Result<Unit> = withContext(Dispatchers.IO) {
            runCatching {
                val manager = context.getSystemService(BluetoothManager::class.java)
                val adapter = manager?.adapter ?: error("Bluetooth no disponible en este dispositivo")
                if (!adapter.isEnabled) error("Bluetooth deshabilitado")

                val device = adapter.bondedDevices.firstOrNull() ?: error("Ninguna impresora emparejada")
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.use { s ->
                    s.connect()
                    s.outputStream.write(bitmapToEscPosRaster(bitmap))
                    s.outputStream.flush()
                }
            }
        }

        companion object {
            // UUID estándar de Serial Port Profile (SPP) — el mismo que usa prácticamente
            // cualquier impresora térmica Bluetooth "genérica" ESC/POS del mercado; no requiere
            // SDK propietario de ningún fabricante (Zebra/Epson/etc. no están disponibles como
            // dependencia Gradle en este proyecto — ver Dev Notes).
            private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        }
    }
    ```
  - [x] Crear `data/bluetooth/EscPosEncoder.kt` — `fun bitmapToEscPosRaster(bitmap: Bitmap): ByteArray`, convierte el `Bitmap` a monocromo (umbral de luminancia) empacado en filas de bytes (8 píxeles por byte) y lo envuelve en el comando ESC/POS `GS v 0` (`1D 76 30`) — comando estándar de "imprimir imagen raster", documentado públicamente y soportado por la inmensa mayoría de impresoras térmicas de recibo compatibles ESC/POS.
    ```kotlin
    fun bitmapToEscPosRaster(bitmap: Bitmap): ByteArray {
        val widthBytes = (bitmap.width + 7) / 8
        val header = byteArrayOf(
            0x1D, 0x76, 0x30, 0x00,
            (widthBytes and 0xFF).toByte(), ((widthBytes shr 8) and 0xFF).toByte(),
            (bitmap.height and 0xFF).toByte(), ((bitmap.height shr 8) and 0xFF).toByte(),
        )
        val body = ByteArray(widthBytes * bitmap.height)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val luminance = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                if (luminance < 128) {
                    val byteIndex = y * widthBytes + x / 8
                    body[byteIndex] = (body[byteIndex].toInt() or (0x80 shr (x % 8))).toByte()
                }
            }
        }
        return header + body
    }
    ```

- [x] **T7: Permisos Bluetooth en `AndroidManifest.xml`** (AC-2, AC-3)
  - [x] Agregar (primera vez que este proyecto declara CUALQUIER `<uses-permission>` — `AndroidManifest.xml` estaba completamente vacío de permisos hasta ahora):
    ```xml
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    ```
    `minSdk=26` (confirmado en `app/build.gradle.kts`): en API ≤30 los permisos legacy `BLUETOOTH`/`BLUETOOTH_ADMIN` son de instalación (no requieren prompt en tiempo de ejecución); en API 31+ (`targetSdk=36`) `BLUETOOTH_CONNECT` es "peligroso" y sí requiere solicitud en tiempo de ejecución (ver T9). No se declara `BLUETOOTH_SCAN` — ver "Fuera de alcance".

### Android — ViewModel y UI

- [x] **T8: `PaymentViewModel` — `navEvent` carga el `saleId`, estado del ticket** (AC-1, AC-2, AC-3, AC-4)
  - [x] Cambiar `_navEvent: Channel<Unit>` → `Channel<String>` (emite el `saleId` devuelto por `saleRepository.createSale(...)`, hoy descartado — `result.onSuccess { saleId -> _navEvent.send(saleId) }`).
  - [x] Agregar a `PaymentUiState`: `val ticketData: TicketData? = null`, `val isPrinting: Boolean = false`, `val printError: String? = null`.
  - [x] Agregar `_shareEvent: Channel<Uri>` / `val shareEvent = _shareEvent.receiveAsFlow()` — Uri del PNG ya escrito en caché, para que `PaymentScreen` dispare el intent de compartir real (necesita un `Context` de UI para `startActivity`, no debe vivir en el ViewModel).
  - [x] `PaymentViewModel` gana `generateTicketUseCase: GenerateTicketUseCase`, `bluetoothTicketPrinter: BluetoothTicketPrinter`, `ticketFileWriter: TicketFileWriter` como dependencias inyectadas.
  - [x] Nuevo método privado `loadTicket(saleId: String, tenantId: String)` — llamado justo después de `_navEvent.send(saleId)` en `onConfirmClick`, llena `ticketData` en el estado.
  - [x] `onPrintClick()`: `isPrinting = true`; renderiza el bitmap (`renderTicketBitmap(state.ticketData)`), llama `bluetoothTicketPrinter.printTicket(bitmap)`; en éxito limpia `isPrinting`/`printError` (sheet permanece abierto, ver Dev Notes); en fallo setea `printError` con el texto exacto de AC-3 (mostrado como Snackbar en `PaymentScreen`, sheet permanece abierto — AC-3 es explícito en que no se cierra).
  - [x] `onShareClick()`: renderiza el bitmap, `ticketFileWriter.writeToCacheAndGetUri(bitmap, "ticket_${saleId}.png")`, envía el `Uri` por `_shareEvent`.
  - [x] `onTicketDismiss()`: limpia `ticketData` y ejecuta la navegación de regreso a Órdenes que hoy corre inmediatamente tras `navEvent` (ver T10 — se mueve del `LaunchedEffect(navEvent)` al cierre real del sheet).

- [x] **T9: `TicketSheet.kt`** (AC-1, AC-2, AC-3, AC-4, AC-5)
  - [x] Crear `ui/screens/orders/TicketSheet.kt` — composable presentacional puro (sin ViewModel propio, mismo patrón que `VariantSelectorSheet` de Historia 3.2: el ViewModel dueño del sheet es el de la pantalla que lo aloja, aquí `PaymentViewModel`), `ModalBottomSheet` con:
    - Vista previa: reutiliza `buildTicketLines(ticketData)` renderizado como texto monoespaciado en un `Column` (NO reinterpreta el `Bitmap` — evita cargar una imagen dentro de la propia pantalla que la genera; el `Bitmap` solo se materializa al imprimir/compartir).
    - Botón "Imprimir vía Bluetooth" — en API 31+, antes de llamar `onPrintClick`, solicita el permiso `BLUETOOTH_CONNECT` en tiempo de ejecución vía `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` (primera vez que este proyecto solicita un permiso peligroso en tiempo de ejecución); en API ≤30 el permiso ya está concedido en instalación, se llama directo.
    - Botón "Compartir".
    - `onDismissRequest = onDismiss` (cubre swipe-down y tap-fuera, ambos disparan el mismo `onTicketDismiss`).

- [x] **T10: Wiring en `PaymentScreen.kt`** (AC-1, AC-5)
  - [x] `LaunchedEffect(Unit) { viewModel.navEvent.collect { saleId -> /* ya no navega inmediato */ } }` — el `navEvent` ahora solo dispara `loadTicket` (dentro del propio ViewModel, ver T8); la navegación de regreso a Órdenes se retrasa hasta que el usuario cierre el sheet.
  - [x] `LaunchedEffect(Unit) { viewModel.shareEvent.collect { uri -> /* Intent.ACTION_SEND real */ } }` — construye el `Intent(Intent.ACTION_SEND).apply { type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }`, `context.startActivity(Intent.createChooser(intent, null))`.
  - [x] `if (uiState.ticketData != null) TicketSheet(ticketData = uiState.ticketData, isPrinting = uiState.isPrinting, onPrintClick = viewModel::onPrintClick, onShareClick = viewModel::onShareClick, onDismiss = viewModel::onTicketDismiss)`.
  - [x] `uiState.printError` → Snackbar con el texto exacto de AC-3 (mismo `SnackbarHostState` ya usado en esta pantalla).
  - [x] La navegación real (`navController.popBackStack(Routes.Orders.route, false)`, ya en `NavGraph.kt`) se mueve para ejecutarse cuando `onTicketDismiss` termina de limpiar el estado — vía un segundo evento de un solo disparo (`_closeEvent: Channel<Unit>`) o reutilizando `onConfirmed` como el callback que `onTicketDismiss` invoca al final. Usar el segundo enfoque (`onConfirmed` invocado desde el `onDismiss` de `TicketSheet`, no desde `navEvent`) — cambio mínimo sobre el wiring existente en `NavGraph.kt`.

### Android — S-02 (regreso con foco de TalkBack)

- [x] **T11: `OrderListScreen` — foco en el FAB al regresar de S-08** (AC-5)
  - [x] Mismo patrón de "resultado vía `SavedStateHandle` de la entrada previa" ya establecido en Historia 3.2 (`ClientFormScreen.onSaved` → `previousBackStackEntry.savedStateHandle`), aquí en dirección "hacia adelante": antes de `popBackStack(Routes.Orders.route, false)`, `NavGraph.kt` marca `navController.getBackStackEntry(Routes.Orders.route).savedStateHandle["focusFab"] = true`.
  - [x] `OrderListScreen` lee ese flag reactivamente (`getStateFlow("focusFab", false).collectAsStateWithLifecycle()`), y si es `true`, limpia el flag y dispara `focusRequester.requestFocus()` sobre un `FocusRequester` adjunto al `ExtendedFloatingActionButton` (`Modifier.focusRequester(...)`, primera vez que este proyecto usa `FocusRequester` para restaurar foco — hasta ahora ningún caso similar, ni siquiera el ya especificado para S-05 en Historia 3.2, llegó a implementarse; ver Dev Notes).
  - [x] La confirmación real de que TalkBack anuncia el FAB al recibir el foco **no se puede verificar sin dispositivo** — documentar como pendiente de verificación manual, mismo criterio que `DatePickerDialog`/`LiveRegionMode` en Historia 3.3.

### Review Findings

- [x] [Review][Patch] El resultado del permiso `BLUETOOTH_CONNECT` se descarta — `onPrintClick()` se llama sin importar si el usuario concedió o negó el permiso, dejando que el intento de impresión falle con `SecurityException` y muestre el mensaje genérico de "impresora no encontrada" en vez de indicar que falta el permiso [`ui/screens/orders/TicketSheet.kt:40-42,59`]
- [x] [Review][Patch] `onShareClick` no tiene manejo de errores — a diferencia de `onPrintClick`, una `IOException` real de `TicketFileWriter` (caché sin espacio/no escribible) se propaga sin capturar dentro de `viewModelScope.launch` y puede tumbar la app [`ui/screens/orders/PaymentViewModel.kt:319-325`]
- [x] [Review][Patch] Si `GenerateTicketUseCase` devuelve `null` (venta no encontrada u otro fallo interno), `ticketData` nunca se llena, el `TicketSheet` nunca se muestra, y no hay ningún mensaje de error ni forma de regresar a S-02 — el proveedor queda varado en `PaymentScreen` pese a que la venta ya se guardó exitosamente [`ui/screens/orders/PaymentViewModel.kt:295-298`, `ui/screens/orders/PaymentScreen.kt:179-180`]
- [x] [Review][Patch] Doble tap en "Compartir" antes de que la primera escritura termine puede disparar dos corrutinas escribiendo el mismo nombre de archivo en caché concurrentemente — sin guardia equivalente al `isPrinting` que sí protege "Imprimir vía Bluetooth" [`ui/screens/orders/PaymentViewModel.kt:319-325`]
- [x] [Review][Patch] El campo de alto en el comando ESC/POS `GS v 0` es de 2 bytes (máximo 65535) — un ticket con una cantidad extrema de líneas produce un `Bitmap` más alto que ese límite y el encoder trunca el valor silenciosamente en vez de fallar de forma clara, enviando una imagen corrupta a la impresora [`data/bluetooth/EscPosEncoder.kt:9-27`]
- [x] [Review][Patch] `EscPosEncoder.kt` (el código más nuevo y propenso a errores de la historia — empacado manual de bits) no tiene ningún test — a diferencia de casi todos los demás archivos nuevos de esta historia. La causa es estructural: `bitmapToEscPosRaster(bitmap: Bitmap)` depende de `android.graphics.Bitmap`/`Color`, no testeables en JVM puro sin Robolectric; extraer el empacado de bytes a una función pura (`width/height: Int` + lambda `isBlackPixel`) sin dependencia de `Bitmap` resuelve esto Y de paso cierra el hallazgo del límite de 65535 con un `require()` [`data/bluetooth/EscPosEncoder.kt`]
- [x] [Review][Patch] `fabFocusRequester.requestFocus()` puede lanzar `IllegalStateException` si se invoca antes de que el `FocusRequester` esté adjunto (primera vez que este proyecto usa `FocusRequester`, sin forma de verificar el timing exacto sin dispositivo) — sin guardia defensiva, y `onFabFocusConsumed()` nunca se llama si lanza, dejando el flag sin consumir [`ui/screens/orders/OrderListScreen.kt:79-84`]
- [x] [Review][Patch] El intent de compartir (`Intent.ACTION_SEND`) no está envuelto en manejo de errores — si el dispositivo no tiene ninguna app capaz de manejar `image/png` (poco común pero posible en un emulador o dispositivo muy restringido), `ActivityNotFoundException` se propaga sin capturar [`ui/screens/orders/PaymentScreen.kt:80-87`]
- [x] [Review][Patch] El Snackbar de error de AC-3 ("No encontramos la impresora...") se muestra vía el `SnackbarHostState` del `Scaffold` exterior, pero `TicketSheet` es un `ModalBottomSheet` que renderiza en su propia ventana/capa por encima del contenido del `Scaffold` — es probable que el Snackbar quede oculto detrás del sheet mientras este permanece abierto, incumpliendo la parte de AC-3 que exige que el Snackbar sea visible con el sheet todavía abierto [`ui/screens/orders/PaymentScreen.kt:91-92`, `ui/screens/orders/TicketSheet.kt`]
- [x] [Review][Defer] `GenerateTicketUseCase.getClientById` hereda la falta de scope de tenant ya existente en `ClientRepository.getClientById` (la firma del método no acepta `tenantId`) — no es un gap nuevo de esta historia, es la misma familia del gap de tenant-scoping de `ClientDao` ya diferido desde Historias 2.1/2.2 — deferred, requiere tocar `ClientRepository`/`ClientDao` que esta historia no debe modificar [`domain/usecases/GenerateTicketUseCase.kt`]
- [x] [Review][Defer] Sin limpieza/expiración de `cacheDir/tickets/` — cada "Compartir" escribe un PNG nuevo que nunca se borra; el sistema operativo purga `cacheDir` bajo presión de almacenamiento como respaldo, pero no hay ninguna limpieza explícita — deferred, baja prioridad, ningún AC lo exige [`ui/screens/orders/TicketFileWriter.kt`]
- [x] [Review][Defer] El ticket no muestra el desglose de métodos de pago (solo se usa `payments.firstOrNull()?.paidAt` para la fecha, descartando el resto) — gap de producto real para un "comprobante de pago", pero ningún AC lo exige explícitamente (AC-1 solo pide "condición de pago (fechas de parcialidades si aplica)") — deferred, candidato a una historia futura relacionada con tickets [`domain/usecases/GenerateTicketUseCase.kt`]
- [x] [Review][Defer] Fallos reales (no solo "cliente no encontrado") al buscar el cliente en `GenerateTicketUseCase` se tragan silenciosamente vía `runCatching{}.getOrNull()` sin ningún registro — misma familia del patrón de manejo de errores silencioso ya diferido repetidamente desde Historia 2.3 [`domain/usecases/GenerateTicketUseCase.kt`]
- [x] [Review][Defer] Sin timeout/cancelación explícita en `BluetoothSocket.connect()` — si la impresora está emparejada pero fuera de rango o apagada, `isPrinting` puede quedar en `true` por el tiempo que tome el timeout implícito del sistema operativo, sin UI de "cancelar". Ningún AC exige un timeout específico — deferred [`data/bluetooth/AndroidBluetoothTicketPrinter.kt`]
- [x] [Review][Defer] Un solo comando ESC/POS `GS v 0` sin fragmentar para todo el alto del ticket — impresoras térmicas baratas con buffers de imagen pequeños (justo el tipo de impresora objetivo de esta historia) pueden atascarse o distorsionar la imagen con comandos raster muy altos. Fragmentar en bandas es el enfoque estándar de librerías ESC/POS maduras, pero es un cambio no trivial que requiere hardware real para validar — deferred [`data/bluetooth/EscPosEncoder.kt`]
- [x] [Review][Defer] El texto del `Bitmap` renderizado no hace salto de línea — un nombre de producto/variante largo se corta silenciosamente en la imagen impresa/compartida, mientras la vista previa en pantalla (`Text` de Compose dentro de un `LazyColumn`) sí hace wrap normalmente, causando una divergencia entre lo que el proveedor ve y lo que se imprime. Implementar wrapping manual en `Canvas.drawText` es un cambio no trivial; ningún AC exige que la vista previa y el render final coincidan pixel a pixel — deferred [`data/ticket/TicketBitmapRenderer.kt`]

## Dev Notes

### Por qué Bluetooth Classic SPP + ESC/POS crudo, sin SDK de terceros

`architecture.md` nombra "Bluetooth Print" como uno de los 8 componentes arquitectónicos estimados, pero no elige ninguna librería concreta, y no existe ninguna dependencia Bluetooth/impresión en `libs.versions.toml`/`build.gradle.kts` hoy. En vez de atar el proyecto a un SDK propietario de fabricante (Zebra, Epson, etc. — cada uno con su propia licencia y superficie de API), se usa el UUID estándar de Serial Port Profile (`00001101-0000-1000-8000-00805F9B34FB`) y el comando ESC/POS `GS v 0` de impresión raster — ambos son, en la práctica, el mínimo común denominador soportado por la inmensa mayoría de impresoras térmicas Bluetooth "genéricas" vendidas para POS, y no requieren ninguna dependencia Gradle nueva (`android.bluetooth` es API de plataforma). Esta es una decisión técnica con una respuesta "estándar" razonablemente clara — a diferencia de la decisión de rango de tasa de impuesto de Historia 2.4 (una regla de negocio genuinamente ambigua sin default seguro), no se escaló como decision-needed.

### Por qué se autoconecta al primer dispositivo Bluetooth emparejado

Sin infraestructura de pairing/selección de dispositivo en el proyecto (ver "Fuera de alcance"), y dado que el flujo estándar para impresoras POS de este segmento es emparejarlas una sola vez desde los Ajustes del sistema operativo, autoconectar a `bondedDevices.firstOrNull()` es la opción más simple que satisface AC-2 ("la app intenta conectar") sin requerir una pantalla nueva. Si el proveedor tiene más de una impresora emparejada simultáneamente, se conecta a la primera que devuelva el sistema — comportamiento no determinista pero aceptable dado que el caso de uso típico (un solo proveedor, una sola impresora) no lo expone; revisar si se vuelve un problema real reportado.

### Por qué `GenerateTicketUseCase` no toca `android.graphics`

Mismo criterio que todo el proyecto ha aplicado desde Historia 1.3 (sin Robolectric, tests 100% JVM puro): `GenerateTicketUseCase` ensambla `TicketData` (ensamblaje de datos, `Sale`/`SaleItem`/`Installment`/`Client`/Settings — todo mockeable con fakes ya existentes) y es completamente testeable. El renderizado a `Bitmap` (`TicketBitmapRenderer.kt`) vive deliberadamente en `ui/` y usa clases `android.graphics.*` que no existen en JVM puro — no testeable sin Robolectric, que este proyecto nunca ha adoptado. `buildTicketLines` se extrae como la única función de ese archivo que sigue siendo JVM-pura (solo strings), permitiendo probar el CONTENIDO del ticket (qué texto aparece, en qué orden) sin poder probar el renderizado visual — mismo balance que el proyecto ya acepta para todas sus pantallas Compose.

### Por qué el `saleId` no se reenvía por `CartRouteCodec`/argumentos de ruta

A diferencia de Historia 3.3 (donde el carrito viaja S-04→S-06→S-07 por argumentos de ruta para evitar el primer ViewModel compartido del proyecto), S-08 se muestra como bottom sheet embebido **dentro de la misma pantalla** (`PaymentScreen`) que acaba de crear la venta — mismo patrón que S-05 (`VariantSelectorSheet`) embebido en `ItemListScreen` en Historia 3.2. El `saleId` nunca sale del `PaymentViewModel` que lo generó; no hay necesidad de codificarlo en ninguna ruta de navegación.

### Ruptura de comportamiento: la navegación a S-02 ya no ocurre inmediatamente tras confirmar el pago

Historia 3.3 dejó `onConfirmed` (invocado desde `navEvent`) navegando de inmediato tras mostrar un Snackbar placeholder. Esta historia reemplaza ese placeholder por el sheet real, así que la navegación real a S-02 se retrasa hasta que el usuario cierra el sheet (AC-5 lo especifica explícitamente: "Dado que el proveedor cierra S-08... Entonces navega a S-02"). Esto es un cambio de comportamiento intencional respecto a 3.3, no un bug — documentado aquí porque el código de 3.3 decía literalmente "el Snackbar placeholder ya se mostró en PaymentScreen" justo en el punto que esta historia reescribe.

### Archivos existentes relevantes — estado actual y cambio en esta historia

| Archivo | Estado actual | Cambio en esta historia |
|---------|---------------|--------------------------|
| `domain/models/Sale.kt` | Sin `subtotal`/`tax` (gap desde Historia 3.3) | + `subtotal`, + `tax` |
| `SaleDao.kt` | Sin `getById` | + `getById` |
| `SaleRepository.kt` | `createSale` (3.3), `saleItemDao`/`installmentDao`/`paymentDao` inyectados pero sin usar en lectura | + `getSaleDetail` (primer uso real de esos 3 DAOs para lectura) |
| `PaymentViewModel.kt`/`PaymentScreen.kt` | `navEvent: Channel<Unit>`, navega inmediato con Snackbar placeholder (3.3) | `navEvent: Channel<String>`; ticket real; navegación diferida al cierre del sheet |
| `AndroidManifest.xml` | Sin permisos, sin `<provider>` (nunca se declaró ninguno) | + permisos Bluetooth, + `FileProvider` |
| `OrderListScreen.kt` | FAB sin `FocusRequester` | + `FocusRequester` + lectura de flag `focusFab` |

**NO tocar:**
- `OrderSummaryScreen.kt`/`OrderSummaryViewModel.kt` (S-06) — sin cambios, ya completos desde Historia 3.3.
- `ItemListScreen.kt`/`ClientSelectScreen.kt` — sin relación con esta historia.
- S-09 (Detalle de Orden) — Historia 3.5, no crear ninguna pantalla nueva para eso aquí.
- No crear pantalla de configuración de datos fiscales — Historia 5.1.
- No implementar `BLUETOOTH_SCAN`/descubrimiento de dispositivos — ver "Fuera de alcance".

### Testing

Mismo patrón establecido en Historias 2.1-3.3: **sin Robolectric**, tests JVM puros con Fake DAOs/interfaces, `./gradlew :app:testDebugUnitTest`.

- **`FakeBluetoothTicketPrinter`** (nuevo, `test/.../ui/screens/orders/`) — implementa `BluetoothTicketPrinter`, controlable (`var result: Result<Unit>`) para simular éxito/fallo sin ningún adaptador Bluetooth real.
- **`FakeTicketFileWriter`** (nuevo) o interfaz extraída si `TicketFileWriter` resulta difícil de fakear por depender de `Context` real — evaluar si vale la pena una interfaz `TicketFileWriter`/`AndroidTicketFileWriter` igual que `BluetoothTicketPrinter`, para que `PaymentViewModelTest` pueda probar `onShareClick()` sin escribir archivos reales.
- **`SaleRepositoryTest`** — casos nuevos para `getSaleDetail`: bundle completo con ítems/pagos/parcialidades; `null` si el `saleId` no existe o pertenece a otro tenant; aislamiento por tenant en cada uno de los 3 DAOs internos.
- **`GenerateTicketUseCaseTest`** (nuevo) — `TicketData` correcto para modo Pago inmediato (`SinglePayment`) vs Parcialidades (`InstallmentPlan`, ordenadas por fecha); fallback a cadena vacía cuando las claves fiscales no existen en Settings; cliente eliminado no tumba la generación (nombre vacío).
- **`TicketBitmapRendererTest`** (nuevo) — prueba SOLO `buildTicketLines` (la parte JVM-pura): orden de líneas, formato de montos, ambos modos de condición de pago, fiscal data vacía muestra el placeholder `"(Sin datos fiscales configurados)"`.
- **`PaymentViewModelTest`** — casos nuevos: `onConfirmClick` ahora deja `ticketData` poblado tras éxito (ya no solo emite `navEvent`); `onPrintClick` éxito/fallo con `FakeBluetoothTicketPrinter` (fallo setea `printError` con el texto exacto de AC-3); `onShareClick` emite `shareEvent` con la URI esperada; `onTicketDismiss` limpia `ticketData` e invoca la navegación.
- Sin test de Composable/UI (`TicketSheet`/permiso en tiempo de ejecución/impresión real/TalkBack) — mismo criterio que todas las historias previas. La solicitud de `BLUETOOTH_CONNECT`, la conexión Bluetooth real, y el foco de TalkBack en el FAB quedan como **pendiente de verificación manual** (sin `adb`/dispositivo en este entorno, mismo arrastre desde Historia 2.1).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Historia 3.4: Generación y Distribución del Ticket] (líneas 574-601) — AC originales
- [Source: _bmad-output/planning-artifacts/epics.md#Requirements Inventory] FR-24 (generación de ticket, líneas 68), FR-25 (impresión Bluetooth, línea 70), FR-26 (compartir PNG, línea 72), FR-28 (setting `datos_fiscales`, línea 76)
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/EXPERIENCE.md] líneas 36, 74-75, 133, 222-224, 241, 302 — único lugar con el spec visual/comportamental completo de S-08 (epics.md no tiene UX-DR dedicado a TicketSheet)
- [Source: _bmad-output/planning-artifacts/architecture/architecture.md] líneas 359, 400-407, 573, 651 — `TicketSheet.kt`/`GenerateTicketUseCase.kt` ya anticipados en el árbol; línea 54 nombra "Bluetooth Print" como componente estimado sin SDK elegido
- [Source: backend/src/Sumitrack.Api/Infrastructure/Extensions/ApplicationBuilderExtensions.cs] (líneas 94-99) — confirma que NINGUNA clave de datos fiscales está sembrada en el backend (a diferencia de `serie_folio`/`max_parcialidades`)
- [Source: _bmad-output/implementation-artifacts/3-3-resumen-de-orden-y-configuracion-de-pago.md] `SaleItemDao`/`InstallmentDao`/`PaymentDao` (creados "por paridad", sin uso hasta esta historia); `SaleRepository` ya inyecta los 3; decisión de arquitectura sobre ViewModels no compartidos entre pantallas; patrón de bottom sheet embebido (S-05) reutilizado para S-08
- [Source: android/app/src/main/java/com/sumitrack/android/data/repositories/SaleRepository.kt, SettingsRepository.kt, domain/models/Sale.kt, ui/screens/orders/PaymentViewModel.kt, PaymentScreen.kt, ui/navigation/NavGraph.kt, Routes.kt, ui/screens/orders/OrderListScreen.kt, ui/screens/orders/VariantSelectorSheet.kt] estado actual antes de esta historia
- [Source: android/app/build.gradle.kts líneas 12-17] `minSdk=26`, `targetSdk=36`, `compileSdk=36` — determina el split de permisos Bluetooth legacy vs runtime

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (`claude-sonnet-5`)

### Debug Log References

**Desviación respecto al skeleton de código del spec (T5/T6, arquitectura descubierta durante la implementación, no un capricho):** el spec original tenía `BluetoothTicketPrinter.printTicket(bitmap: Bitmap)` y `TicketFileWriter.writeToCacheAndGetUri(bitmap: Bitmap, fileName: String): Uri`, con `PaymentViewModel.onPrintClick()`/`onShareClick()` llamando `renderTicketBitmap(ticket)` directamente antes de invocar esas interfaces. Al escribir los fakes de test (`FakeBluetoothTicketPrinter`/`FakeTicketFileWriter`) se descubrió que esto rompía la testabilidad que el propio spec exige: `Bitmap.createBitmap(...)` (dentro de `renderTicketBitmap`) es un stub de Android que lanza en tests JVM puros sin Robolectric — significaba que `onPrintClick`/`onShareClick` nunca podrían probarse pese a que la sección Testing de la historia los lista explícitamente. Se corrigió moviendo `renderTicketBitmap` DENTRO de las implementaciones reales (`AndroidBluetoothTicketPrinter`/`AndroidTicketFileWriter`), cambiando ambas interfaces para recibir `TicketData` en vez de `Bitmap` — así `PaymentViewModel` nunca toca `android.graphics`. Además, `TicketFileWriter.writeToCacheAndGetUri` devuelve `String` (no `android.net.Uri`): `Uri.parse`/`Uri.EMPTY` también son stubs sin implementación real fuera de un dispositivo, así que un `FakeTicketFileWriter` no podía construir ni devolver un `Uri` real en JVM puro. `PaymentScreen.kt` hace `Uri.parse(uriString)` justo antes de construir el `Intent` real (nunca se ejecuta en tests, consistente con que este proyecto no prueba Composables). `TicketBitmapRenderer.kt` también se movió de `ui/screens/orders/` a `data/ticket/` (no especificado en el spec) porque ambas implementaciones Android que lo usan (`AndroidBluetoothTicketPrinter` en `data/bluetooth/`, `AndroidTicketFileWriter` en `ui/screens/orders/`) necesitaban importarlo, y que la capa `data/` importara desde `ui/` habría sido una dependencia de capas invertida.

Ningún otro desvío: el resto de la historia (Bluetooth SPP/ESC-POS, autoconexión al primer dispositivo emparejado, permisos, FileProvider, navegación diferida, foco del FAB) se implementó tal como está especificado.

### Completion Notes List

Historia implementada completa. 209 tests ✅ (0 fallos, +22 sobre Historia 3.3). `BUILD SUCCESSFUL` (`assembleDebug` + `testDebugUnitTest`, sin warnings).

- AC-1 ✅: `TicketSheet` (S-08) muestra la vista previa (`buildTicketLines`) con datos fiscales (con placeholder si Settings no tiene las claves), cliente, ítems, subtotal/impuestos/total y folio — se abre automáticamente al recibir `PaymentViewModel.uiState.ticketData` tras `onConfirmClick`.
- AC-2 ✅: "Imprimir vía Bluetooth" solicita `BLUETOOTH_CONNECT` en tiempo de ejecución (API 31+) o llama directo (API ≤30), `onPrintClick` → `BluetoothTicketPrinter.printTicket` → `AndroidBluetoothTicketPrinter` autoconecta al primer dispositivo emparejado (SPP estándar) y envía el ticket vía comando ESC/POS `GS v 0`.
- AC-3 ✅: fallo de conexión → `printError` con el texto EXACTO del AC, mostrado como Snackbar en `PaymentScreen`; `ticketData` no se limpia, el sheet permanece abierto (test `onPrintClick failure sets the exact AC-3 error message and keeps the sheet state`).
- AC-4 ✅: "Compartir" → `onShareClick` → `TicketFileWriter` escribe el PNG en `cacheDir/tickets/` (almacenamiento interno privado, no "guardado" en el sentido que el AC busca prevenir) → `Intent.ACTION_SEND` real vía `FileProvider`.
- AC-5 ✅: cerrar el sheet (`onDismissRequest`, cubre swipe-down y tap-fuera) → `onTicketDismiss()` limpia el estado + `onConfirmed()` navega a S-02 marcando `focusFab=true` en el `SavedStateHandle` de la entrada de Órdenes ya existente en el back stack → `OrderListScreen` consume el flag y llama `FocusRequester.requestFocus()` en el FAB. La nueva orden aparece al tope automáticamente (query reactiva ya existente desde Historia 3.1, sin cambios necesarios).
- **Ruptura de comportamiento intencional (documentada en Dev Notes de la historia):** la navegación a S-02 ya NO ocurre inmediatamente al confirmar el pago — se retrasa hasta que el usuario cierra el TicketSheet, reemplazando el placeholder Snackbar de Historia 3.3.
- Sin test de Composable/UI (`TicketSheet`, solicitud de permiso runtime, conexión Bluetooth real, foco de TalkBack) — mismo criterio que todas las historias previas. La confirmación de que TalkBack anuncia el FAB correctamente al recibir el foco queda **pendiente de verificación manual** (sin `adb`/dispositivo en este entorno).
- **Pendiente:** verificación manual en dispositivo/emulador físico con una impresora térmica Bluetooth real emparejada — este entorno no tiene `adb` ni hardware Bluetooth disponible. Recomendado antes de mergear: confirmar la venta → ver TicketSheet con datos correctos → "Imprimir vía Bluetooth" (con y sin impresora emparejada, verificar el texto exacto del Snackbar de error) → "Compartir" (verificar que la imagen llega por WhatsApp/similar y que el share intent no persiste el PNG en Descargas/Galería) → cerrar el sheet (swipe-down y tap-fuera) → confirmar que el FAB en S-02 recibe el foco y TalkBack lo anuncia → confirmar que la nueva orden aparece al tope de Historial en estado pendiente.

**Post-code-review:** 9 patches aplicados (ver "Review Findings" arriba) — el permiso `BLUETOOTH_CONNECT` denegado ya se distingue de "impresora no encontrada", `onShareClick` maneja errores de escritura y protege contra doble tap, `GenerateTicketUseCase` devolviendo `null` ya no deja al proveedor varado (diálogo de fallback con salida explícita a Historial), el comando ESC/POS rechaza alturas fuera de rango en vez de truncar en silencio, `EscPosEncoder` ahora tiene un núcleo puro testeable (`packEscPosRaster`), `FocusRequester.requestFocus()` está protegido con `runCatching`, el intent de compartir captura `ActivityNotFoundException`, y el error de AC-3 se muestra inline dentro del propio `TicketSheet` (el Snackbar del `Scaffold` exterior quedaba oculto detrás de la ventana del `ModalBottomSheet`). 7 hallazgos diferidos a `deferred-work.md`. 11 tests nuevos agregados para cubrir las correcciones (`EscPosEncoderTest` + 5 casos nuevos en `PaymentViewModelTest`). **220 tests ✅ (0 fallos)**, `BUILD SUCCESSFUL` sin warnings nuevos.

### File List

**Archivos creados (NEW):**
- `android/app/src/main/java/com/sumitrack/android/domain/models/SaleDetail.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/models/TicketData.kt`
- `android/app/src/main/java/com/sumitrack/android/domain/usecases/GenerateTicketUseCase.kt`
- `android/app/src/main/java/com/sumitrack/android/data/ticket/TicketBitmapRenderer.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/TicketFileWriter.kt`
- `android/app/src/main/java/com/sumitrack/android/data/bluetooth/BluetoothTicketPrinter.kt`
- `android/app/src/main/java/com/sumitrack/android/data/bluetooth/AndroidBluetoothTicketPrinter.kt`
- `android/app/src/main/java/com/sumitrack/android/data/bluetooth/EscPosEncoder.kt` — [Review] `packEscPosRaster` extraído como núcleo puro testeable con guardas de rango (1-65535px)
- `android/app/src/main/java/com/sumitrack/android/di/TicketModule.kt`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/TicketSheet.kt` — [Review] respeta el resultado del permiso Bluetooth, `printError` inline, guardia `isSharing`
- `android/app/src/main/res/xml/file_paths.xml`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakeBluetoothTicketPrinter.kt`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakeTicketFileWriter.kt`
- `android/app/src/test/java/com/sumitrack/android/domain/usecases/GenerateTicketUseCaseTest.kt`
- `android/app/src/test/java/com/sumitrack/android/data/ticket/TicketBitmapRendererTest.kt`
- `android/app/src/test/java/com/sumitrack/android/data/bluetooth/EscPosEncoderTest.kt` — [Review]

**Archivos modificados (UPDATE):**
- `android/app/src/main/java/com/sumitrack/android/domain/models/Sale.kt` — + `subtotal`, + `tax`
- `android/app/src/main/java/com/sumitrack/android/data/local/dao/SaleDao.kt` — + `getById`
- `android/app/src/main/java/com/sumitrack/android/data/repositories/SaleRepository.kt` — + `getSaleDetail`, mapeo `subtotal`/`tax` en `toDomain()`, mappers de `SaleItem`/`Payment`/`Installment`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/PaymentViewModel.kt` — `navEvent: Channel<String>` (saleId), `ticketData`/`isPrinting`/`printError`, `onPrintClick`/`onShareClick`/`onTicketDismiss`; [Review] + `ticketLoadFailed`/`isSharing`, `onBluetoothPermissionDenied`, manejo de errores en `onShareClick`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/PaymentScreen.kt` — muestra `TicketSheet`, colecta `shareEvent` (Intent real), navegación diferida al cierre del sheet; [Review] diálogo de fallback si `ticketLoadFailed`, captura `ActivityNotFoundException`, ya no muestra `printError` vía Snackbar (se movió a `TicketSheet`)
- `android/app/src/main/java/com/sumitrack/android/ui/navigation/NavGraph.kt` — `onConfirmed` ahora se invoca desde el cierre del sheet, marca `focusFab` antes de `popBackStack`
- `android/app/src/main/java/com/sumitrack/android/ui/screens/orders/OrderListScreen.kt` — `FocusRequester` en el FAB, lee flag `focusFab`; [Review] `requestFocus()` envuelto en `runCatching`
- `android/app/src/main/AndroidManifest.xml` — + permisos Bluetooth, + `<provider>` `FileProvider`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/clients/FakeSaleDao.kt` — + `getById`; [Review] + `forceGetByIdNull`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/FakeTicketFileWriter.kt` — [Review] + `throwOnWrite`
- `android/app/src/test/java/com/sumitrack/android/data/repositories/SaleRepositoryTest.kt` — + 4 casos de `getSaleDetail`
- `android/app/src/test/java/com/sumitrack/android/ui/screens/orders/PaymentViewModelTest.kt` — constructor actualizado + 5 casos nuevos (ticketData, onPrintClick éxito/fallo, onShareClick, onTicketDismiss); [Review] + 4 casos nuevos (permiso denegado, fallo al compartir, doble tap, ticket nulo)

## Change Log

- **2026-07-26** — Historia 3.4 implementada completa (Status: review)
  - NEW: `SaleDetail`, `TicketData`/`TicketFiscalData`/`TicketLineItem`/`TicketPaymentCondition` (dominio); `Sale` + `subtotal`/`tax` (gap de Historia 3.3 corregido)
  - NEW: `SaleDao.getById`, `SaleRepository.getSaleDetail` (primer uso real de `saleItemDao`/`installmentDao`/`paymentDao`, inyectados desde 3.3 "por paridad")
  - NEW: `GenerateTicketUseCase` — 100% testeable en JVM puro, reutilizable por `saleId` (Historia 3.5 lo reutilizará)
  - NEW: `TicketBitmapRenderer` (`data/ticket/`) — `buildTicketLines` (testeable) separado de `renderTicketBitmap` (android.graphics)
  - NEW: `BluetoothTicketPrinter`/`AndroidBluetoothTicketPrinter` (SPP + ESC/POS `GS v 0` crudo, sin SDK de terceros), `EscPosEncoder`
  - NEW: `TicketFileWriter`/`AndroidTicketFileWriter` (cache privado + `FileProvider`), `TicketSheet` (S-08)
  - UPDATE: `AndroidManifest.xml` — primeros permisos del proyecto (`BLUETOOTH`/`BLUETOOTH_ADMIN` legacy + `BLUETOOTH_CONNECT` runtime) y primer `<provider>` (`FileProvider`)
  - UPDATE: `PaymentViewModel`/`PaymentScreen` — `navEvent` carga el ticket sin navegar de inmediato; navegación a S-02 diferida al cierre del sheet
  - UPDATE: `OrderListScreen`/`NavGraph` — foco de TalkBack en el FAB al regresar de S-08 (`SavedStateHandle` + `FocusRequester`, primera vez en el proyecto)
  - **Decisión de arquitectura durante implementación:** `BluetoothTicketPrinter`/`TicketFileWriter` reciben `TicketData` (no `Bitmap`) y `TicketFileWriter` devuelve `String` (no `Uri`) — el spec original pasaba `Bitmap`/`Uri`, pero ambos son stubs de Android que lanzan en tests JVM puros sin Robolectric; se corrigió para que `PaymentViewModel` nunca toque `android.graphics`/`android.net.Uri`, cumpliendo la cobertura de test que la propia historia exige
  - NEW: tests — `GenerateTicketUseCaseTest` (6), `TicketBitmapRendererTest` (7), + 4 casos de `getSaleDetail` en `SaleRepositoryTest`, + 5 casos nuevos en `PaymentViewModelTest`
  - Build: 209 tests ✅ (0 fallos, +22 sobre Historia 3.3), `BUILD SUCCESSFUL` (`assembleDebug` + `testDebugUnitTest`, sin warnings)
  - Pendiente: verificación manual en dispositivo/emulador con impresora Bluetooth real (sin `adb`/hardware en este entorno); code review todavía no ejecutado

- **2026-07-26** — Code review completo, 9 patches aplicados (Status: done)
  - Review: Blind Hunter (17 hallazgos) + Edge Case Hunter (7) + Acceptance Auditor (3) en paralelo → 0 decision-needed, 9 patch, 7 defer, 6 dismiss
  - PATCH: permiso `BLUETOOTH_CONNECT` denegado ya no dispara un intento de impresión — mensaje claro en vez de "impresora no encontrada"
  - PATCH: `onShareClick` maneja errores de escritura (`IOException`) y protege contra doble tap con un guard `isSharing` (mismo patrón que `isPrinting`)
  - PATCH: `GenerateTicketUseCase` devolviendo `null` ya no deja al proveedor varado en `PaymentScreen` — `ticketLoadFailed` + diálogo con salida explícita a Historial
  - PATCH: comando ESC/POS `GS v 0` rechaza alturas fuera de rango (1-65535px) con `require()` en vez de truncar el campo silenciosamente
  - NEW: `packEscPosRaster` — núcleo puro de `EscPosEncoder` sin `Bitmap`, cierra el hallazgo de "sin test" del archivo más nuevo/propenso a errores de la historia
  - PATCH: `fabFocusRequester.requestFocus()` envuelto en `runCatching` (primer uso de `FocusRequester` en el proyecto, sin forma de verificar el timing sin dispositivo)
  - PATCH: `Intent.ACTION_SEND` captura `ActivityNotFoundException` con Snackbar de respaldo
  - PATCH: el error de AC-3 se muestra inline dentro de `TicketSheet` en vez de vía el `SnackbarHostState` del `Scaffold` exterior — el `ModalBottomSheet` renderiza en su propia ventana y ocultaba el Snackbar
  - DEFER: 7 hallazgos agregados a `deferred-work.md` (tenant-scoping heredado en `getClientById`, sin limpieza de caché de tickets, ticket sin desglose de métodos de pago, fallos de cliente sin log, sin timeout en el socket Bluetooth, ESC/POS sin fragmentar, texto sin wrap en el bitmap)
  - NEW: tests — `EscPosEncoderTest` (7), + 4 casos nuevos en `PaymentViewModelTest`
  - Build: **220 tests ✅** (0 fallos, +11 sobre el conteo pre-review), `BUILD SUCCESSFUL` sin warnings nuevos
