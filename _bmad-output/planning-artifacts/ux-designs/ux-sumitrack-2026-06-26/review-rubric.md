# Spine Pair Review — Sumitrack

## Overall verdict

El par de spines es **adecuado** y listo para consumo downstream con correcciones puntuales. La estructura canónica se respeta en ambos documentos, el sistema de tokens es coherente y completo en un 90%, la IA es la más detallada de cualquier ejemplo de referencia, y los tres flujos clave tienen protagonista, pasos numerados y clímax. Los problemas bloqueantes son pocos pero reales: un token duplicado crítico (`secondary` vs `sync-ok`), tres nombres de componente que no coinciden entre spines, y el `Stepper de Cantidad` que tiene spec conductual en EXPERIENCE pero ninguna visual en DESIGN. Resueltos esos cuatro puntos, el par puede usarse como contrato firme para arquitectura y desarrollo de historias.

---

## 1. Flow Coverage — adequate

Se verificaron los tres flujos contra los requisitos del decision log: nueva orden, cobro de parcialidad y resolución de conflicto. Todos tienen protagonista nombrado (Roberto), pasos numerados, beat de clímax y narración de resolución (Flujo 1). Los flujos 2 y 3 carecen de camino de falla explícito; se aceptan como omisión menor dado el ámbito de v1, pero deja a downstream sin contrato para esos edge cases.

Flujos no cubiertos como Key Flow: Login (trivial, aceptable), Alta Rápida de Cliente (embebida en Flujo 1 como referencia en paso 3, aceptable), Agenda de Cobros (no tiene flujo propio — S-10 solo aparece en IA con `[ASSUMPTION]` sin ancla en flujo).

### Findings

- **medium** Flujo 2 (cobro de parcialidad) no documenta camino de falla: ¿qué ocurre si el pago registrado no cuadra al sincronizar, o si el detalle de la orden está stale al abrirse? (EXPERIENCE.md § Key Flows › Flujo 2). *Fix:* agregar sección "Falla" con al menos un escenario: error de sync durante el registro del cobro → snackbar + dato conservado local.

- **medium** Flujo 3 (conflicto de sync) no documenta camino de falla: ¿qué ocurre si el usuario descarta el dialog sin resolver? ¿Se repite en el siguiente sync? (EXPERIENCE.md § Key Flows › Flujo 3). *Fix:* agregar una línea de falla/abandon: "Si el usuario cierra el dialog sin elegir, el conflicto permanece en cola y se reofrece en el próximo sync."

- **low** S-10 (Agenda de Cobros) está descrita en IA con `[ASSUMPTION]` en su punto de acceso pero no tiene un flujo propio ni mención en ningún Key Flow. Un consumidor que implemente S-10 no tiene contrato de experiencia. (EXPERIENCE.md § Information Architecture › S-10). *Fix:* añadir un flujo 4 breve "Roberto consulta cobros del día" o marcar S-10 explícitamente como fuera de alcance en v1.

---

## 2. Token Completeness — broken (un defecto crítico; el resto es sólido)

Se extrajeron todos los tokens del frontmatter YAML de DESIGN.md y todas las referencias `{path.to.token}` de prosa en ambos documentos.

**Tokens definidos:** `colors` (19), `typography` (9), `rounded` (6), `spacing` (8). Total: 42 tokens.

**Referencias en prosa resueltas:** todas las referencias `{...}` en DESIGN.md y EXPERIENCE.md apuntan a tokens definidos en el frontmatter. No hay referencias rotas.

**Defecto crítico — token duplicado semántico:**
`colors.secondary: "#00BCD4"` y `colors.sync-ok: "#00BCD4"` son el mismo valor hexadecimal con la misma intención semántica ("exclusivo para indicadores de sincronización"). EXPERIENCE.md usa `{colors.sync-ok}` en Component Patterns. DESIGN.md menciona `{colors.secondary}` y `{colors.sync-ok}` en distintos párrafos. Un desarrollador que encuentre ambos tokens no sabrá cuál aplicar.

**Tokens definidos pero sin ninguna referencia en prosa (huérfanos):**
- `colors.on-secondary` — definido, nunca referenciado
- `colors.on-error` — definido, nunca referenciado
- `typography.display-large` (28sp/700) — definido en YAML, ausente en la tabla de roles tipográficos de DESIGN.md § Typography
- `spacing.screen-vertical-top` (0dp) — definido, nunca referenciado
- `spacing.icon-text-gap` (8dp) — definido, nunca referenciado

**Pares de contraste:** EXPERIENCE.md § Accessibility Floor afirma "todos los pares texto/fondo cumplen WCAG AA (4.5:1)" y menciona el badge de atraso como validado. Sin embargo, ningún par está declarado explícitamente en DESIGN.md. Un arquitecto de accesibilidad downstream no puede verificar sin recalcular.

