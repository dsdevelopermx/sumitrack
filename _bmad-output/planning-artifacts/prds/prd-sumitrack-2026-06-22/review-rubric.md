# PRD Quality Review — Sumitrack MVP (prd-sumitrack-2026-06-22)

---

## Overall verdict

El PRD tiene un núcleo sólido: visión específica, glosario consistente, FRs concretos con consecuencias medibles, y supuestos declarados explícitamente. Los riesgos downstream más importantes son dos: la ausencia de una UJ dedicada a notificaciones/agenda (FR-21/23 carecen de camino narrativo) y la vaguedad de varios FRs en §4.5 y §4.6 que entrega criterios de aceptación implícitos en lugar de verificables. El PRD está listo para alimentar arquitectura con un pase de refuerzo puntual en done-ness; no requiere reescritura.

---

## 1. Decision-readiness — adequate

El PRD toma decisiones reales y las nombra como tales: un solo dispositivo en v1 (OQ-3), ticket por share intent en lugar de correo (OQ-5), cancelación con Cobros como bifurcación explícita (FR-16). Las Open Questions están cerradas y mapeadas al decision log. La contra-métrica de §1 ("registros paralelos en papel") es el mejor indicador de que el autor entendió qué apostó.

Las tensiones que faltan nombrar explícitamente: (a) el modelo "conservar ambos" en FR-34 crea duplicados en la base de datos — ¿quién los limpia y cuándo? Esto no es un detalle de implementación, es una regla de negocio omitida. (b) FR-16 opción A ("acuerdo manual entre las partes") deja dinero fuera del sistema sin trazabilidad — se declara pero no se advierte el riesgo de auditoría.

### Findings

- **medium** Regla de limpieza de duplicados de conflicto ausente (§4.9 FR-34) — La opción "conservar ambos" genera registros con `is_conflict_duplicate = true` pero el PRD no define cuándo ni cómo se resuelven; no hay pantalla de pendientes de resolución mencionada en los FRs (sí en el addendum, pero no en los requisitos). *Fix:* añadir un FR-34b que defina la vista de conflictos pendientes y el criterio de resolución (ej. el Usuario debe resolverlo antes de poder registrar nuevos Cobros sobre ese registro, o no hay bloqueo).
- **low** FR-16 Opción A no advierte riesgo de auditoría (§4.4 FR-16) — Cancelar parcialidades con Cobros ya registrados deja dinero fuera del sistema. El PRD lo declara pero no lo marca como `[NOTE FOR PM]`. *Fix:* agregar un callout que reconozca que esta opción no tiene trazabilidad financiera y que el Tenant asume la responsabilidad del acuerdo externo.

---

## 2. Substance over theater — strong

Sin persona theater: hay un solo usuario operativo (Roberto) y se usa consistentemente en todas las UJs. Sin NFR theater: los NFRs tienen umbrales numéricos donde importa (2 min, 10 seg, 99.5%, Android 8.0+). NFR-6 ("extensibilidad fiscal") es el más cercano a boilerplate, pero está justificado por el roadmap CFDI/SAT declarado. La sección de Fuera de Alcance hace trabajo real: cada exclusión tiene versión objetivo.

El único caso de posible furniture es NFR-4 §multi-tenant: "sin cambios de código ni redeployment" es una afirmación que pertenece a la arquitectura para validar, no al PRD para declarar. Es un objetivo válido pero enunciarlo como NFR sin un umbral verificable lo acerca a wishlist.

### Findings

- **low** NFR-4 multi-tenant no es verificable como escrito (§5 NFR-4) — "Onboarding sin cambios de código" es una meta de diseño, no un NFR testeable. *Fix:* convertir en: "El onboarding de un nuevo Tenant se completa mediante configuración de datos (inserción en tabla de Tenants + credenciales) sin deployments adicionales."

---

## 3. Strategic coherence — strong

La tesis está clara: digitalizar el ciclo crédito-cobro para un operador de campo en contexto de conectividad intermitente, sin ERP, preservando la velocidad y confianza del sistema en papel que reemplaza. Cada funcionalidad core (offline, ticket inmediato, Saldo visible, recordatorios) sirve esa tesis. La decisión de excluir portal web, CFDI y reportes avanzados del MVP es coherente con la apuesta.

Las métricas de §8 validan la tesis, no miden actividad genérica: "0 registros paralelos en papel" y "0 interrupciones por falta de señal" son exactamente los indicadores correctos para esta apuesta. El Onboarding de Tenants como métrica sin fecha objetivo es el único desalineamiento menor (mide la capacidad SaaS, no el valor al usuario primario).

### Findings

