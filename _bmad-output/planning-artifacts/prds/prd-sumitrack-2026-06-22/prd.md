---
title: "Sumitrack — PRD v1 (MVP)"
status: draft
created: 2026-06-22
updated: 2026-06-22
---

# PRD: Sumitrack MVP

## 0. Propósito del Documento

Este PRD define los requisitos del MVP de Sumitrack: una aplicación Android para gestión de ventas a crédito y cobros en campo, respaldada por una API REST en la nube. Está dirigido al equipo de desarrollo (actualmente el desarrollador principal), a revisores técnicos y a cualquier agente downstream que genere arquitectura, épicas o historias de usuario a partir de este documento.

El documento parte del brief finalizado el 2026-06-21 (`_bmad-output/planning-artifacts/briefs/brief-sumitrack-2026-06-21/brief.md`) y no duplica su contenido — lo extiende con requisitos funcionales detallados, reglas de negocio, NFRs y restricciones de diseño.

Insumos adicionales complementados en el `addendum.md` de este workspace: recomendaciones de proveedor cloud (bajo costo), modelo de monetización SaaS futuro.

---

## 1. Visión

Sumitrack digitaliza el ciclo completo de venta a crédito y cobro para proveedores B2B que operan en campo. El proveedor registra cada venta desde su Android, acuerda condiciones de pago con el cliente, imprime el ticket en el acto, y a partir de ese momento el sistema lleva la cuenta: qué debe cada cliente, cuándo vence, y qué se ha cobrado.

La app funciona sin internet. El proveedor opera con normalidad durante cortes de señal; la sincronización ocurre en segundo plano al reconectar.

La plataforma es multi-tenant desde el primer día, con la visión de escalar como SaaS a otros proveedores B2B en LATAM que comparten el mismo patrón operativo: crédito informal, cobro en campo, sin ERP.

**Contra-métrica clave:** El éxito no es solo que el proveedor use la app — es que deje de necesitar papel. Si hay registros paralelos en papel tres meses después del lanzamiento, el flujo de registro no es suficientemente rápido o confiable.

---

## 2. Usuario Objetivo

### 2.1 Jobs To Be Done

- Saber en segundos cuánto le debe cada cliente sin buscar en notas.
- Registrar una venta mientras el cliente está presente, sin demoras.
- Imprimir un ticket de entrega en el acto, desde el vehículo.
- Nunca olvidar una fecha de cobro próxima.
- Registrar un pago recibido y actualizar el saldo sin calcular a mano.
- Operar con normalidad en zonas con señal intermitente o nula.
- Acceder al sistema desde cualquier dispositivo Android si cambia o pierde el teléfono.

### 2.2 No-usuarios (v1)

- Clientes finales del proveedor (las llanteras): no tienen acceso al sistema.
- Empleados o socios del proveedor: no hay perfiles múltiples por tenant en v1.
- Administradores de backoffice: el portal web es V2.

### 2.3 Jornadas de Usuario Clave

**UJ-1. El proveedor registra una venta en campo, sin señal.**
- **Persona + contexto:** Roberto, proveedor de insumos para llantas. Está en el taller de un cliente, acaba de descargar materiales. Sin señal.
- **Estado de entrada:** Autenticado en la app. SQLite local disponible.
- **Camino:**
  1. Abre la app → navega a Nueva Venta.
  2. Selecciona al cliente de la lista.
  3. Agrega productos y cantidades del pedido.
  4. Elige condición de pago: 3 parcialidades, con fechas acordadas en el momento.
  5. Confirma la venta → la app guarda localmente y genera el ticket.
  6. Imprime el ticket vía Bluetooth en la impresora térmica del vehículo.
- **Clímax:** El cliente recibe su ticket impreso. Roberto sabe que el crédito quedó registrado, aunque no haya señal.
- **Resolución:** La Venta queda en estado Pendiente localmente. Al reconectar, se sincroniza automáticamente con la nube.
- **Caso borde:** Si la impresora no está disponible, el ticket puede enviarse por correo o posponerse — la Venta ya fue guardada.

