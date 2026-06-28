---
status: draft
updated: 2026-06-26
platform: Android (Jetpack Compose)
system: Material Design 3

colors:
  primary: "#1A237E"
  primary-variant: "#3949AB"
  on-primary: "#FFFFFF"
  background: "#F0F0F5"
  surface: "#FFFFFF"
  on-surface: "#1A1A2E"
  on-surface-variant: "#6B6B80"
  error: "#B00020"
  status-paid: "#2E7D32"
  status-pending: "#F57F17"
  status-overdue: "#AD1457"
  status-cancelled: "#9E9E9E"
  sync-ok: "#00BCD4"
  sync-pending: "#FF7043"
  outline: "#E8E8EE"
  scrim: "rgba(0,0,0,0.5)"

typography:
  font-family: Roboto
  display-large: { size: 28sp, weight: 700, tracking: -0.5 }
  title-large: { size: 22sp, weight: 700, tracking: -0.3 }
  title-medium: { size: 18sp, weight: 700, tracking: -0.3 }
  body-large: { size: 15sp, weight: 600, tracking: 0 }
  body-medium: { size: 14sp, weight: 400, tracking: 0 }
  body-small: { size: 13sp, weight: 400, tracking: 0 }
  label-large: { size: 12sp, weight: 700, tracking: 0.5 }
  label-small: { size: 12sp, weight: 700, tracking: 0.5 }

rounded:
  card: 16dp
  button: 12dp
  chip: 20dp
  bottom-sheet: 28dp
  input-field: 10dp
  badge: 20dp

spacing:
  screen-horizontal: 16dp
  card-gap: 10dp
  card-padding: 14dp 16dp
  section-gap: 24dp
  list-item-height: 64dp
  fab-margin: 16dp
---

## Brand & Style

Sumitrack es una herramienta de trabajo de campo para proveedores B2B. La marca proyecta **confianza y eficiencia**: el usuario sabe en segundos cuánto le deben sus clientes y puede registrar una venta mientras el cliente espera. No hay lugar para la ambigüedad visual.

El estilo es **sobrio y directo**: superficies limpias, jerarquía tipográfica clara, y señales de estado inconfundibles. El índigo transmite profundidad y seriedad; el cian (`{colors.sync-ok}`) está reservado exclusivamente para el estado de sincronización. El color se usa para comunicar, no para decorar.

La voz visual es la de una app que respeta el tiempo de Roberto: mínima fricción, máxima claridad.

→ Referencia visual de la dirección elegida: `.working/direction-c-indigo-moderno.html`. Los spines ganan en conflicto con cualquier artefacto visual.

## Colors

### Paleta principal

| Token | Valor | Uso |
|---|---|---|
| `colors.primary` | `#1A237E` | App bar, pantallas de login, botón primario outline |
| `colors.primary-variant` | `#3949AB` | FAB, botones de acción primaria, top app bar variant |
| `colors.on-primary` | `#FFFFFF` | Texto e iconos sobre primario |
| `colors.background` | `#F0F0F5` | Fondo de pantallas con listas |
| `colors.surface` | `#FFFFFF` | Cards, bottom sheets, dialogs |
| `colors.on-surface` | `#1A1A2E` | Texto principal sobre superficie |
| `colors.on-surface-variant` | `#6B6B80` | Texto secundario, placeholders, fechas |
| `colors.outline` | `#E8E8EE` | Bordes de cards, separadores, dividers |

### Colores de estado

| Token | Valor | Estado |
|---|---|---|
| `colors.status-paid` | `#2E7D32` | Liquidado — verde semántico |
| `colors.status-pending` | `#F57F17` | Parcial / Pendiente — ámbar de advertencia |
| `colors.status-overdue` | `#AD1457` | Atraso / Vencido — rosa error |
| `colors.status-cancelled` | `#9E9E9E` | Cancelado — gris neutro |
| `colors.sync-ok` | `#00BCD4` | Exclusivo: registro sincronizado con la nube |
| `colors.sync-pending` | `#FF7043` | Exclusivo: registro pendiente de sync — solo para íconos, no texto |
| `colors.error` | `#B00020` | Error del sistema, validaciones críticas |

Los estados de órdenes utilizan fondo tonal claro (10% opacidad del color de estado) y texto en el color de estado a plena opacidad.

