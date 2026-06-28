# Accessibility Review — Sumitrack
_Fecha: 2026-06-26 | Revisado por: auditor de accesibilidad conductual_
_Documentos auditados: DESIGN.md · EXPERIENCE.md · .decision-log.md_

---

## Overall verdict

El documento declara una Accessibility Floor razonable y cubre bien los casos más visibles (sync icons con forma+color+contentDescription, badges de estado con color+texto, respeto a fontScale). Sin embargo, hay una contradicción directa y crítica entre lo declarado y lo especificado (el stepper de 40dp viola el mínimo de 48dp que la misma sección declara), y varios flujos complejos —especialmente S-07 Pago y S-10 Agenda— tienen gaps serios que dejarían a usuarios con TalkBack activo o fontScale elevado en situaciones de bloqueo. El piso declarado es correcto en intención pero incompleto en cobertura para una app de campo B2B de este nivel de complejidad.

---

## Findings

### CRITICAL

- **[critical]** Touch target — Stepper de cantidad (S-05): DESIGN.md especifica explícitamente "Botones −/+ circulares de **40dp de diámetro**", en directa contradicción con la Accessibility Floor del mismo documento que declara "Mínimo 48dp × 48dp en todos los elementos interactivos. Los chips de filtro y **steppers** mantienen este mínimo independientemente del tamaño visual." El stepper no tiene campo de texto alternativo en v1 (solo −/+), por lo que el target es la única forma de interacción. (§ DESIGN.md › Components › Stepper de Cantidad; § EXPERIENCE.md › Accessibility Floor). *Fix:* Cambiar el tamaño visual del botón a 40dp pero envolver cada uno en un `Box` de 48dp con `minimumInteractiveComponentSize` o aplicar `Modifier.size(48.dp)` al componente `IconButton`; el círculo visible puede seguir siendo 40dp con relleno interno de 4dp.

---

### HIGH

- **[high]** Estado sin canal secundario — Agenda de Cobros (S-10): La descripción indica que "Los días con pagos pendientes se resaltan" y que los pagos próximos al umbral "se distinguen visualmente", sin especificar ningún canal adicional al color. Viola la regla declarada "nunca solo color" y quebraría la experiencia para usuarios con daltonismo o baja visión. (§ EXPERIENCE.md › Descripción de superficies › S-10). *Fix:* Especificar que los días con pagos llevan un indicador de punto o número de cobros superpuesto (conteo numérico en el tile del día), y que los próximos al umbral muestran un ícono de alerta (campana o reloj) además del cambio de color.

- **[high]** LiveRegion ausente para "Restante por asignar" (S-07): El valor "Restante por asignar" se actualiza en tiempo real conforme el usuario agrega filas de métodos de pago. Es el dato crítico que habilita el botón "Confirmar Pago", pero no se especifica que sea una región accesible con `LiveRegion.Polite`. Un usuario de TalkBack que esté foco en los campos de monto nunca será notificado del cambio de saldo restante. (§ EXPERIENCE.md › Component Patterns › Constructor de Métodos de Pago). *Fix:* Anotar que el `Text` de "Restante por asignar" se implementa con `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` para que TalkBack lo anuncie automáticamente al cambiar.

- **[high]** Desbordamiento horizontal en filas de método de pago (S-07) con fontScale elevado: Cada fila de pago contiene tres controles en fila horizontal: dropdown de tipo, campo numérico de monto, y botón "×". Con `fontScale >= 1.5` (accesible por defecto en Android), el texto del dropdown puede truncarse o los controles colapsar sin estrategia definida. El documento declara que los layouts usan `wrapContentHeight`, pero no menciona el comportamiento horizontal de estas filas complejas. (§ EXPERIENCE.md › Component Patterns › Constructor de Métodos de Pago). *Fix:* Especificar que el dropdown de tipo tiene `maxLines=1, overflow=Ellipsis` con `minWidth` fijo (ej. 120dp), el campo numérico tiene `weight(1f)`, y el botón "×" tiene `wrapContentWidth`. Incluir noción de stack vertical de los controles si el ancho disponible cae por debajo de un umbral (p.ej. `WindowSizeClass.Compact` con fontScale > 1.5).