---

**UJ-2. El proveedor cobra y consulta el saldo de un cliente.**
- **Persona + contexto:** Roberto, en el taller de un cliente que le paga la primera de tres parcialidades.
- **Estado de entrada:** Autenticado. Con o sin señal.
- **Camino:**
  1. Navega al perfil del cliente → ve el Saldo total y la lista de Ventas abiertas.
  2. Selecciona la Venta y la Parcialidad correspondiente.
  3. Registra el Cobro recibido.
  4. La app actualiza el estatus de la Parcialidad a pagada y recalcula el Saldo.
- **Clímax:** El Saldo actualizado refleja lo que el cliente todavía debe. No hay cálculo manual.
- **Resolución:** El Cobro queda guardado localmente y se sincroniza al reconectar.

---

**UJ-3. El proveedor resuelve un conflicto de sync al reconectar.**
- **Persona + contexto:** Roberto usó la app offline en dos dispositivos distintos (o editó algo antes de que terminara de subir).
- **Estado de entrada:** La app detecta al sincronizar que un registro fue modificado en ambos lados.
- **Camino:**
  1. La app muestra una alerta de Conflicto con los dos versiones del registro en pantalla.
  2. Roberto compara y elige: reemplazar la versión en nube con la local, o conservar ambas como registros separados.
  3. Confirma su elección.
- **Clímax:** El Conflicto queda resuelto. No hay pérdida silenciosa de datos.
- **Resolución:** El registro resuelto se sincroniza. El log de conflictos queda disponible para revisión.

---

## 3. Glosario

- **Tenant** — Proveedor B2B registrado en la plataforma. Todos sus datos (Clientes, Ventas, Cobros, Settings) están aislados de otros Tenants. Cardinalidad: un Tenant tiene muchos Usuarios (v1: uno).
- **Usuario** — Persona autenticada que opera la app. En v1, cada Tenant tiene un único Usuario.
- **Cliente** — Empresa o persona física a quien el Tenant vende a crédito. Tiene un Saldo acumulado de Ventas abiertas.
- **Producto** — Artículo del catálogo del Tenant con precio e impuesto configurables.
- **Venta** — Operación que registra la entrega de Productos a un Cliente con condiciones de pago. Tiene un Estatus y un Folio único por Tenant.
- **Estatus de Venta** — Estado del avance de cobro: `Pendiente` (ningún pago recibido), `Parcial` (pagos parciales registrados), `Liquidado` (saldo saldado), `Cancelado` (venta anulada).
- **Condición de Pago** — Modalidad acordada para una Venta: pago único o Parcialidades con fechas.
- **Parcialidad** — Pago parcial programado de una Venta, con monto y fecha acordados. Una Venta puede tener de 1 a N Parcialidades (N ≤ límite en Settings).
- **Cobro** — Registro de recepción de dinero contra una Venta o una Parcialidad específica.
- **Saldo** — Suma de montos pendientes de todas las Ventas en estado Pendiente o Parcial de un Cliente.
- **Crédito a Favor** — Saldo positivo de un Cliente originado por la cancelación de una Venta con Cobros ya registrados. Puede aplicarse como pago parcial o total en una Venta futura del mismo Cliente.
- **Folio** — Identificador único y legible de una Venta dentro del Tenant. Formato: `{Serie}{número}` (ej. A1, A2). La Serie es configurable en Settings; el número es auto-incremental por Tenant.
- **Settings** — Configuración operativa del Tenant. Se almacena en la nube y se descarga al dispositivo al iniciar sesión.
- **Ticket** — Comprobante de una Venta con datos fiscales del Tenant, imprimible vía Bluetooth o compartible vía apps externas instaladas en el dispositivo.
- **Sync** — Proceso de sincronización bidireccional entre la base de datos local (SQLite) y la base de datos en la nube.
- **Conflicto** — Condición que ocurre durante Sync cuando un mismo registro fue modificado localmente y en la nube de forma independiente entre dos sincronizaciones.