### Findings

- **critical** `colors.secondary` (#00BCD4) y `colors.sync-ok` (#00BCD4) son tokens duplicados con idéntico valor y uso. Introduce ambigüedad sobre cuál referencia usar. (DESIGN.md § Colors, frontmatter). *Fix:* eliminar `colors.secondary` del frontmatter y de la tabla de paleta principal; hacer `colors.sync-ok` el único token autorizado para el cian de sincronización. Si se necesita un "secondary" semántico de Material 3, mapearlo a un color distinto o documentar explícitamente que `secondary = sync-ok`.

- **high** `typography.display-large` está definido en YAML pero no aparece en la tabla de roles tipográficos ni en ninguna referencia de prosa — sin uso documentado. (DESIGN.md § Typography, frontmatter). *Fix:* añadir una fila en la tabla de Typography con su rol (p.ej., título de empty state, app bar display) o eliminarlo del frontmatter.

- **medium** `colors.on-secondary`, `colors.on-error`, `spacing.screen-vertical-top` y `spacing.icon-text-gap` son tokens huérfanos sin referencia en prosa. (DESIGN.md frontmatter). *Fix:* documentar su uso en las secciones correspondientes o eliminarlos.

- **medium** No hay declaración explícita de pares de contraste en DESIGN.md. La afirmación de WCAG AA está solo en EXPERIENCE.md § Accessibility Floor. (DESIGN.md § Colors). *Fix:* añadir una sub-sección "Contraste" en Colors con al menos los pares críticos: `on-surface / surface`, `on-primary / primary`, `status-overdue / fondo tonal`, `sync-ok / surface`.

---

## 3. Component Coverage — thin

Se extrajeron todos los nombres de componente de ambos documentos.

**DESIGN.md Components (8 entradas):**
Card de Orden · Indicadores de Sincronización · Badge de Estado de Orden · FAB — Nueva Orden · Barra de Navegación Inferior · Campo de Búsqueda / Filtro · Bottom Sheet de Variante · Fila de Método de Pago

**EXPERIENCE.md Component Patterns (6 entradas):**
Card de Orden · Ícono de Sincronización · Selector de Variante · Constructor de Métodos de Pago · Chips de Filtro · Stepper de Cantidad

**Gaps — sin spec visual en DESIGN.md:**
- `Stepper de Cantidad` — spec conductual completa en EXPERIENCE.md (botones circulares 40dp, deshabilitado en 1, sin campo editable), pero ninguna entrada en DESIGN.md § Components con especificación visual.
- `Chips de Filtro` — spec conductual en EXPERIENCE.md; en DESIGN.md están embebidos en la descripción de `Campo de Búsqueda / Filtro` pero sin entrada propia.

**Gaps — sin spec conductual en EXPERIENCE.md Component Patterns:**
- `FAB — Nueva Orden` — su spec conductual está dispersa en EXPERIENCE.md § Interaction Primitives (correcto para primitivas de interacción, pero el comportamiento de colapso al scroll, visibilidad en empty state y label "Nueva Orden" merecen una entrada en Component Patterns).
- `Barra de Navegación Inferior` — solo aparece en IA. Sin reglas de comportamiento (¿qué pasa si el tab activo se toca de nuevo? ¿scroll-to-top?).
- `Badge de Estado de Orden` — no tiene entrada en Component Patterns; solo aparece en State Patterns § Estados de Órdenes.

**Nombres no coincidentes entre spines:**
- DESIGN: "Indicadores de Sincronización" / EXPERIENCE: "Ícono de Sincronización"
- DESIGN: "Bottom Sheet de Variante" / EXPERIENCE: "Selector de Variante"
- DESIGN: "Fila de Método de Pago" / EXPERIENCE: "Constructor de Métodos de Pago"

### Findings

- **high** `Stepper de Cantidad` tiene spec conductual (EXPERIENCE.md § Component Patterns) pero ninguna spec visual en DESIGN.md § Components: diámetro de botones, estilo (filled/outlined/tonal), token de color, tipografía del valor numérico. *Fix:* añadir entrada en DESIGN.md § Components: botones circulares 40dp con `{colors.primary-variant}`, valor en `{typography.body-large}`.

- **high** Tres nombres de componente no coinciden entre spines (ver tabla arriba). Downstream no puede cruzar los documentos mecánicamente. (DESIGN.md § Components vs EXPERIENCE.md § Component Patterns). *Fix:* unificar a un nombre canónico en ambos documentos: "Ícono de Sincronización", "Bottom Sheet de Variante", "Constructor de Métodos de Pago".

- **medium** `Barra de Navegación Inferior` tiene spec visual en DESIGN.md pero ninguna regla de comportamiento en EXPERIENCE.md Component Patterns. ¿Qué ocurre al tocar el tab activo? (EXPERIENCE.md § Component Patterns — ausente). *Fix:* añadir entrada mínima: "Tab activo tocado de nuevo → scroll al tope de la lista. No hay re-navegación ni animación de selección adicional."

- **medium** `FAB — Nueva Orden` tiene comportamiento documentado en Interaction Primitives (colapso al scroll, aparición al subir), pero el comportamiento especial en empty state ("Reemplazable por el toque en pantalla vacía" — DESIGN.md) no está en EXPERIENCE.md. *Fix:* añadir entrada en Component Patterns con regla: "En empty state de S-02, el FAB coexiste con el CTA del empty state — el FAB no se oculta."

---

## 4. State Coverage — adequate

Se recorrieron las 15 superficies (S-01 a S-15) contra los estados esperados: vacío, carga fría, foco, error, offline, permiso denegado.

Cobertura global sólida gracias a las secciones de States genéricas (conectividad, carga skeleton, error de sistema). Las siguientes superficies tienen cobertura débil o nula:

### Findings

- **high** S-10 (Agenda de Cobros) no tiene ningún estado documentado: sin internet (¿muestra fechas locales?), sin cobros programados (empty state), carga. (EXPERIENCE.md § Information Architecture › S-10 — ninguna mención en State Patterns). *Fix:* añadir entradas en State Patterns para S-10: empty ("No hay cobros programados. Crea una orden con parcialidades para verlos aquí."), offline (usa datos locales sin banner adicional).

- **medium** S-04 (Lista de Ítems) no documenta el estado vacío: ¿qué ve Roberto si el catálogo de productos está vacío o todos están inactivos? (EXPERIENCE.md § State Patterns — no cubierto). *Fix:* añadir empty state de S-04: "Aún no hay productos en el catálogo. Agrégalos en Configuración." con botón "Ir a Configuración".

- **medium** S-13 (Alta / Edición de Cliente) no documenta estados de validación: campos obligatorios vacíos al guardar, formato de teléfono inválido. (EXPERIENCE.md § Information Architecture › S-13 y § State Patterns — no cubierto). *Fix:* añadir entrada en State Patterns: "Campos obligatorios vacíos → el botón 'Guardar' permanece deshabilitado. No se usan mensajes de error hasta el intento de guardar."

- **low** S-08 (Ticket — Bottom Sheet post-pago) tiene microcopia para el error de impresora en Voice and Tone, pero no tiene entrada en State Patterns que documente qué muestra la UI si "Imprimir vía Bluetooth" falla. (EXPERIENCE.md § Voice and Tone — mención incidental). *Fix:* añadir entrada: "Error de impresora → Snackbar con texto de Voice and Tone; bottom sheet permanece abierto para retry o compartir."

---

## 5. Visual Reference Coverage — broken

**Archivos presentes en el workspace:**
```
.working/direction-a-azul-profesional.html
.working/direction-b-verde-campo.html
.working/direction-c-indigo-moderno.html
```

No existe ninguna carpeta `mockups/`, `wireframes/` ni `imports/`.

Ninguno de los tres archivos HTML es referenciado inline desde DESIGN.md ni EXPERIENCE.md. La Dirección C fue elegida per decision log, pero `direction-c-indigo-moderno.html` está huérfano. Ningún spine tiene una sección de "Composition reference" equivalente a la que aparece en los ejemplos de Quill y Drift.

### Findings

- **high** Cero referencias inline a visuales en ambos spines. Los tres HTML de exploración visual existen pero están desconectados del contrato. Un desarrollador downstream no sabe que existe una referencia visual. (DESIGN.md y EXPERIENCE.md — ausente). *Fix:* añadir en DESIGN.md § Brand & Style o en EXPERIENCE.md § Foundation: "→ Referencia visual: `.working/direction-c-indigo-moderno.html`. Los spines ganan en conflicto."

- **medium** Las carpetas `mockups/` y `wireframes/` no existen. Dado que S-02, S-07 y S-10 son las superficies más complejas, sería de alto valor tener al menos mockups HTML para esas tres. (workspace — ausente). *Fix:* crear `mockups/` con mínimo `order-list.html`, `payment.html` y `billing-schedule.html`; referenciarlos inline en la IA de EXPERIENCE.md.

---

## 6. Bloat & Overspecification — adequate

La prosa es concisa. Las tablas reemplazan párrafos donde corresponde. No hay secciones decorativas sin decisión.

Dos instancias de detalle de implementación en EXPERIENCE.md que pertenecen a la capa de código, no de UX:

### Findings

- **medium** `HapticFeedbackConstants.VIRTUAL_KEY` en § Interaction Primitives es una constante de Android SDK, no una decisión de UX. (EXPERIENCE.md § Interaction Primitives). *Fix:* reemplazar por "vibración háptica leve (intensidad: VIRTUAL_KEY)" o simplemente "respuesta háptica leve en confirmaciones".

- **low** `wrapContentHeight` en § Accessibility Floor es un atributo de layout XML/Compose, no una regla de UX. (EXPERIENCE.md § Accessibility Floor). *Fix:* reemplazar por "los layouts se adaptan a la altura del texto sin truncar."

- **low** El FAB gradient en § Elevation ("solo en el FAB, como acento visual") menciona un gradiente sin especificarlo: dirección, colores de inicio/fin, stops. Si no se especifica, la regla no puede implementarse. (DESIGN.md § Elevation & Depth). *Fix:* especificar el gradiente o eliminarlo — "FAB en `{colors.primary-variant}` sólido, sin gradiente."

---

## 7. Inheritance Discipline — adequate

Los nombres del decision log se trasladan fielmente a los spines: "Historial de Órdenes", "Parcialidades", "Crédito a Favor", "Agenda de Cobros" son consistentes. La terminología es estable a lo largo de ambos documentos, excepto por los tres nombres de componente ya documentados en §3.

El protagonista "Roberto" es consistente en los tres flujos y encaja con el perfil operativo del decision log (proveedor de insumos en campo). El decision log no nombra al persona — la elección de nombre en EXPERIENCE es autónoma pero no genera conflicto.

### Findings

- **high** Tres nombres de componente son distintos entre spines (ver §3). Downstream no puede cruzar documentos mecánicamente por nombre. (DESIGN.md § Components vs EXPERIENCE.md § Component Patterns). *[Duplicado de §3 — misma corrección aplica.]*

- **low** El decision log menciona "nube con check (☁✓) = sincronizado · nube vacía = pendiente" como iconografía. DESIGN.md § Indicadores de Sincronización dice "nube con checkmark en centro" y "nube vacía (outline)" — consistent. EXPERIENCE.md § Ícono de Sincronización repite la misma descripción. Coherente, pero la variante de outline ("sin relleno") debería reflejarse también en DESIGN.md para que sea accionable para un diseñador de recursos.

---

## 8. Shape Fit — strong

**DESIGN.md** respeta el orden canónico:
Brand & Style → Colors → Typography → Layout & Spacing → Elevation & Depth → Shapes → Components → Do's and Don'ts ✓

**EXPERIENCE.md** incluye todas las secciones requeridas:
Foundation ✓ · Information Architecture ✓ · Voice and Tone ✓ · Component Patterns ✓ · State Patterns ✓ · Interaction Primitives ✓ · Accessibility Floor ✓ · Key Flows ✓

La sección "Inspiration & Anti-patterns" está presente en ambos ejemplos de referencia pero ausente en EXPERIENCE.md de Sumitrack.

### Findings

- **low** Sección "Inspiration & Anti-patterns" ausente. Presente en los dos ejemplos de referencia; útil para comunicar decisiones explícitamente rechazadas (p.ej., swipe-to-delete, drag-and-drop, push notifications). (EXPERIENCE.md — ausente). *Fix:* añadir sección con al menos 3 entradas: lo rechazado, de dónde viene la inspiración (si aplica), y la razón del rechazo.

---

## Mechanical Notes

1. **Token duplicado:** `colors.secondary = colors.sync-ok = #00BCD4`. Eliminar `secondary` o redirigirlo a un valor Material 3 distinto.

2. **Nombres de componente no alineados entre spines (3 pares):** ver §3 y §7.

3. **ASSUMPTION en IA:** S-10 tiene `[ASSUMPTION]` en su punto de acceso — indica que el acceso desde S-02 no fue validado con el product owner. Esto debe resolverse antes de que arquitectura lo trate como contrato.

4. **Frontmatter `status: draft`** en ambos documentos — aceptable, pero debe actualizarse a `final` tras las correcciones documentadas aquí.

5. **`colors.sync-pending: "#FF7043"`** en DESIGN.md frontmatter vs `#FF7043` en la tabla de colores de estado — coincide. Sin embargo, `#FF7043` (naranja profundo) tiene contraste sobre blanco de ~3.2:1, insuficiente para texto en WCAG AA. Si se usa como color de texto de badge, necesita validación. Si es solo ícono, 3:1 es el umbral correcto — pasa.

6. **S-12 Perfil de Cliente:** "banner rojo con el monto" para deuda vencida — el token correspondiente es `colors.status-overdue` (#AD1457, rosa-rojo). Un desarrollador debe interpretar "rojo" como `status-overdue`, pero la prose no usa el token. Debería referenciar `{colors.status-overdue}`.
