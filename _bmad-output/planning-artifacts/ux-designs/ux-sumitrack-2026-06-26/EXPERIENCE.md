---
status: draft
updated: 2026-06-26
platform: Android (Jetpack Compose)
system: Material Design 3
design-reference: DESIGN.md
---

## Foundation

**Plataforma:** Android nativo, Jetpack Compose. SDK mínimo: API 26 (Android 8.0).  
**Sistema de UI:** Material Design 3. DESIGN.md hereda y extiende sus tokens; este documento especifica únicamente el delta de comportamiento.  
**Orientación:** Portrait exclusivamente en v1.  
**Uso típico:** Una mano, pulgar como dedo dominante. Todas las acciones primarias deben ser alcanzables en la zona inferior-central de la pantalla (zona de pulgar cómoda).  
**Conectividad:** Offline-first. La app opera sin internet usando SQLite local. Sincronización bidireccional automática al recuperar conexión. El usuario puede disparar sync manual desde Configuración.  
**Modo oscuro:** Fuera de alcance en v1.  
**Idioma:** Español (es-MX). No se requiere i18n en v1.

---

## Information Architecture

La app tiene tres destinos principales en la barra de navegación inferior: **Órdenes**, **Clientes** y **Config**. La navegación es plana — máximo tres niveles de profundidad en cualquier flujo.

### Mapa de Superficies

| # | Superficie | Tipo | Acceso desde |
|---|---|---|---|
| S-01 | Login | Pantalla completa | Primer arranque / sesión expirada |
| S-02 | Historial de Órdenes | Pantalla principal — tab Órdenes | Barra nav / post-orden completa |
| S-03 | Nueva Orden › Selección de Cliente | Pantalla | FAB en S-02 |
| S-04 | Nueva Orden › Lista de Ítems | Pantalla | Confirmación de cliente en S-03 |
| S-05 | Selector de Variante (Bottom Sheet) | Bottom sheet | Toque en ítem con variantes en S-04 |
| S-06 | Nueva Orden › Resumen | Pantalla | Botón "Revisar Orden" en S-04 |
| S-07 | Pantalla de Pago | Pantalla | Botón "Ir a Pagar" en S-06 |
| S-08 | Ticket | Bottom sheet | Confirmación de pago en S-07 |
| S-09 | Detalle de Orden | Pantalla | Toque en card en S-02 |
| S-10 | Agenda de Cobros | Pantalla | Ícono de calendario en app bar de S-02 (`contentDescription`: "Agenda de cobros") |
| S-11 | Clientes | Pantalla principal — tab Clientes | Barra nav |
| S-12 | Perfil de Cliente | Pantalla | Toque en cliente en S-11 o S-03 |
| S-13 | Alta / Edición de Cliente | Pantalla | Botón "+" en S-11 / botón editar en S-12 |
| S-14 | Configuración | Pantalla principal — tab Config | Barra nav |
| S-15 | Resolución de Conflicto | Dialog / pantalla modal | Detección automática al sincronizar |

### Descripción de superficies

**S-01 — Login**  
Campo de usuario y contraseña. Botón "Entrar". Sin opciones de recuperación de contraseña en v1. Si hay sesión previa cacheada, esta pantalla no se muestra — la app abre directamente en S-02. Los campos usan `isError = true` + `supportingText` con mensaje descriptivo ("Usuario o contraseña incorrectos. Inténtalo de nuevo.") al primer intento de guardar fallido — visible y anunciable por TalkBack. Campos con `ImeAction`: usuario → `Next`, contraseña → `Done`.

**S-02 — Historial de Órdenes (Home)**  
Lista de órdenes en orden cronológico descendente. Cada card muestra folio, nombre del cliente, monto total, badge de estado e Ícono de Sincronización. SearchBar en la parte superior con chips de filtro expandibles (por folio, por nombre de cliente, por fecha). FAB "Nueva Orden" en la esquina inferior derecha. Ícono de calendario en la app bar para acceder a la Agenda de Cobros (S-10) — `contentDescription`: "Agenda de cobros".