---

## 4. Funcionalidades

### 4.1 Autenticación y Sesión

**Descripción:** El Usuario accede con credenciales (usuario + contraseña) validadas contra la nube. Las credenciales se almacenan en la nube para permitir login desde cualquier dispositivo Android. La sesión persiste localmente una vez autenticada — el Usuario no necesita volver a hacer login en cada apertura, salvo cierre de sesión manual. Al iniciar sesión, la app descarga los Settings del Tenant.

**Requisitos Funcionales:**

#### FR-1: Login con usuario y contraseña
El Usuario puede autenticarse con nombre de usuario y contraseña. Las credenciales se validan contra la nube.

**Consecuencias:**
- Con credenciales válidas y conexión: sesión activa, Settings descargados, navegación habilitada.
- Con credenciales incorrectas: mensaje de error claro; no se concede acceso.
- Sin conexión a internet: si el dispositivo tiene una sesión activa previa cacheada, el Usuario puede continuar operando en modo offline. Si no hay sesión previa, el login falla con mensaje explicativo.

**Fuera de alcance:** Login biométrico, Google Sign-In (V2).

---

#### FR-2: Persistencia de sesión
La sesión activa persiste en el dispositivo entre aperturas de la app. El Usuario no requiere reingresar credenciales salvo que cierre sesión manualmente o la sesión expire por política de seguridad.

---

#### FR-3: Cierre de sesión
El Usuario puede cerrar sesión manualmente desde la app. Al hacerlo, los datos de sesión locales se borran.

---

#### FR-4: Descarga de Settings al iniciar sesión
Al autenticarse exitosamente, la app descarga los Settings del Tenant desde la nube y los almacena localmente. Los Settings locales se usan durante operación offline.

---

### 4.2 Gestión de Clientes

**Descripción:** El Tenant mantiene un catálogo de Clientes. Cada Cliente tiene datos de identificación, notas de seguimiento, historial de Ventas y Saldo acumulado visible de forma inmediata. Realiza UJ-2.

**Requisitos Funcionales:**

#### FR-5: Alta de Cliente
El Usuario puede registrar un nuevo Cliente con: nombre o razón social, teléfono de contacto, dirección (opcional), RFC (opcional), notas libres.

**Consecuencias:**
- El Cliente queda disponible de inmediato para asociar a nuevas Ventas.
- La operación se guarda localmente y se sincroniza al reconectar.

---

#### FR-6: Consulta y búsqueda de Clientes
El Usuario puede consultar la lista de Clientes con búsqueda por nombre. Cada entrada en la lista muestra el Saldo actual del Cliente.

**Consecuencias:**
- La consulta de Saldo responde en menos de 10 segundos desde datos locales.
- La búsqueda filtra en tiempo real sin requerir conexión.

---

#### FR-7: Perfil de Cliente
El perfil de un Cliente muestra: datos de identificación, Saldo total, lista de Ventas abiertas (Pendiente, Parcial) y acceso al historial completo.

---

#### FR-8: Edición de datos de Cliente
El Usuario puede editar los datos de identificación y notas de un Cliente en cualquier momento.

---

#### FR-9: Notas de seguimiento
El Usuario puede agregar o editar notas libres por Cliente para registrar acuerdos, observaciones o alertas informales.

---

### 4.3 Catálogo de Materiales

**Descripción:** El Tenant gestiona su catálogo de Productos. Los precios e impuestos son configurables por producto. El catálogo persiste localmente para uso offline.

**Requisitos Funcionales:**

#### FR-10: Alta y edición de Producto
El Usuario puede registrar y editar Productos con: nombre, precio unitario, impuesto aplicable (porcentaje configurable, ej. IVA 16%).

---