- **[high]** Validación de formularios sin errores inline accesibles (S-13 y S-01): S-13 define campos obligatorios (nombre, teléfono) pero no especifica cómo se comunican los errores de validación. S-01 menciona "mensaje de error claro" pero sin definir el componente. En Material 3, `OutlinedTextField` soporta `isError=true` + `supportingText` que anuncia el error vía `semantics`. Si la implementación usa solo borde rojo sin `supportingText`, el error es invisible para TalkBack. (§ EXPERIENCE.md › S-01, S-13). *Fix:* Anotar explícitamente que todos los campos obligatorios en S-13 usan `isError` + `supportingText` con el mensaje de error descriptivo (ej. "El nombre es obligatorio") y que S-01 muestra el error como `supportingText` del campo contraseña, no solo como Snackbar.

---

### MEDIUM

- **[medium]** ContentDescription ausente — botón "×" de eliminar fila de pago (S-07): El botón de eliminar fila de método de pago es un `IconButton` de ícono "×" sin texto adyacente. El documento no especifica su `contentDescription`. Con TalkBack, sería anunciado como "Botón sin etiqueta" o el nombre del drawable. (§ EXPERIENCE.md › Component Patterns › Constructor de Métodos de Pago). *Fix:* Especificar `contentDescription`: "Eliminar método de pago [tipo]" (incluyendo el tipo de método para dar contexto, ej. "Eliminar método de pago Efectivo").

- **[medium]** ContentDescription ausente — ícono de calendario en app bar (S-02): La app bar de S-02 tiene un "ícono de calendario" para acceder a S-10, marcado como [ASSUMPTION] en el documento. No se especifica `contentDescription`. (§ EXPERIENCE.md › S-02). *Fix:* Confirmar el ícono en el diseño definitivo y especificar `contentDescription`: "Agenda de cobros".

- **[medium]** Estado de filtro activo/inactivo con diferenciación solo por color (S-02, S-11): Los chips de filtro activos se describen como "fondo `{colors.primary-variant}` con texto blanco" vs. inactivos en estilo por defecto. El cambio de estado es exclusivamente cromático — no hay ícono de selección (checkmark) ni cambio de forma. Violaría el principio declarado para usuarios con daltonismo. (§ EXPERIENCE.md › Component Patterns › Chips de Filtro). *Fix:* Especificar que el chip activo muestra un ícono leadingIcon de checkmark (patrón estándar de `FilterChip` en Material 3) o un cambio de borde visible además del cambio de color de fondo.

- **[medium]** Agrupación semántica de cards para TalkBack no especificada (S-02, S-11): Las cards de orden tienen 4–5 elementos de texto + 2 íconos en su interior. Sin agrupación semántica explícita, TalkBack navegará elemento por elemento (folio → fecha → cliente → monto → badge → ícono sync), lo que fragmenta el contexto. El documento no menciona `mergedDescendants` ni una descripción compuesta. (§ DESIGN.md › Components › Card de Orden). *Fix:* Anotar que la card completa se implementa con `Modifier.semantics(mergeDescendants = true)` y que el foco de accesibilidad recae sobre la card como unidad, anunciando un string compuesto: "Orden [folio], [cliente], [monto], [estado], [sync status]".

- **[medium]** Orden de foco y ImeActions no especificados en formularios (S-01, S-13, S-14): El Accessibility Floor menciona compatibilidad con teclado físico pero no define el orden de foco ni las `imeAction` (Next/Done) en ningún formulario. En S-13 hay 5 campos de texto + un botón de geolocalización — el orden lógico y la acción del botón de teclado no están definidos. (§ EXPERIENCE.md › Accessibility Floor). *Fix:* Para cada formulario, especificar la secuencia de `imeAction`: campos intermedios con `ImeAction.Next`, último campo con `ImeAction.Done`. Definir que el foco avanza en orden visual top-to-bottom.

- **[medium]** Contradicción en tamaño mínimo de texto — label-small 10sp (DESIGN.md): El documento declara "El tamaño mínimo de texto en pantalla es 12sp", pero el token `label-small` está definido como 10sp y se usa en "Etiquetas de chips de filtro". A fontScale=1.0 ya está por debajo del mínimo declarado; a fontScale reducido (0.85, que algunos usuarios configuran) cae a ~8.5sp. (§ DESIGN.md › Typography). *Fix:* Elevar `label-small` a 12sp para alinearse con el mínimo declarado, o reclasificar los chips de filtro para que usen `label-large` (12sp).