**S-03 — Nueva Orden › Selección de Cliente**  
Campo de búsqueda de clientes en tiempo real. Lista de resultados con nombre y saldo actual. Si el cliente tiene deuda vencida, muestra un badge con texto "Vencido" y color `{colors.status-overdue}` — nunca solo color. Botón "Nuevo cliente" si el cliente no existe (abre S-13 en modo de alta rápida con retorno automático a S-03 al guardar).

**S-04 — Nueva Orden › Lista de Ítems**  
Lista de productos activos del catálogo: nombre y precio. Indicador de contador en cada ítem cuando ya fue agregado. Barra inferior persistente con el subtotal acumulado y botón "Revisar Orden". Ítems con variantes muestran un indicador visual (chip "Variantes"). Estado vacío: ver State Patterns § S-04.

**S-05 — Selector de Variante (Bottom Sheet)**  
Nombre del ítem en la cabecera. Opciones de variante como chips de selección única. Stepper de cantidad. Botón "Agregar a la orden" primario. Al confirmar, el ítem con variante y cantidad seleccionadas se suma a S-04. Al cerrar el sheet (swipe down o Back) sin confirmar, el focus de TalkBack regresa al ítem en S-04 que disparó la apertura.

**S-06 — Nueva Orden › Resumen**  
Lista de todos los ítems agregados con subtotal por línea. Subtotal, impuestos y total en sección fija al fondo. Nombre del cliente vinculado en la cabecera. Botón "Editar" regresa a S-04. Botón "Ir a Pagar" avanza a S-07.

**S-07 — Pantalla de Pago**  
Dos modos seleccionables: **Pago inmediato** y **Parcialidades**. Total de la orden visible en la cabecera durante toda la pantalla.

*Pago inmediato:* Constructor de Métodos de Pago. Contador "Restante por asignar" implementado con `LiveRegionMode.Polite` para que TalkBack anuncie el cambio al agregar o modificar filas. Botón "Confirmar Pago" habilitado cuando Restante = $0.00. Si el cliente tiene Crédito a Favor, se muestra chip informativo con el monto y opción de aplicarlo como método.

*Parcialidades:* Campo de número de parcialidades. El sistema calcula fechas sugeridas por periodicidad (semanal/quincenal/mensual). El usuario puede ajustar fechas y montos. Suma de parcialidades debe igualar el total.

Las filas del Constructor de Métodos de Pago usan layout horizontal de tres controles: dropdown de tipo (`maxLines=1`, `overflow=Ellipsis`, `minWidth=120dp`), campo numérico de monto (`weight(1f)`), y botón × (`wrapContentWidth`). Esto garantiza que fontScale elevado no colapse los controles.

**S-08 — Ticket (Bottom Sheet post-pago)**  
Vista previa del ticket. Botones: "Imprimir vía Bluetooth" y "Compartir" (Android share intent como PNG). Al cerrar, navega a S-02. Al cerrar el sheet, el foco de TalkBack regresa al FAB en S-02.

**S-09 — Detalle de Orden**  
Vista completa de la orden: folio, fecha, cliente, estado, ítems, condición de pago, historial de cobros. Botón "Compartir Ticket". Botón "Cancelar Orden" (destructivo, con confirmación). Si la orden está en estado Parcial al cancelar: dialog con opciones "Cancelar parcialidades" o "Generar Crédito a Favor".

**S-10 — Agenda de Cobros**  
Vista de calendario con todos los pagos programados. Los días con cobros muestran: cambio de color de fondo del tile + número de cobros superpuesto (ej. "3") para canal secundario no dependiente del color. Los cobros próximos al umbral configurado muestran adicionalmente un ícono de alerta (campana). Al tocar un día, se despliega lista de cobros de ese día (cliente, monto, folio). Toque en una fila navega a S-09.

En calendarios personalizados de Compose, cada celda de día lleva `contentDescription` compuesta: "Lunes 29 de junio, 3 cobros pendientes" o "Martes 30 de junio, sin cobros".

**S-11 — Clientes**  
Lista de clientes con nombre, teléfono, saldo actual e Ícono de Sincronización. SearchBar. Botón "+" para alta (S-13). Toque en cliente abre S-12.