- **low** Métrica de Onboarding de Tenants sin objetivo temporal (§8) — "Sin cambios de código" no tiene una meta cuantificable de tiempo de onboarding (ej. < 30 minutos). Sin eso, no es accionable como SM de lanzamiento. *Fix:* agregar objetivo de tiempo o moverla a métricas de plataforma V2.

---

## 4. Done-ness clarity — adequate

La mayoría de FRs core tienen consecuencias verificables o umbrales numéricos. FR-12, FR-14, FR-16, FR-34 son buenos ejemplos: cada uno describe qué sucede, no solo qué se puede hacer. Sin embargo, hay un bloque de FRs con done-ness insuficiente que el downstream de historias necesitará resolver.

Casos problemáticos:

- **FR-2 (Persistencia de sesión):** "el Usuario no requiere reingresar credenciales salvo que cierre sesión manualmente *o la sesión expire por política de seguridad*" — no hay política de seguridad definida. ¿Cuánto dura la sesión? ¿Hay expiración en v1?
- **FR-7 (Perfil de Cliente):** lista qué muestra pero no define qué significa "historial completo" — ¿desde el inicio del tenant? ¿con filtros? No hay consecuencia verificable.
- **FR-17 (Listado de Ventas):** "Vista global y vista por Cliente disponibles" — no hay consecuencia de rendimiento ni de paginación. Para listas con cientos de Ventas, esto importa.
- **FR-21 (Notificaciones push):** no hay UJ que narre el flujo completo. ¿La notificación llega si la app está cerrada? ¿Requiere backend push (FCM)? ¿Funciona offline? La respuesta importa para arquitectura.
- **FR-23 (Agenda/calendario):** "vista de calendario" sin consecuencia de interacción — ¿tocar un pago navega a la Venta? ¿Es solo lectura?
- **FR-33 (Sync automática):** "El Usuario recibe notificación o indicador visual" — la ambigüedad `o` deja abierto el mecanismo concreto.

### Findings

- **high** FR-21 no define si las notificaciones funcionan con la app cerrada ni el mecanismo (§4.6 FR-21) — Si se requiere FCM, eso implica infraestructura de backend. Si solo son alarmas locales, el alcance offline cambia. *Fix:* especificar si son notificaciones push vía FCM (requiere app en background/backend) o alarmas locales programadas (funciona offline, sin backend push), y cómo se comportan cuando el dispositivo está sin señal.
- **high** FR-2 cita "política de seguridad" sin definirla (§4.1 FR-2) — Un arquitecto no puede implementar persistencia de sesión sin saber si hay expiración, y un revisor de seguridad no puede validar el comportamiento. *Fix:* declarar si hay expiración de sesión en v1 (ej. "sin expiración automática en v1; la sesión persiste indefinidamente hasta cierre manual") o la duración concreta.
- **medium** FR-23 agenda/calendario sin consecuencia de interacción (§4.6 FR-23) — "Vista de calendario" no dice si es navegable, si abre detalles de Venta, si permite registrar Cobros desde ahí. *Fix:* agregar al menos una consecuencia: ej. "Al tocar un pago en el calendario, el Usuario navega al detalle de la Venta correspondiente."
- **medium** FR-17 listado de Ventas sin límite de rendimiento ni paginación (§4.4 FR-17) — Un proveedor activo puede acumular cientos de Ventas. *Fix:* añadir consecuencia de rendimiento (ej. "La lista carga en menos de 3 segundos desde datos locales para hasta 500 Ventas") y/o especificar paginación.

---

## 5. Scope honesty — strong

La tabla de Fuera de Alcance es completa y usa "Versión objetivo" en lugar de "no aplica". Los `[SUPUESTO]` inline están presentes donde importan (SQLite sin cifrado, dispositivo con bloqueo de pantalla). La cláusula de no-usuarios (§2.2) hace trabajo real: excluye clientes finales, empleados y administradores con justificación explícita.

El addendum nombra sus propias limitaciones ("para referencia del arquitecto — no pertenece al PRD") y separa decisiones tomadas de recomendaciones pendientes. La decisión de Railway vs Neon para V2 está correctamente diferida.

Un punto menor: FR-26 declara "No se requiere infraestructura de correo en v1" como consecuencia funcional, lo cual es un enunciado de scope, no de comportamiento. No es un problema serio pero puede confundir al downstream que extrae FRs como especificaciones atómicas.

### Findings

- **low** FR-26 mezcla enunciado de scope con consecuencia funcional (§4.7 FR-26) — "No se requiere infraestructura de correo en v1" es un no-goal, no una consecuencia. *Fix:* mover a la tabla de Fuera de Alcance o a un callout `[NON-GOAL for MVP]` dentro del FR.

---

## 6. Downstream usability — strong