#### FR-11: Activar / desactivar Producto
El Usuario puede desactivar un Producto para que no aparezca en nuevas Ventas, sin eliminarlo. Los Productos desactivados siguen visibles en el historial de Ventas anteriores.

**Fuera de alcance:** Borrado físico de Productos (preservación de integridad histórica).

---

### 4.4 Registro de Ventas

**Descripción:** El núcleo operativo de la app. El Usuario registra Ventas seleccionando Cliente y Productos, define condiciones de pago, y la Venta queda guardada localmente de inmediato. Realiza UJ-1. Un ticket se puede generar al confirmar la Venta.

**Requisitos Funcionales:**

#### FR-12: Registro de nueva Venta
El Usuario puede crear una Venta seleccionando: Cliente, uno o más Productos con cantidades, y condición de pago (pago único o Parcialidades).

**Consecuencias:**
- La Venta se guarda localmente al confirmar, independientemente de la conectividad.
- El Saldo del Cliente se actualiza localmente de forma inmediata.
- El tiempo de registro de una Venta estándar (Cliente conocido, 1-3 productos) no debe superar 2 minutos.

---

#### FR-13: Condición de pago — pago único
El Usuario puede definir una Venta con pago en una sola exhibición, con fecha de pago acordada.

---

#### FR-14: Condición de pago — Parcialidades
El Usuario puede definir una Venta con N Parcialidades (1 ≤ N ≤ límite configurado en Settings, máximo 15), asignando a cada Parcialidad un monto y una fecha acordada.

**Consecuencias:**
- La suma de los montos de las Parcialidades debe ser igual al total de la Venta antes de confirmar.
- El sistema valida la consistencia antes de guardar.

---

#### FR-15: Estatus de Venta
Cada Venta tiene un Estatus actualizado automáticamente según los Cobros registrados:
- `Pendiente`: sin pagos recibidos.
- `Parcial`: al menos un Cobro registrado, saldo aún abierto.
- `Liquidado`: todos los montos recibidos.
- `Cancelado`: Venta anulada por el Usuario.

---

#### FR-16: Cancelación de Venta
El Usuario puede cancelar una Venta ya registrada, independientemente de su Estatus actual. La Venta pasa a Estatus `Cancelado`.

**Consecuencias:**
- La cancelación requiere confirmación explícita del Usuario.
- Una Venta cancelada no acepta nuevos Cobros.
- Si la Venta no tiene Cobros registrados: se cancela directamente. Las Parcialidades pendientes se cancelan también.
- Si la Venta tiene Cobros registrados (Estatus Parcial): el sistema pregunta al Usuario cómo proceder con el dinero ya recibido:
  - **Opción A — Cancelar parcialidades:** Se cancelan todas las Parcialidades pendientes. Los Cobros quedan en el historial como referencia. El dinero ya cobrado queda fuera del sistema (acuerdo manual entre las partes).
  - **Opción B — Generar Crédito a Favor:** Se genera un Crédito a Favor al Cliente por el monto total de los Cobros ya registrados. El Crédito a Favor queda visible en el perfil del Cliente y puede aplicarse en Ventas futuras.
- Los Cobros previamente registrados quedan siempre en el historial para auditoría, independientemente de la opción elegida.

---

#### FR-17: Listado de Ventas
El Usuario puede consultar Ventas filtradas por Estatus y por Cliente. Vista global y vista por Cliente disponibles.

---

### 4.5 Cobros y Parcialidades

**Descripción:** El Usuario registra cada pago recibido contra una Venta o una Parcialidad específica. El sistema actualiza el Estatus y el Saldo automáticamente. Realiza UJ-2.

**Requisitos Funcionales:**

#### FR-18: Registro de Cobro sobre Venta de pago único
El Usuario puede registrar el pago recibido sobre una Venta de pago único. Al registrar el monto total, la Venta pasa a `Liquidado`.

---