`{colors.sync-pending}` (#FF7043) tiene contraste 3.2:1 sobre blanco — cumple el umbral de íconos (3:1) pero no el de texto (4.5:1). No usar como color de texto de badge.

### Contraste WCAG AA declarado

Los siguientes pares críticos cumplen el mínimo de 4.5:1 para texto y 3:1 para íconos:

| Par | Ratio approx. | Uso |
|---|---|---|
| `on-surface` (#1A1A2E) / `surface` (#FFFFFF) | 19:1 | Texto principal en cards |
| `on-primary` (#FFFFFF) / `primary` (#1A237E) | 12:1 | Texto en app bar |
| `on-primary` (#FFFFFF) / `primary-variant` (#3949AB) | 8.5:1 | Texto en FAB y botones |
| `status-overdue` (#AD1457) / fondo tonal 10% | 5.2:1 | Texto en badge de atraso |
| `sync-ok` (#00BCD4) / `surface` (#FFFFFF) | 2.5:1 | Solo ícono (umbral 3:1 no cumplido — usar sobre fondo oscuro o aumentar tamaño ≥ 18sp) |
| `status-paid` (#2E7D32) / fondo tonal 10% | 4.6:1 | Texto en badge de pagado |

**Nota:** `{colors.sync-ok}` sobre `{colors.surface}` blanco tiene contraste insuficiente para texto pero aceptable para ícono de 20dp o mayor. No usar como color de texto.

## Typography

Tipografía: **Roboto** (sistema nativo Android). Se heredan las variantes de Material 3 Type Scale sin modificación.

| Rol | Token | Tamaño | Peso | Uso |
|---|---|---|---|---|
| Display | `typography.display-large` | 28sp | 700 | Saldo total destacado en perfil de cliente (S-12), monto en empty state prominence |
| App bar | `typography.title-large` | 22sp | 700 | Título en app bar |
| Montos | `typography.title-medium` | 18sp | 700 | Montos en cards, totales de orden |
| Nombres | `typography.body-large` | 15sp | 600 | Nombre de cliente, nombre de ítem, valor del stepper |
| Secundario | `typography.body-medium` | 14sp | 400 | Descripción secundaria, datos de cliente |
| Etiquetas | `typography.body-small` | 13sp | 400 | Folios, fechas |
| Chips | `typography.label-large` | 12sp | 700 | Texto de badge de estado, etiqueta de chip activo |
| Micro | `typography.label-small` | 12sp | 700 | Etiquetas de chips de filtro (mínimo de pantalla) |

El tamaño mínimo de texto en pantalla es 12sp. El sistema respeta `fontScale` del sistema operativo.

## Layout & Spacing

- Margen horizontal de pantalla: `{spacing.screen-horizontal}` (16dp) en ambos lados.
- Las listas de cards usan `{spacing.card-gap}` (10dp) entre elementos.
- El padding interno de cada card es `{spacing.card-padding}` (14dp vertical / 16dp horizontal).
- El FAB se posiciona a `{spacing.fab-margin}` (16dp) del borde inferior derecho, por encima de la barra de navegación.
- Los elementos de lista de ítems tienen altura mínima de `{spacing.list-item-height}` (64dp) para targets táctiles cómodos.
- La separación entre secciones dentro de una pantalla usa `{spacing.section-gap}` (24dp).

**Rejilla:** Layout de una columna en toda la app. No hay layouts de dos columnas en v1.

## Elevation & Depth

- Cards de órdenes y clientes: `elevation 1dp` (shadow Material 3 tonal — mínima, solo separación de fondo).
- App bar: `elevation 0dp` en scroll top; sombra tonal sutil al hacer scroll down.
- FAB: `elevation 6dp`, color `{colors.primary-variant}` sólido.
- Bottom sheets y dialogs: `elevation 3dp` (scrim `{colors.scrim}` detrás).

## Shapes

| Componente | Corner radius |
|---|---|
| Cards (órdenes, clientes) | `{rounded.card}` — 16dp |
| Botones de acción primaria | `{rounded.button}` — 12dp |
| Chips de filtro y estado | `{rounded.chip}` — 20dp (completamente ovalados) |
| Bottom sheets | `{rounded.bottom-sheet}` — 28dp arriba, 0dp abajo |
| Campos de texto / inputs | `{rounded.input-field}` — 10dp |
| Badges de estado | `{rounded.badge}` — 20dp |

## Components

### Card de Orden

Estructura vertical de dos filas:
- **Fila superior:** folio (`{typography.body-small}`, `{colors.primary-variant}`) alineado izquierda + fecha (`{typography.body-small}`, `{colors.on-surface-variant}`) alineada derecha.
- **Fila media:** nombre del cliente (`{typography.body-large}`).
- **Fila inferior:** monto total (`{typography.title-medium}`, `{colors.primary}`) alineado izquierda + badge de estado e ícono de sync alineados derecha.

Toque en cualquier punto de la card → abre detalle de la orden. La card completa implementa `semantics(mergeDescendants = true)` para que TalkBack la anuncie como unidad: "Orden [folio], [cliente], [monto], [estado], [sync status]."

### Ícono de Sincronización

| Estado | Forma | Color |
|---|---|---|
| Sincronizado | Nube con checkmark en el centro | `{colors.sync-ok}` — cian |
| Pendiente de sync | Nube en outline (sin relleno, trazo 2dp) | `{colors.sync-pending}` — naranja |

Tamaño mínimo del ícono: 20dp. Los íconos van acompañados de `contentDescription` accesible. Reservado exclusivamente para indicadores de sincronización — no usar `{colors.sync-ok}` en ningún otro contexto.

### Badge de Estado de Orden

Chip compacto con texto en `{typography.label-large}`, fondo tonal al 12% del color de estado, texto al color de estado.

| Estado | Color base | Texto del badge |
|---|---|---|
| Liquidada | `{colors.status-paid}` | "Pagada" |
| Parcialidades / Parcial | `{colors.status-pending}` | "Parcialidades" |
| Atraso | `{colors.status-overdue}` | "Atraso" |
| Cancelada | `{colors.status-cancelled}` | "Cancelada" |

### FAB — Nueva Orden

Extended FAB con ícono `+` y etiqueta "Nueva Orden". Color `{colors.primary-variant}` sólido. `contentDescription`: "Nueva Orden". Se colapsa a FAB circular al hacer scroll down. En empty state de S-02, el FAB permanece visible — no se oculta aunque el empty state tenga su propio CTA.

### Barra de Navegación Inferior

Tres destinos en M3 `NavigationBar`:
1. **Órdenes** — ícono de lista / documento
2. **Clientes** — ícono de persona / grupo
3. **Config** — ícono de engranaje

Destino activo: ícono con `{colors.primary-variant}`, indicador pill, etiqueta visible. Destinos inactivos: `{colors.on-surface-variant}`, etiqueta visible.

### Campo de Búsqueda / Filtro

`SearchBar` de Material 3 en la parte superior de las pantallas de Historial y Clientes. Al activar la búsqueda, los chips de filtro se expanden debajo del campo.

### Chips de Filtro

Chips horizontales deslizables (`FilterChip` de Material 3). El chip activo muestra: fondo `{colors.primary-variant}`, texto blanco en `{typography.label-large}`, y `leadingIcon` de checkmark para señal visual no dependiente del color. Un solo chip activo a la vez.

### Selector de Variante (Bottom Sheet)

Bottom sheet con corner radius `{rounded.bottom-sheet}`. Nombre del ítem en la cabecera (`{typography.title-medium}`). Variantes como chips de selección única. Stepper de cantidad al centro. Botón "Agregar" primario.

### Constructor de Métodos de Pago

Cada método: fila con dropdown de tipo, campo numérico de monto y botón de eliminar (ícono ×). Contador "Restante por asignar" debajo de la lista en `{typography.title-medium}`, `{colors.primary}`. Botón "+ Agregar método" en estilo text button. El método "Efectivo" no puede repetirse.

### Stepper de Cantidad

Dos botones circulares a ambos lados del valor numérico:
- **Área táctil:** 48dp × 48dp (mínimo de accesibilidad).
- **Círculo visual:** 40dp de diámetro, color `{colors.primary-variant}`, ícono −/+ en `{colors.on-primary}`.
- **Valor:** `{typography.body-large}`, `{colors.on-surface}`, centrado entre los botones.
- El botón − se deshabilita y usa `{colors.on-surface-variant}` cuando la cantidad es 1.
- No hay campo de texto editable directo en v1 — solo −/+.

## Do's and Don'ts

**✓ Hacer**
- Un solo toque para cualquier acción primaria; dos toques como máximo para cualquier acción de la app.
- Mostrar el indicador de sync en cada registro de orden y cliente, siempre visible sin necesidad de expandir.
- Usar color + ícono + texto para comunicar estados críticos (deuda, atraso, sync pendiente) — nunca solo color.
- Confirmar destructivos (cancelar venta, cerrar sesión) con un dialog explícito antes de ejecutar.
- Mantener el monto total de la orden siempre visible en la pantalla de resumen y de pago.
- Usar `{colors.status-overdue}` (no "rojo") al referenciar colores de estado en código y prosa de spines.

**✗ No hacer**
- No usar más de 3 niveles de navegación en ningún flujo.
- No ocultar el saldo del cliente detrás de un tap adicional en el perfil.
- No usar `{colors.sync-ok}` (cian) para ningún concepto que no sea sincronización.
- No usar `{colors.sync-pending}` como color de texto — solo para íconos.
- No bloquear la UI durante sincronización en background.
- No usar rojo para estados que no sean error o atraso.
