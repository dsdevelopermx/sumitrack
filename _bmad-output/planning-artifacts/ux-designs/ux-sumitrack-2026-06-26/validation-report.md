# Validation Report — Sumitrack

- **DESIGN.md:** `ux-sumitrack-2026-06-26/DESIGN.md`
- **EXPERIENCE.md:** `ux-sumitrack-2026-06-26/EXPERIENCE.md`
- **Run at:** 2026-06-26
- **Revisores:** review-rubric.md · review-accessibility.md

## Overall verdict

El par de documentos es **adecuado y listo para consumo downstream** con un conjunto acotado de correcciones. La IA es excepcionalmente detallada (15 superficies, 3 flujos con protagonista), la estructura canónica se respeta en ambos documentos, y el sistema de tokens es coherente en un 90%. Los dos defectos críticos deben resolverse antes de que arquitectura consuma los documentos: el token duplicado `colors.secondary = colors.sync-ok` y el Stepper de Cantidad especificado en 40dp que contradice el mínimo de 48dp de la Accessibility Floor.

## Category verdicts

| Categoría | Veredicto |
|---|---|
| Flow coverage | adequate |
| Token completeness | **broken** |
| Component coverage | thin |
| State coverage | adequate |
| Visual reference coverage | **broken** |
| Bloat & overspecification | adequate |
| Inheritance discipline | adequate |
| Shape fit | **strong** |

## Findings by severity

### Critical (2)

**[Token completeness]** — Token duplicado: `colors.secondary = colors.sync-ok = #00BCD4` (DESIGN.md frontmatter § Colors)
Introduce ambigüedad directa para desarrollo. EXPERIENCE.md ya usa `{colors.sync-ok}` consistentemente.
Fix: Eliminar `colors.secondary` del frontmatter y de la tabla de paleta. Hacer `colors.sync-ok` el único token autorizado para el cian.

**[Accesibilidad]** — Stepper especificado en 40dp — viola mínimo 48dp declarado (DESIGN.md § Components; EXPERIENCE.md § Accessibility Floor)
El documento declara el mínimo de 48dp y luego especifica 40dp para el mismo componente. El stepper no tiene campo editable alternativo.
Fix: El área táctil debe ser 48dp × 48dp. El círculo visual puede seguir siendo 40dp con padding interno de 4dp.

---

### High (9)

**[Token completeness]** — `typography.display-large` huérfano — definido sin uso documentado (DESIGN.md frontmatter § Typography)
Fix: Añadir fila en tabla de roles tipográficos o eliminar del frontmatter.

**[Component coverage]** — Stepper de Cantidad sin spec visual en DESIGN.md (DESIGN.md § Components — ausente)
Fix: Añadir entrada: botones circulares 48dp táctil / 40dp visual con `{colors.primary-variant}`, valor en `{typography.body-large}`.

**[Component coverage / Inheritance]** — 3 nombres de componente no coinciden entre spines (DESIGN.md § Components vs EXPERIENCE.md § Component Patterns)
"Indicadores de Sincronización" / "Ícono de Sincronización" · "Bottom Sheet de Variante" / "Selector de Variante" · "Fila de Método de Pago" / "Constructor de Métodos de Pago"
Fix: Unificar a: "Ícono de Sincronización", "Selector de Variante (Bottom Sheet)", "Constructor de Métodos de Pago".

**[State coverage]** — S-10 Agenda sin ningún estado documentado (EXPERIENCE.md § State Patterns — ausente para S-10)
Fix: Añadir empty state y estado offline para S-10.

**[Visual references]** — Cero referencias inline a visuales en los spines (DESIGN.md y EXPERIENCE.md — ausente)
Fix: Añadir en DESIGN.md § Brand & Style: "→ Referencia visual: `.working/direction-c-indigo-moderno.html`. Los spines ganan en conflicto."

**[Accesibilidad]** — S-10 Agenda: días resaltados solo por color (EXPERIENCE.md § IA › S-10)
Fix: Añadir indicador numérico de conteo en tile del día y/o ícono de alerta para cobros próximos.

**[Accesibilidad]** — LiveRegion ausente para "Restante por asignar" en S-07 (EXPERIENCE.md § Component Patterns › Constructor de Métodos de Pago)
Fix: Especificar `LiveRegionMode.Polite` para el Text de "Restante por asignar".

**[Accesibilidad]** — Fila de pago sin estrategia de desbordamiento con fontScale elevado (EXPERIENCE.md § Component Patterns › Constructor de Métodos de Pago)
Fix: Especificar `maxLines=1, overflow=Ellipsis` en dropdown, `weight(1f)` en campo de monto, `wrapContentWidth` en botón ×.

