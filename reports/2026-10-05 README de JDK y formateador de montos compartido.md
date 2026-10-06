# Reporte de Sesión — README de JDK y formateador de montos compartido

**Fecha:** 2026-10-05
**Origen:** ítems de deuda de la retrospectiva de Epic 5 (#1 documentar el rango de JDK, #7 formateador de montos compartido)

---

## Resumen Ejecutivo

Backlog de `epics.md` agotado; se atendieron dos ítems de la retro. Estado de trabajo: **sin commitear**.

**1. README.** Decía "JDK 17 (incluido en Android Studio)", falso desde que el JBR pasó a JDK 25. Ahora documenta el rango real (JDK 17–23, recomendado 21; Gradle 8.13 / AGP 8.10.1), el síntoma con JDK 25, el `JAVA_HOME` de Homebrew, cómo correr contra la API local (`--urls http://localhost:5600`, `adb reverse`, el puerto 5000 ocupado por AirPlay en macOS) y las pruebas e2e (Maestro, `verify-sync.sh`).

**2. `formatMoney`.** La voz de UX pide "$1,250.00" y la app mostraba "$1250.00". La retro hablaba de 3 copias privadas de `formatAmount`; eran **13** (pantallas, tarjetas `ClientCard`/`ProductCard`/`OrderCard`, ticket y recordatorio, con 5 nombres distintos). Se centralizaron en `domain/format/MoneyFormat.kt`: `"$" + String.format(Locale.US, "%,.2f", amount)`. `Locale.US` fijo a propósito (con el locale del dispositivo, en es-ES saldría "1.250,00"); mismo redondeo HALF_UP y mismo signo en negativos (`$-1,250.00`) que el formato anterior.

## Verificación

- `MoneyFormatTest` (6 tests): miles, <1000, HALF_UP, decimales faltantes, independencia de locale (es-ES, de-DE), negativos.
- `ReminderContentBuilderTest` fijaba el formato defectuoso (`$1250.00`) y se actualizó a `$1,250.00`; los tests del ticket usan montos <1000 y no cambian.
- **422 tests, 0 fallos** (416 + 6); `assembleDebug` OK. Diff de las 13 sustituciones revisado a mano antes de compilar (lección de la retro de Epic 5).
- **Verificado en dispositivo** (ver Adenda): el flujo `maestro/02-producto-alta.yaml` crea el producto a $1,250 y asserta `.*1,250.00.*`; pasa.

## Archivos

Nuevos: `domain/format/MoneyFormat.kt`, `MoneyFormatTest.kt`. Modificados: `README.md`, `maestro/02-producto-alta.yaml`, `deferred-work.md`, `ReminderContentBuilderTest.kt` y 12 archivos de producción (`ClientProfileScreen`, `AgendaScreen`, `OrderSummaryScreen`, `OrderDetailScreen`, `ClientSelectScreen`, `ItemListScreen`, `PaymentScreen`, `ProductCard`, `OrderCard`, `ClientCard`, `TicketBitmapRenderer`, `ReminderContentBuilder`).

## Próximos pasos

- Con el teléfono y la API arriba: `cd android/maestro && ./run.sh 02-producto-alta.yaml` (y la corrida completa).
- Pendientes de la retro de Epic 5: error de Room mostrado como empty state (decisión de proyecto), checklist de rutas de fallo en `create-story`, sesión de verificación en dispositivo (WorkManager, permiso `POST_NOTIFICATIONS`, TalkBack), retrospectiva opcional de Epic 4.


---

## Adenda — primera verificación real en un segundo dispositivo (Samsung SM_G781B, Android 13, 360dp)

Se conectó un dispositivo; se levantó la API local en segundo plano (PostgreSQL ya estaba arriba) y se corrió la suite completa de Maestro. Era la primera vez que la app corría en algo distinto del Pixel, y salieron **dos defectos reales** que el Pixel ocultaba:

1. **Hoja del ticket cortada (bug de usabilidad).** Flujos 03 y 04 fallaron: tras "Confirmar Pago" la `ModalBottomSheet` del ticket abría **parcialmente expandida** y "Compartir" quedaba tapado hasta arrastrar la hoja (en 360dp hay menos dp de alto que en el Pixel). Fix: `rememberModalBottomSheetState(skipPartiallyExpanded = true)` en `TicketSheet.kt`. Flujo 03 y 04 pasan.
2. **Contraste en superficies `primaryContainer` (accesibilidad).** Visto en la celda de la Agenda con cobros y en el texto de los FAB: el tema define `primaryContainer = #3949AB` pero no `onPrimaryContainer`, así que Material ponía su morado oscuro por defecto: **2.22:1** (WCAG pide 4.5:1). Fix: `onPrimaryContainer = OnPrimary` (blanco, **7.73:1**; coincide con DESIGN.md para el chip activo) en `Theme.kt`. Afecta a los 3 FAB, la celda de la Agenda y el badge "Variantes". Verificado con capturas.

**Verificación de Epic 5 en dispositivo (la mayor superficie pendiente de la retro, ítem 10) — ya hecha en parte:**
- **Recordatorio real:** el job de WorkManager aparece en `JobScheduler` con solo restricción de tiempo (`TIMING_DELAY`, **sin restricción de red**, como diseñó 5.2 para funcionar offline). Forzado con `cmd jobscheduler run -f`, la notificación llega con el cliente como título, "$100.00 · vence el 21 nov 2026" y el **ícono propio** en la barra; tocarla abre la app (`MainActivity`).
- **Permiso `POST_NOTIFICATIONS`:** concedido en el dispositivo (Android 13).
- **Agenda a 360dp:** cuadrícula legible, "hoy" marcado, celda con conteo, lista del día con folio/monto/estado.
- **Sin verificar aún:** TalkBack por celda, escala de fuente 200 % (requiere cambiar un ajuste del sistema del teléfono), el disparo a las 9:00 sin forzar.

Suite en el Samsung tras los fixes: 00, 01, 02, 05 (corrida completa) y 03, 04 (tras el fix) en verde; 422 tests unitarios, 0 fallos. Observación menor: en este dispositivo el mes abreviado sale "sept" (ICU del fabricante) vs "sep" en el Pixel; cosmético.