**S-12 — Perfil de Cliente**  
Cabecera con nombre del cliente y saldo total en `{typography.display-large}`. Secciones:
- **Alerta de estado financiero:** banner con color `{colors.status-overdue}` para deudas vencidas, banner con color `{colors.sync-ok}` para Crédito a Favor — ambos incluyen monto en texto.
- **Datos de contacto:** Teléfono, dirección, notas libres, geolocalización (toque abre mapa externo).
- **Órdenes abiertas:** Lista de órdenes en estado Pendiente o Parcial.
- **Historial completo:** Todas las órdenes con sus cobros, colapsable.

Botón "Editar" en la app bar para S-13 en modo edición.

**S-13 — Alta / Edición de Cliente**  
Campos: nombre o razón social (obligatorio), teléfono (obligatorio), RFC (opcional), dirección (opcional), notas (opcional), botón de captura de geolocalización. Todos los campos obligatorios usan `isError = true` + `supportingText` con mensaje descriptivo al intentar guardar con campo vacío (ej. "El nombre es obligatorio"). El botón "Guardar" permanece deshabilitado hasta que los campos obligatorios estén completos. Orden de `ImeAction`: nombre → `Next`, teléfono → `Next`, RFC → `Next`, dirección → `Next`, notas → `Done`. En modo de alta rápida desde S-03, guarda y regresa automáticamente al selector de cliente.

**S-14 — Configuración**  
Organizada en secciones: Datos fiscales del Tenant · Catálogo de Productos · Parámetros de venta · Sincronización (botón "Sincronizar ahora", log de conflictos) · Sesión (botón "Cerrar sesión" — destructivo, con confirmación).

**S-15 — Resolución de Conflicto**  
Dialog/modal que muestra las dos versiones del registro en conflicto. Al abrirse, el foco de TalkBack se mueve al título del dialog. El usuario elige: "Usar versión local" o "Conservar ambas". No hay resolución automática. Al cerrarse, el foco regresa a la pantalla anterior.

---

## Voice and Tone

**Registro:** Cálido y cercano, directo, sin tecnicismos. La app es un compañero de trabajo de Roberto, no un software corporativo.

**Principios:**
- Confirmaciones en positivo: "¡Listo! Orden guardada." no "Operación completada exitosamente."
- Errores sin culpar: "No pudimos conectar. Trabajando sin internet." no "Error de red."
- Instrucciones orientadas a la acción: "Selecciona al cliente" no "Es necesario seleccionar un cliente para continuar."
- Cantidades siempre formateadas: `$1,250.00` con separador de miles y dos decimales.
- Fechas en formato humano: "Hoy, 10:25 a.m.", "Mañana", "Lun 29 jun", nunca ISO.

**Ejemplos de microcopia:**

| Contexto | Texto |
|---|---|
| Orden guardada | "¡Listo! La orden quedó registrada." |
| Sin conexión | "Sin internet. Todo se guarda aquí y sube cuando haya señal." |
| Sync completado | "Sincronizado ☁" |
| Sync pendiente | "Pendiente de subir a la nube." |
| Cero órdenes | "Aún no hay órdenes. Toca + para empezar." |
| Cliente sin saldo | "Sin adeudos. ¡Todo al corriente!" |
| Cliente con atraso | "Tiene $2,400.00 vencido. Última visita hace 12 días." |
| Crédito a favor | "Tiene $800.00 a su favor. Puedes aplicarlo al pago." |
| Confirmar cancelación | "¿Cancelar esta orden? Esto no se puede deshacer." |
| Error de credenciales | "Usuario o contraseña incorrectos. Inténtalo de nuevo." |
| Impresora no disponible | "No encontramos la impresora. La orden ya está guardada — puedes compartir el ticket después." |
| Catálogo vacío (S-04) | "Aún no hay productos en el catálogo. Agrégalos en Configuración." |
| Agenda vacía (S-10) | "No hay cobros programados. Crea una orden con parcialidades para verlos aquí." |

---

## Component Patterns

### Card de Orden (comportamiento)