#### FR-18b: Aplicación de Crédito a Favor
El Usuario puede aplicar el Crédito a Favor de un Cliente como pago parcial o total al registrar una nueva Venta o un Cobro. El sistema muestra el Crédito disponible cuando el Cliente tiene saldo a favor.

**Consecuencias:**
- El Crédito a Favor se descuenta del monto aplicado.
- Si el Crédito cubre el total de la Venta, esta pasa directamente a `Liquidado`.

---

#### FR-19: Registro de Cobro sobre Parcialidad
El Usuario puede marcar una Parcialidad como pagada. El sistema recalcula el Estatus de la Venta: `Parcial` si quedan Parcialidades pendientes, `Liquidado` si todas están cubiertas.

---

#### FR-20: Historial de Cobros
El Usuario puede consultar el historial completo de Cobros por Venta y por Cliente, con fecha y monto de cada pago.

---

### 4.6 Recordatorios y Agenda de Cobros

**Descripción:** El sistema notifica al proveedor de pagos próximos a vencer. Los días de anticipación son configurables en Settings. La agenda muestra todos los pagos programados en un calendario interno. Realiza UJ-1 (preparación de visitas de cobro).

**Requisitos Funcionales:**

#### FR-21: Notificaciones push para pagos próximos
El sistema envía notificaciones push automáticas al dispositivo para alertar al Usuario de Ventas o Parcialidades cuya fecha de pago se aproxima.

**Consecuencias:**
- La notificación incluye: nombre del Cliente, monto pendiente, fecha de vencimiento.
- El número de días de anticipación usa el valor configurado en Settings (`dias_anticipacion_recordatorio`).
- Las Ventas en estatus `Liquidado` o `Cancelado` no generan recordatorios.

---

#### FR-22: Configuración de días de anticipación
Los días de anticipación para recordatorios son configurables en Settings (ver FR-30). El valor se aplica globalmente a todos los recordatorios del Tenant.

---

#### FR-23: Agenda / calendario de pagos
El Usuario puede consultar una vista de calendario con todos los pagos programados (fechas de Parcialidades pendientes y pagos únicos futuros), distinguiendo los próximos a vencer según el valor configurado en Settings.

---

### 4.7 Ticket de Venta

**Descripción:** Al confirmar una Venta, el sistema genera un Ticket con los datos fiscales del Tenant. El Ticket puede imprimirse vía Bluetooth o enviarse por correo. No es CFDI — es un comprobante de control interno para v1.

**Requisitos Funcionales:**

#### FR-24: Generación de Ticket
Al confirmar una Venta, el sistema genera un Ticket que incluye: datos fiscales del Tenant (nombre, RFC, dirección, teléfono), datos del Cliente, listado de Productos con cantidades y precios, subtotal, impuestos, total, condición de pago acordada (fechas de Parcialidades si aplica) y el Folio de la Venta.

El Folio se asigna automáticamente al crear la Venta con formato `{serie_folio}{número}` (ej. A1, A2). El número es auto-incremental por Tenant y nunca se reutiliza, incluso si la Venta es cancelada posteriormente.

---

#### FR-25: Impresión vía Bluetooth
El Usuario puede imprimir el Ticket en una impresora térmica compatible vía Bluetooth, directamente desde la app.

**Consecuencias:**
- Si la impresora no está disponible, el sistema ofrece las alternativas (correo, posponer) sin bloquear la Venta ya guardada.

---

#### FR-26: Compartir Ticket como imagen vía apps del dispositivo
El Usuario puede compartir el Ticket como imagen (PNG generado en memoria) utilizando el intent nativo de compartir de Android. Esto permite enviarlo por WhatsApp, Telegram, o cualquier otra app instalada en el dispositivo que acepte imágenes.

**Consecuencias:**
- La imagen se genera en memoria; no se guarda en el almacenamiento del dispositivo salvo acción explícita del Usuario.
- No se requiere infraestructura de correo en v1.
- La opción de compartir está disponible inmediatamente después de confirmar la Venta y desde el detalle de cualquier Venta existente.