**[Accesibilidad]** — Errores de formulario sin spec de componente accesible, S-01 y S-13 (EXPERIENCE.md § IA › S-01, S-13)
Fix: Anotar que campos usan `isError + supportingText` con mensaje descriptivo.

---

### Medium (17)

**[Token completeness]** — 4 tokens huérfanos sin referencia en prosa (DESIGN.md frontmatter): `colors.on-secondary`, `colors.on-error`, `spacing.screen-vertical-top`, `spacing.icon-text-gap`.
Fix: Documentar uso o eliminar.

**[Token completeness]** — Sin declaración de pares de contraste WCAG (DESIGN.md § Colors).
Fix: Añadir sub-sección "Contraste" con pares críticos.

**[Component coverage]** — Barra de Navegación Inferior sin spec conductual (EXPERIENCE.md § Component Patterns — ausente).
Fix: Añadir entrada mínima sobre comportamiento al tocar tab activo.

**[Component coverage]** — FAB en empty state no en Component Patterns (EXPERIENCE.md § Component Patterns — ausente).
Fix: Añadir: "En empty state de S-02, el FAB no se oculta."

**[State coverage]** — S-04 sin empty state de catálogo vacío (EXPERIENCE.md § State Patterns).
Fix: Añadir: "Aún no hay productos en el catálogo. Agrégalos en Configuración."

**[State coverage]** — S-13 sin estados de validación de formulario (EXPERIENCE.md § State Patterns).
Fix: Añadir comportamiento de campos obligatorios y mensajes de error.

**[Bloat]** — `HapticFeedbackConstants.VIRTUAL_KEY` es constante de SDK (EXPERIENCE.md § Interaction Primitives).
Fix: Reemplazar por "respuesta háptica leve en confirmaciones."

**[Visual references]** — Sin mockups de pantallas complejas (S-02, S-07, S-10).
Fix: Crear `mockups/` con `order-list.html` y `payment.html`.

**[Accesibilidad]** — ContentDescription ausente en botón "×" de eliminar método de pago (S-07).
Fix: `contentDescription`: "Eliminar método de pago [tipo]".

**[Accesibilidad]** — ContentDescription ausente en ícono de calendario en app bar (S-02).
Fix: `contentDescription`: "Agenda de cobros".

**[Accesibilidad]** — Chips de filtro activos diferenciados solo por color.
Fix: Añadir leadingIcon checkmark en chip activo (patrón FilterChip M3).

**[Accesibilidad]** — Cards sin agrupación semántica para TalkBack (DESIGN.md § Card de Orden).
Fix: Especificar `semantics(mergeDescendants = true)` con string compuesto.

**[Accesibilidad]** — ImeActions no especificadas en formularios (S-01, S-13, S-14).
Fix: Campos intermedios con `ImeAction.Next`, último campo con `ImeAction.Done`.

**[Accesibilidad]** — `label-small` en 10sp contradice mínimo declarado de 12sp.
Fix: Elevar `label-small` a 12sp.

**[Accesibilidad]** — Retorno de foco tras cierre de bottom sheet no especificado (S-05, S-08).
Fix: Restaurar foco al elemento disparador al cerrar.

**[Accesibilidad]** — Badge de deuda en S-03 posiblemente solo color (Flujo 1, paso 3).
Fix: Especificar texto abreviado en el badge ("Vencido" o monto).

**[Flow coverage]** — Flujos 2 y 3 sin camino de falla.
Fix: Añadir sección "Falla" en cada flujo.

---

### Low (9)

**[Flow coverage]** — S-10 sin flujo propio ni mención en Key Flows.
**[Bloat]** — `wrapContentHeight` es atributo de Compose/XML, no decisión de UX.
**[Bloat]** — Gradiente del FAB mencionado sin especificar.
**[Inheritance]** — Ícono sync "outline" no especificado en DESIGN.md para ser accionable.
**[Shape fit]** — Sección "Inspiration & Anti-patterns" ausente.
**[State coverage]** — S-08 Ticket sin estado de error de impresora en State Patterns.
**[Accesibilidad]** — Feedback háptico no diferenciado por tipo de acción.
**[Accesibilidad]** — Skeleton screens sin contentDescription.
**[Accesibilidad]** — Foco de TalkBack en S-15 y en calendario S-10 no definido.

## Reviewer files

- `review-rubric.md`
- `review-accessibility.md`