Toque → navega a S-09 (Detalle de Orden). No hay swipe-to-action en v1. El badge de estado se actualiza en tiempo real si el estado cambia mientras la pantalla está activa. El Ícono de Sincronización se actualiza al terminar el proceso de sincronización sin recargar la pantalla completa. La card implementa `semantics(mergeDescendants = true)` — TalkBack la anuncia como unidad compuesta: "Orden [folio], [cliente], [monto], [estado], [sync status]."

### Ícono de Sincronización

- **Sincronizado:** Nube con checkmark en el centro. Color `{colors.sync-ok}`. `contentDescription`: "Sincronizado con la nube."
- **Pendiente:** Nube en outline (sin relleno, trazo 2dp). Color `{colors.sync-pending}`. `contentDescription`: "Pendiente de sincronizar."

El ícono se muestra en la esquina superior derecha de cada card de orden y cliente. No se usa en ningún otro contexto.

### Selector de Variante (Bottom Sheet)

Se abre con animación de deslizamiento desde abajo. Solo una variante puede estar seleccionada a la vez (chips de selección única). El botón "Agregar" permanece deshabilitado hasta seleccionar variante. Al cerrar el sheet sin confirmar (swipe down o Back), no se modifica la orden y el foco de TalkBack regresa al ítem en S-04.

### Constructor de Métodos de Pago

Cada fila es independiente. El dropdown de tipo filtra "Efectivo" de opciones adicionales si ya existe una fila con ese método. El campo de monto muestra teclado numérico con decimales. El contador "Restante por asignar" usa `LiveRegionMode.Polite` — TalkBack lo anuncia automáticamente al cambiar. El botón "Confirmar Pago" se habilita cuando Restante = $0.00. El botón "×" de cada fila lleva `contentDescription`: "Eliminar método de pago [tipo]" — incluyendo el tipo de método para dar contexto (ej. "Eliminar método de pago Efectivo").

### Chips de Filtro

En S-02 y S-11: al activar la barra de búsqueda, aparecen chips horizontales deslizables. El chip activo usa: fondo `{colors.primary-variant}`, texto blanco, y `leadingIcon` de checkmark — triple canal (color + forma + ícono) para comunicar el estado sin depender solo del color. Solo un chip puede estar activo a la vez. El filtro por fecha abre un date picker.

### Stepper de Cantidad

Dos `IconButton` de 48dp × 48dp con círculo visual de 40dp a ambos lados del valor. El botón − se deshabilita cuando la cantidad es 1. No hay campo editable directo en v1 — solo −/+.

### FAB — Nueva Orden

Toque único → inicia flujo de nueva orden (S-03). Se oculta progresivamente al hacer scroll down y reaparece al hacer scroll up. En el empty state de S-02, el FAB permanece visible — no se oculta aunque el empty state tenga su propio CTA.

### Barra de Navegación Inferior

Tres tabs: Órdenes, Clientes, Config. Al tocar el tab activo → scroll al tope de la lista de esa pantalla. No hay re-animación de selección adicional. El tab activo no hace push de una nueva pantalla.

---

## State Patterns

### Estados de Conectividad

| Estado | Indicador |
|---|---|
| Online, sync al día | Sin banner — estado normal |
| Online, sync en progreso | Progress indicator lineal en la parte superior de la app bar (no bloquea UI) |
| Offline | Banner no intrusivo en la parte superior: "Sin internet. Los cambios se guardarán localmente." — aparece 3 segundos y se colapsa a un ícono pequeño en la app bar |
| Sync completado | Snackbar: "Sincronizado correctamente ☁" — 3 segundos, sin acción |
| Error de sync | Snackbar con acción: "Error al sincronizar. Reintentar" |

### Estados de Órdenes

| Estado | Badge | Comportamiento |
|---|---|---|
| Pendiente | Badge ámbar "Pendiente" | Acepta cobros. Visible en deudas del cliente. |
| Parcial | Badge ámbar "Parcialidades" | Acepta cobros parciales. Parcialidades vencidas muestran badge adicional. |
| Liquidado | Badge verde "Pagada" | Solo lectura. No acepta cobros. |
| Cancelado | Badge gris "Cancelada" | Solo lectura. Visible en historial pero no cuenta en saldo. |

### Estado Vacío (Empty State)