**Fuera de alcance v1:** Envío de Ticket por correo electrónico (V2).

---

### 4.8 Configuración (Settings)

**Descripción:** Los Settings del Tenant se almacenan en la nube y se descargan al dispositivo al iniciar sesión (FR-4). El Usuario puede modificarlos desde la app; los cambios se persisten localmente y se sincronizan con la nube. Durante operación offline, el sistema usa los Settings cacheados localmente.

**Requisitos Funcionales:**

#### FR-27: Límite de Parcialidades por Venta
Setting `max_parcialidades` (entero, rango 1–15, default 15). Define el número máximo de Parcialidades permitidas al crear una Venta.

---

#### FR-28: Datos fiscales del Tenant para Ticket
Setting `datos_fiscales`: nombre o razón social, RFC, dirección fiscal, teléfono. Estos datos aparecen en todos los Tickets generados.

---

#### FR-29: Días de anticipación para recordatorios
Setting `dias_anticipacion_recordatorio` (entero, rango 1–30). Define cuántos días antes del vencimiento de un pago se envía la notificación push.

---

#### FR-30: Serie de Folio
Setting `serie_folio` (texto, máximo 5 caracteres, default `"A"`). Define el prefijo de los Folios de Venta del Tenant. El número incremental se reinicia únicamente si el Tenant lo solicita explícitamente (fuera del alcance del MVP — los números no se reutilizan en v1).

---

#### FR-31: Modificación de Settings desde la app
El Usuario puede modificar cualquier Setting directamente desde la app. Los cambios se guardan localmente de inmediato y se sincronizan con la nube al reconectar.

---

### 4.9 Operación Offline y Sincronización

**Descripción:** La app opera de forma completa sin conexión a internet usando SQLite local. Al recuperar conexión, la app sincroniza automáticamente los cambios locales con la nube. Cuando se detecta un Conflicto, el Usuario lo resuelve manualmente. Realiza UJ-3.

**Requisitos Funcionales:**

#### FR-32: Operación offline completa
Todas las operaciones de la app (registro de Ventas, Cobros, consultas, edición de Clientes, modificación de Settings) funcionan sin conexión a internet usando los datos locales en SQLite.

**Consecuencias:**
- La pérdida de conexión no interrumpe ningún flujo activo.
- El indicador de estado de conexión y sync es visible en la app en todo momento.

---

#### FR-33: Sincronización automática al reconectar
Al detectar conexión a internet disponible, la app inicia automáticamente la sincronización de cambios locales pendientes con la nube, en segundo plano.

**Consecuencias:**
- La sincronización no bloquea la operación del Usuario.
- El Usuario recibe notificación o indicador visual al completar la sincronización.

---

#### FR-34: Detección y resolución manual de Conflictos
Si durante Sync se detecta que un mismo registro fue modificado localmente y en la nube de forma independiente, el sistema presenta un Conflicto al Usuario.

**Consecuencias:**
- El sistema muestra ambas versiones del registro en pantalla.
- El Usuario elige una de dos acciones: **Reemplazar** (usar la versión local, descartar la de nube) o **Conservar ambos** (crear un segundo registro marcado como duplicado para revisión posterior).
- No hay resolución automática silenciosa; toda ambigüedad es explícita.
- El log de Conflictos resueltos queda disponible para auditoría.

---

## 5. Requisitos No Funcionales

### NFR-1: Rendimiento
- El tiempo de registro de una Venta estándar (Cliente conocido, 1-3 Productos) no debe superar **2 minutos** end-to-end desde la apertura del flujo hasta la generación del Ticket.
- La consulta del Saldo de un Cliente desde datos locales debe responder en **menos de 10 segundos**.
- La Sync en background no debe degradar visiblemente la respuesta de la UI.

### NFR-2: Disponibilidad
- La app opera al 100% offline; la disponibilidad de la API en la nube es un objetivo de **99.5% mensual** para las operaciones que la requieren (login, sync, descarga de Settings).