- **[medium]** Retorno de foco tras cierre de bottom sheet no especificado (S-05, S-08): Cuando el selector de variante (S-05) o el ticket (S-08) se cierran, el foco de TalkBack debería regresar al elemento que lo activó. El documento no especifica este comportamiento. Sin él, TalkBack puede perder el foco o enviarlo al inicio de la pantalla. (§ EXPERIENCE.md › Component Patterns › Selector de Variante). *Fix:* Anotar que al cerrar cualquier bottom sheet se restaura el foco al elemento disparador (ítem en S-04, botón "Confirmar Pago" en S-07).

- **[medium]** Badge de deuda vencida en lista de clientes (S-03) posiblemente solo color: En el Key Flow 1, paso 3, se describe "un badge rojo lo indica en la fila de la lista". El documento no especifica el texto o ícono dentro de este badge. Si es un punto o fondo rojo sin texto, viola la regla de doble canal. (§ EXPERIENCE.md › Key Flows › Flujo 1, paso 3). *Fix:* Especificar que el badge de deuda vencida en S-03 muestra texto abreviado (ej. "Vencido" o el monto) además del color, alineando con el patrón de badges de estado del resto de la app.

---

### LOW

- **[low]** Feedback háptico no diferenciado: Solo se especifica un constante háptico (`HapticFeedbackConstants.VIRTUAL_KEY`) para "acciones de confirmación". No se diferencia entre confirmación exitosa, cancelación destructiva, o error de validación. Android ofrece constantes semánticas más ricas (`CONFIRM`, `REJECT`, `LONG_PRESS`). (§ EXPERIENCE.md › Interaction Primitives). *Fix:* Especificar `CONFIRM` para pagos confirmados, `REJECT` (o vibración de error) para validaciones fallidas, y dejar `VIRTUAL_KEY` para interacciones neutras.

- **[low]** Accesibilidad de skeleton screens durante carga no especificada: Los skeleton screens (shimmer) no tienen descripción de accesibilidad. TalkBack los enunciaría silenciosamente o con artefactos. (§ EXPERIENCE.md › State Patterns › Estado de Carga). *Fix:* Anotar que el contenedor de skeleton lleva `contentDescription`: "Cargando..." y `semantics { isTraversalGroup = true }` para evitar que TalkBack enumere cada bloque shimmer individual.

- **[low]** Comportamiento de foco en S-15 (Resolución de Conflicto) no definido: El dialog/modal de resolución de conflicto no especifica dónde se posiciona el foco al aparecer ni al resolverse. (§ EXPERIENCE.md › S-15). *Fix:* Especificar que el foco se mueve al título del dialog al abrirse y regresa a la pantalla anterior al cerrarse.

- **[low]** Navegación de TalkBack en vista de calendario (S-10) no especificada: Las vistas de calendario personalizadas en Compose requieren semántica explícita para que TalkBack pueda navegar por días. El documento no menciona cómo los días se anuncian ni cómo se navega entre semanas. (§ EXPERIENCE.md › S-10). *Fix:* Anotar que si se usa un componente de calendario personalizado, cada celda de día lleva `contentDescription` compuesta: "Lunes 29 de junio, 3 cobros pendientes" o "Lunes 30 de junio, sin cobros".

---

## What's working

- **Sync icons:** Doble canal bien especificado: forma diferenciada (nube con check / nube vacía) + color + `contentDescription` semántico ("Sincronizado con la nube" / "Pendiente de sincronizar"). Ejemplo de implementación correcto.
- **Badges de estado de orden:** Combinan color + texto en todos los estados (Pagada, Parcialidades, Pendiente, Cancelada). No hay estado de orden comunicado solo por color.
- **ANIMATOR_DURATION_SCALE:** Explícitamente referenciado con la acción correcta (omitir transiciones si reducidas).
- **Tamaño de ítems de lista:** 64dp de altura mínima garantiza target cómodo en el contexto de mayor tráfico de la app.
- **FAB:** `contentDescription` "Nueva Orden" especificado. Posición en zona de pulgar cómoda correctamente pensada.
- **Dialogs destructivos:** Labels descriptivos en botones ("Sí, cancelar orden" / "No, mantenerla") evitan ambigüedad para todos los usuarios, incluyendo TalkBack.
- **wrapContentHeight declarado:** La estrategia general de no truncar texto al escalar fontScale está enunciada, aunque necesita completarse en componentes horizontales complejos.
- **Microcopia de error:** Textos como "Usuario o contraseña incorrectos. Inténtalo de nuevo." son descriptivos y accionables, no solo mensajes de estado.