| Superficie | Mensaje | CTA |
|---|---|---|
| S-02 (sin órdenes) | "Aún no hay órdenes. Toca + para empezar." | FAB "Nueva Orden" |
| S-04 (catálogo vacío) | "Aún no hay productos en el catálogo. Agrégalos en Configuración." | Botón "Ir a Configuración" |
| S-10 (sin cobros) | "No hay cobros programados. Crea una orden con parcialidades para verlos aquí." | — |
| S-11 (sin clientes) | "Aún no hay clientes. Toca + para agregar el primero." | Botón "+" |

Cada estado incluye ícono ilustrativo (outlined, color `{colors.on-surface-variant}`) y mensaje en `{typography.body-large}`.

### Estado de Error de Sistema

Dialog con ícono de error, descripción del problema en lenguaje humano y una acción de recuperación. Nunca se muestran códigos de error al usuario.

### Estado de Carga

Skeleton screens (shimmer) durante la primera carga de datos. El contenedor de skeleton lleva `contentDescription`: "Cargando..." para que TalkBack lo anuncie como unidad sin enumerar cada bloque shimmer individual. Spinners circulares solo en acciones puntuales (guardar, sincronizar manualmente).

### Estado de Validación de Formulario (S-01, S-13, S-14)

Los campos obligatorios usan `isError = true` + `supportingText` con mensaje descriptivo al intentar guardar con valor vacío o inválido. El botón de guardar permanece deshabilitado hasta que todos los campos obligatorios sean válidos. No se muestran errores hasta el primer intento de guardar — sin validación agresiva en tiempo de escritura.

### Estados de S-08 (Ticket)

- **Impresora no disponible:** Snackbar con el texto de Voice and Tone. El bottom sheet permanece abierto para retry o para usar "Compartir". La orden ya está guardada — no se bloquea.

### Estados de S-10 (Agenda)

- **Sin cobros programados:** Empty state con mensaje de Voice and Tone.
- **Offline:** Usa datos locales. Sin banner adicional — los datos offline son confiables y la agenda funciona normalmente.

---

## Interaction Primitives

**Toque:** Acción primaria universal. Respuesta háptica leve de confirmación en acciones finalizadas (pago confirmado, orden guardada); respuesta háptica de error en validaciones fallidas; sin respuesta háptica en interacciones de navegación neutras.

**Toque largo:** No se usa en v1.

**Swipe horizontal:** Reservado para el deslizamiento de chips de filtro. No hay swipe-to-delete ni swipe-to-archive en listas.

**Swipe vertical:** Pull-to-refresh en S-02 e S-11 para forzar recarga de datos locales. El Bottom Sheet de Variante y el de Ticket se cierran con swipe down.

**Back navigation:** Cierra bottom sheets y dialogs antes de salir de la pantalla. En el flujo de nueva orden (S-03 → S-04 → S-06 → S-07), Back retrocede al paso anterior. Un dialog confirma si se abandona la orden desde S-04 o posterior.

**FAB:** Ver Component Patterns § FAB.

**Dialogs de confirmación:** Destructivos siempre con AlertDialog. Botones con labels descriptivos: "Sí, cancelar orden" / "No, mantenerla". Nunca "Aceptar / Cancelar" genérico.

---

## Accessibility Floor