El glosario de §3 es completo, consistente en mayúsculas, y los términos se usan de forma homogénea en los FRs (Venta, Cobro, Parcialidad, Saldo, Conflicto — nunca con sinónimos flotantes). Los IDs de FR son únicos con una sola excepción: FR-18 y FR-18b — el uso de sufijo alfabético en lugar de FR-19 para lo que habría sido el siguiente FR sugiere que FR-18b fue añadido después; los IDs posteriores (FR-19, FR-20…) son contiguos y no se rompe la secuencia, pero la notación `b` es no estándar.

Las UJs tienen protagonista nombrado (Roberto) con contexto inline en cada una. Las cross-references entre FRs y UJs son parciales: UJ-1 y UJ-2 tienen sus FRs declarados en la descripción de sección (§4.4 "Realiza UJ-1", §4.5 "Realiza UJ-2") pero UJ-3 no aparece mapeado a una sección funcional explícita — la resolución de conflictos (FR-34) no declara "Realiza UJ-3".

No hay UJ dedicada a las notificaciones/agenda (FR-21, FR-23). Para un flujo que es diario en la operación del proveedor, su ausencia como UJ deja el comportamiento de notificación sin ancla narrativa para el diseñador UX.

### Findings

- **high** Ausencia de UJ para el flujo de recordatorios/agenda (§4.6) — FR-21 y FR-23 describen notificaciones y agenda de cobros, pero no hay UJ que narre qué hace Roberto cuando recibe una notificación: ¿la abre, ve la agenda, navega a la Venta, registra el Cobro? Sin esta UJ, el diseñador UX no tiene un camino a seguir y el arquitecto no puede inferir el flujo push-to-action. *Fix:* añadir UJ-4: "Roberto recibe un recordatorio de cobro próximo y registra el pago al día siguiente."
- **medium** FR-34 no declara que realiza UJ-3 (§4.9 FR-34) — La única UJ sobre conflictos (UJ-3) no está cruzada con FR-34. *Fix:* añadir "Realiza UJ-3." en la descripción de §4.9.
- **low** FR-18b usa notación no estándar de ID (§4.5 FR-18b) — Rompe la convención secuencial. *Fix:* renombrar FR-18b a FR-18a para consistencia, o renumerar a FR-19 y recorrer los IDs subsiguientes.

---

## 7. Shape fit — strong

El PRD es correcto en su forma para este producto: herramienta de campo de un solo operador, Android-first, chain-top hacia arquitectura → épicas → historias. Las tres UJs son operativas (no de descubrimiento ni de administración), lo cual es adecuado para una herramienta de campo. Los SMs son operacionales en lugar de user-facing (correcto para un single-operator tool). Las personas son mínimas (un único protagonista) en lugar de over-pobladas.

La omisión de UX spec formal es correcta para este shape: no es un producto de consumo masivo donde el diseñador UX necesita prototipos de alta fidelidad como input. Un pase de UX puede derivar directamente de las UJs y los FRs con consecuencias.

No hay over-formalización ni under-formalización. El PRD cumple con la forma que declara querer.

---

## Mechanical notes

**Glosario drift:** Ninguno detectado. "Venta", "Cobro", "Parcialidad", "Saldo", "Conflicto", "Tenant", "Folio" se usan consistentemente en mayúsculas a lo largo del documento. No se encontraron sinónimos flotantes ni variaciones de caso.

**ID continuity:** FR-1 a FR-34, contiguos excepto FR-18b (ver §6). OQ-1 a OQ-5 presentes y resueltos. NFR-1 a NFR-6 contiguos. UJ-1 a UJ-3 presentes. No hay IDs referenciados que no existan.

**Assumptions Index roundtrip:** El único `[SUPUESTO]` inline está en NFR-3 (SQLite sin cifrado). No hay un Índice de Supuestos formal al final del documento — dado que hay un solo supuesto declarado, la ausencia del índice es aceptable pero podría generar overhead en documentos más largos.

**UJ protagonista naming:** Roberto nombrado con contexto inline en UJ-1, UJ-2, UJ-3. Correcto.

**Secciones requeridas para el shape y stakes declarados:** Visión ✓, Usuario Objetivo/JTBDs ✓, UJs ✓, Glosario ✓, FRs ✓, NFRs ✓, Fuera de Alcance ✓, Preguntas Abiertas ✓, Métricas de Éxito con contra-métricas ✓. Sin secciones faltantes estructurales.

**Cross-reference UJ ↔ sección funcional:** UJ-1 mapeado en §4.4 ✓. UJ-2 mapeado en §4.2 y §4.5 ✓. UJ-3 no mapeado en §4.9 ✗ (ver finding §6).