### NFR-3: Seguridad
- Las contraseñas se almacenan en la nube con hash seguro (bcrypt o Argon2). Nunca en texto plano, ni localmente.
- Toda comunicación entre app y API viaja cifrada por **HTTPS/TLS**.
- El token de sesión se invalida en cierre de sesión manual.
- Los datos locales en SQLite no están cifrados en v1. **[SUPUESTO: el dispositivo del proveedor tiene bloqueo de pantalla habilitado; el cifrado de SQLite se evalúa en V2 según hallazgos de seguridad en campo.]**

### NFR-4: Multi-tenant e Aislamiento
- Cada Tenant tiene sus datos completamente aislados. No es posible acceder a datos de otro Tenant bajo ninguna circunstancia.
- La arquitectura multi-tenant soporta onboarding de nuevos Tenants sin cambios de código ni redeployment.

### NFR-5: Compatibilidad Android
- La app es compatible con **Android 8.0 (API 26)** y versiones superiores.
- El diseño asume un dispositivo Android de un solo usuario (el proveedor).

### NFR-6: Extensibilidad fiscal
- El modelo de datos de Ventas y Productos soporta la incorporación futura de integración CFDI/SAT sin refactorización mayor del esquema de base de datos.

---

## 6. Fuera del Alcance del MVP

| Funcionalidad | Versión objetivo |
|---------------|-----------------|
| Portal web (administración y empleados) | V2 |
| Integración CFDI/SAT (facturación electrónica) | V2 |
| Login biométrico / Google Sign-In | V2 |
| Múltiples Usuarios por Tenant | V2 |
| Roles y permisos | V2 |
| SMS, WhatsApp o email de recordatorio al Cliente final | V2 |
| Integración con Google Calendar u otras apps externas | V2 |
| Reportes y dashboards avanzados | V2 |
| App para clientes finales | V2 |
| Cifrado local de SQLite | Evaluación V2 |
| Eliminación física de Productos | Fuera de roadmap (integridad histórica) |

---

## 7. Preguntas Abiertas

Todas las preguntas abiertas originales han sido resueltas. Ver `.decision-log.md` para el registro completo.

| # | Pregunta | Resolución |
|---|----------|------------|
| OQ-1 | Cloud provider | **Railway + .NET + PostgreSQL**. Ver `addendum.md` §A1. |
| OQ-2 | Cancelación con Cobros parciales | **El sistema pregunta:** cancelar parcialidades (ajuste manual) o generar Crédito a Favor. Ver FR-16. |
| OQ-3 | Múltiples dispositivos simultáneos | **Un solo dispositivo en v1.** La resolución de Conflictos cubre el caso excepcional. Ver FR-34. |
| OQ-4 | Folio del Ticket | **Auto-incremental por Tenant con Serie configurable** (default `"A"`). Ejemplo: A1, A2…An. Ver FR-24, FR-30. |
| OQ-5 | Envío por correo | **Descartado en v1.** Reemplazado por compartir Ticket como imagen PNG vía Android share intent. Ver FR-26. |

---

## 8. Métricas de Éxito

| Métrica | Objetivo v1 | Contra-métrica |
|---------|-------------|----------------|
| Registro de Ventas sin pérdida | 0 Ventas o Parcialidades perdidas en el sistema | Registros paralelos en papel 3 meses post-lanzamiento |
| Velocidad de registro | < 2 minutos por Venta estándar | Abandono del flujo a mitad del registro |
| Consulta de Saldo | < 10 segundos desde datos locales | Usuario llama al cliente para preguntar cuánto debe |
| Cobertura de recordatorios | 100% de pagos próximos notificados a tiempo | Cobros tardíos por falta de alerta |
| Continuidad offline | 0 interrupciones de operación por falta de señal | Ventas registradas en papel por problemas de conectividad |
| Onboarding de nuevos Tenants | Sin cambios de código | Horas de desarrollo por nuevo Tenant |