- **Touch targets:** Mínimo 48dp × 48dp en todos los elementos interactivos. El Stepper de Cantidad tiene área táctil de 48dp con círculo visual de 40dp — el área táctil incluye padding invisible de 4dp alrededor del círculo. Los chips de filtro y badges interactivos mantienen este mínimo.
- **Color + forma + texto:** Ningún estado crítico se comunica solo por color. Badges: color + texto. Íconos de sync: forma (nube con check / nube vacía outline) + color + `contentDescription`. Chips de filtro activos: color + ícono de checkmark. Días con cobros en S-10: color + número superpuesto.
- **Content descriptions:** Todos los íconos sin texto adyacente tienen `contentDescription`. Ícono de sync sincronizado: "Sincronizado con la nube." Ícono de sync pendiente: "Pendiente de sincronizar." FAB: "Nueva Orden." Calendario en app bar: "Agenda de cobros." Botón × en filas de pago: "Eliminar método de pago [tipo]."
- **Agrupación semántica:** Las cards de orden y cliente implementan `semantics(mergeDescendants = true)` para que TalkBack las navegue como unidades, no elemento por elemento.
- **LiveRegion:** El contador "Restante por asignar" en S-07 usa `LiveRegionMode.Polite` para anunciar cambios automáticamente a TalkBack.
- **Formularios:** Orden de foco top-to-bottom con `ImeAction`: campos intermedios → `Next`, último campo → `Done`. Errores comunicados con `isError + supportingText` — no solo con borde de color.
- **Skeleton screens:** El contenedor lleva `contentDescription`: "Cargando..." para evitar que TalkBack enumere bloques shimmer individuales.
- **Escala de texto:** La UI respeta `fontScale` del sistema. Los layouts se adaptan a la altura del texto sin truncar. Las filas de métodos de pago tienen estrategia de desbordamiento explícita (ver § Component Patterns › Constructor de Métodos de Pago).
- **Tamaño mínimo de texto:** 12sp en pantalla. `label-small` es 12sp.
- **Contraste:** Todos los pares texto/fondo cumplen WCAG AA (4.5:1). Ver DESIGN.md § Colors › Contraste WCAG AA declarado. `{colors.sync-ok}` y `{colors.sync-pending}` se usan solo como íconos, no como texto.
- **Animaciones:** Respeta `ANIMATOR_DURATION_SCALE` del sistema. Si el usuario tiene animaciones reducidas, las transiciones se omiten.
- **Foco de TalkBack:** Al cerrar cualquier bottom sheet, el foco regresa al elemento que lo disparó. Al abrir S-15 (Resolución de Conflicto), el foco se mueve al título del dialog; al cerrar, regresa a la pantalla anterior.

---

## Inspiration & Anti-patterns

### Anti-patterns explícitamente rechazados

**Swipe-to-delete / swipe-to-archive en listas.**
Rechazado porque las acciones destructivas (cancelar una orden) requieren confirmación explícita que el swipe no permite. La app es de campo — los toques accidentales son frecuentes en movimiento.

**Drag-and-drop para reordenar ítems.**
Rechazado por complejidad de implementación vs. valor en v1. El orden de ítems en la orden no es relevante para el negocio.

**Push notifications al cliente final.**
Fuera de alcance en v1. Los clientes de Sumitrack (las llanteras) no tienen acceso al sistema — las notificaciones son internas para Roberto.

**Menús contextuales por long press.**
Rechazado en favor de acciones explícitas dentro del detalle de la orden (S-09). El long press es descubrible solo para usuarios avanzados; la app debe ser operada por anyone en el primer uso.

**Bottom navigation con más de 3 tabs.**
Rechazado para mantener la app en el rango de complejidad mínima. Agenda de cobros accede desde el ícono en la app bar de S-02, no como cuarto tab.

---

## Key Flows

### Flujo 1 — Roberto registra una orden en campo sin señal

**Protagonista:** Roberto, proveedor de insumos para llantas. Está en el taller de un cliente, acaba de descargar materiales. Sin señal en la zona.

1. Roberto saca el teléfono del bolsillo. La app estaba en segundo plano — al abrirla está directamente en el Historial de Órdenes (S-02). Un ícono pequeño en la app bar indica que está sin internet.
2. Toca el FAB "Nueva Orden".
3. **S-03 — Selección de cliente:** escribe las primeras letras del nombre. La lista filtra en tiempo real desde la base local. El cliente tiene un badge "Vencido" con texto y color `{colors.status-overdue}` — Roberto lo ve antes de confirmar.
4. **S-04 — Lista de ítems:** encuentra los productos del pedido. Toca cada uno para agregar. Uno tiene variantes: aparece el Selector de Variante (S-05), elige el modelo correcto, ajusta la cantidad, toca "Agregar". La barra inferior muestra el subtotal acumulado.
5. Toca "Revisar Orden".
6. **S-06 — Resumen:** revisa todo. Correcto. Toca "Ir a Pagar".
7. **S-07 — Pantalla de Pago:** el cliente pagará en 3 parcialidades. Roberto selecciona periodicidad mensual. El sistema sugiere tres fechas. Ajusta la primera. Los montos cuadran. Toca "Confirmar Pago".
8. **S-08 — Ticket:** Roberto toca "Compartir" y envía la imagen por WhatsApp al cliente. Toca "Cerrar".
9. La app regresa a S-02. La orden aparece al tope con badge "Parcialidades" e Ícono de Sincronización en outline (pendiente).

**Clímax:** El cliente tiene su comprobante. Roberto sabe que el crédito quedó registrado sin internet, en menos de 2 minutos.

**Resolución:** Al salir del taller y recuperar señal, la app sincroniza en background. El ícono cambia a nube con check. Snackbar: "Sincronizado correctamente ☁".

**Falla:** Si Roberto sale de la pantalla de ítems hacia atrás en S-04, un dialog pregunta: "¿Abandonar esta orden? Los ítems seleccionados se perderán." Botones: "Sí, salir" / "No, quedarme." La orden no existe localmente hasta que Roberto confirma el pago en S-07.

---

### Flujo 2 — Roberto cobra una parcialidad y consulta el saldo del cliente

**Protagonista:** Roberto. De regreso en el taller de Abarrotes La Paz. El cliente le paga la primera de tres parcialidades.

1. Roberto abre la app → tab Clientes (S-11). Escribe "La Paz". Toca al cliente.
2. **S-12 — Perfil del cliente:** ve el saldo total en `{typography.display-large}`. Lista de órdenes abiertas. Identifica la orden de la semana pasada. La toca.
3. **S-09 — Detalle de Orden:** ve las tres parcialidades. La primera tiene fecha de hoy. Registra el cobro: efectivo, monto completo. Confirma.
4. La parcialidad pasa a "Pagada". El estado de la orden cambia a "Parcial". El saldo del cliente se actualiza de inmediato.
5. Roberto regresa al perfil (S-12). El saldo refleja solo las dos parcialidades restantes.

**Clímax:** El saldo actualizado es la verdad. Roberto ya puede pensar en su siguiente visita.

**Falla:** Si al sincronizar el cobro se detecta un conflicto (la orden fue modificada en otro dispositivo entre el momento de apertura y el de guardado), la app presenta S-15 (Resolución de Conflicto). El cobro local se conserva en cola hasta que Roberto resuelva el conflicto. No se pierde el registro.

---

### Flujo 3 — Roberto resuelve un conflicto de sincronización

**Protagonista:** Roberto. Usó la app en un segundo dispositivo mientras el primero sincronizaba una edición de datos de un cliente.

1. Al sincronizar, la app detecta un conflicto en el registro de un cliente.
2. Aparece S-15 con las dos versiones del registro. Al abrirse, el foco de TalkBack va al título "Conflicto detectado."
3. Roberto compara. La versión local tiene el teléfono correcto. Elige "Usar versión local."
4. El conflicto queda resuelto. El registro se sincroniza de inmediato. El foco regresa a la pantalla previa.
5. En S-14 › Sincronización queda el log del conflicto resuelto para auditoría.

**Clímax:** Ningún dato se perdió silenciosamente. Roberto tomó la decisión; la app la ejecutó.

**Abandono:** Si Roberto cierra S-15 sin elegir (swipe down o Back), el conflicto permanece en cola y se reofrece en el próximo sync. No se resuelve automáticamente; no se pierde.

---

### Flujo 4 — Roberto consulta los cobros del día

**Protagonista:** Roberto. Es lunes por la mañana. Quiere saber a qué talleres visitar hoy para cobrar.

1. Abre la app → S-02. Toca el ícono de calendario en la app bar.
2. **S-10 — Agenda de Cobros:** ve el mes actual. Los días con cobros muestran un número superpuesto y diferente color de fondo. Hoy tiene "3" cobros programados.
3. Toca el día de hoy. Se despliega la lista: Ferretería El Clavo ($2,200), Papelería Cervantes ($800), Abarrotes La Paz ($4,000). Uno tiene ícono de campana — vence hoy y está dentro del umbral de alerta.
4. Toca la fila de Ferretería El Clavo → navega a S-09 (Detalle de Orden).

**Clímax:** Roberto tiene su ruta de cobros del día sin calcular ni buscar.
