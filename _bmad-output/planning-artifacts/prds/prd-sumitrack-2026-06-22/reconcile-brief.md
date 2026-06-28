---
title: "Reconciliación Brief → PRD — Sumitrack MVP"
created: 2026-06-22
---

# Reconciliación Brief → PRD

## Gaps encontrados

### GAP-1: El ticket enviable por correo quedó reemplazado sin nota de degradación explícita
**Origen en brief:** §Alcance del MVP — "Ticket imprimible vía Bluetooth y opcionalmente enviable por correo"
**Estado en PRD:** FR-26 reemplaza el envío por correo con "compartir como imagen PNG vía Android share intent" y marca el envío por correo como fuera de alcance v1 (V2). La decisión es razonable y aparece en la tabla de preguntas abiertas (OQ-5), pero no incluye la justificación del cambio en el cuerpo del PRD. Un lector que solo lea el PRD no entenderá por qué se eliminó una funcionalidad explícita del brief.
**Severidad:** Menor — la lógica de negocio equivalente está cubierta; falta la trazabilidad de la decisión.

---

### GAP-2: La vista de calendario interno del brief no está suficientemente especificada en el PRD
**Origen en brief:** §Alcance del MVP — "Fechas de pagos programadas con vista de calendario interno"
**Estado en PRD:** FR-23 menciona "vista de calendario con todos los pagos programados" pero no define el tipo de vista (mensual, semanal, lista), navegación entre periodos, ni criterio visual de diferenciación entre pagos próximos y no próximos. La sección de fuera de alcance confirma que la integración con Google Calendar es V2, pero no especifica qué entiende el PRD por "calendario interno".
**Severidad:** Menor — el requisito existe pero está subdesarrollado; requiere más detalle antes de arquitectura/UX.

---

### GAP-3: Historial de compras por cliente no especificado como requisito formal
**Origen en brief:** §La Solución ítem 1 — "catálogo de clientes con historial de compras, saldos pendientes y notas de seguimiento"
**Estado en PRD:** FR-7 menciona "acceso al historial completo" en el perfil del cliente, pero no hay un requisito funcional dedicado que defina qué contiene ese historial (ventas liquidadas, canceladas, rango temporal, paginación). El historial de Cobros (FR-20) está bien definido, pero el historial de Ventas completo (incluyendo cerradas) no tiene FR propio.
**Severidad:** Menor — está implícito en FR-7 y FR-17, pero no hay un requisito formal con criterios de aceptación claros.

---

### GAP-4: Criterio de éxito "recordatorios reducen cobros tardíos de forma medible" no tiene métrica ni mecanismo en el PRD
**Origen en brief:** §Criterios de Éxito — "Los recordatorios de pago reducen los cobros tardíos de forma medible (baseline: estado actual en papel)"
**Estado en PRD:** La sección 8 (Métricas de Éxito) define "100% de pagos próximos notificados a tiempo" pero no incluye ningún mecanismo para medir la reducción de cobros tardíos post-lanzamiento, ni referencia al baseline en papel. La contra-métrica del brief (comparación con estado manual) quedó fuera.
**Severidad:** Menor — el objetivo de negocio del brief no tiene una métrica operacional correspondiente en el PRD que permita evaluar el éxito real del producto.

---

### GAP-5: La visión de escalabilidad SaaS y el modelo de monetización no están referenciados en el PRD de forma trazable
**Origen en brief:** §Resumen Ejecutivo, §Visión a Futuro — "plataforma SaaS para otros proveedores B2B en LATAM", modelo de onboarding sin cambios de código
**Estado en PRD:** NFR-4 cubre el aislamiento multi-tenant y el onboarding sin cambios de código, y la Sección 1 menciona la visión SaaS. Sin embargo, el PRD no referencia el `addendum.md` con el modelo de monetización más allá de una mención de pasada en §0. Si el addendum contiene decisiones de arquitectura de pricing que afectan el diseño (p.ej., trial, tier de features), esas restricciones no están visibles en el PRD para los agentes downstream.
**Severidad:** Menor — depende del contenido del addendum; si contiene restricciones de diseño, deben subir al PRD.

---

## Contenido correctamente trasladado

- **Problema de negocio** (§El Problema del brief): capturado en la Visión (§1) y en los Jobs To Be Done (§2.1).
- **Operación offline-first**: cubierta con profundidad en §4.9 (FR-32 a FR-34) y en los User Journeys UJ-1 y UJ-3.
- **Gestión de clientes**: brief §La Solución ítem 1 → PRD §4.2 (FR-5 a FR-9). Datos de identificación, saldo, notas cubiertas.
- **Catálogo de materiales con impuestos configurables**: brief §La Solución ítem 2 → PRD §4.3 (FR-10, FR-11).
- **Registro de venta con condiciones de pago**: brief §La Solución ítem 3 → PRD §4.4 (FR-12 a FR-17) con mayor detalle que el brief.
- **Seguimiento de cobros y parcialidades**: brief §La Solución ítem 4 → PRD §4.5 (FR-18 a FR-20).
- **Recordatorios push**: brief §La Solución ítem 5 → PRD §4.6 (FR-21 a FR-23).
- **Ticket imprimible vía Bluetooth**: brief §La Solución ítem 6 / §Alcance MVP → PRD §4.7 (FR-24, FR-25).
- **Autenticación por usuario**: brief §Alcance MVP → PRD §4.1 (FR-1 a FR-4).
- **Arquitectura multi-tenant**: brief §Diferenciadores Clave → PRD NFR-4 y §1.
- **Criterios de éxito cuantitativos** (< 2 min registro, < 10 s consulta saldo): brief §Criterios de Éxito → PRD FR-12, FR-6, §5 NFR-1, §8.
- **Estatus de ventas** (Pendiente, Parcial, Liquidado, Cancelado): brief §Alcance MVP → PRD FR-15 con reglas de negocio ampliadas.
- **Compatibilidad Android y stack técnico**: brief §Diferenciadores → PRD NFR-5 y referencias al stack (.NET, PostgreSQL, Railway).
- **Extensibilidad fiscal CFDI**: brief §Diferenciadores / §Criterios de Éxito → PRD NFR-6.
- **Glosario estructurado**: concepto implícito en el brief → PRD §3 formalizado correctamente.

---

## Elementos correctamente excluidos

Los siguientes ítems marcados explícitamente fuera del MVP en el brief tampoco aparecen como requisitos en el PRD, lo cual es correcto:

| Ítem excluido en brief | Confirmado fuera en PRD |
|------------------------|------------------------|
| Portal web (administración y empleados) | Sí — §6 tabla V2 |
| Integración CFDI/SAT | Sí — §6 tabla V2 |
| Integración con Google Calendar | Sí — §6 tabla V2 |
| SMS/WhatsApp/email de recordatorio al cliente final | Sí — §6 tabla V2 |
| Reportes y dashboards avanzados | Sí — §6 tabla V2 |
| App para clientes finales | Sí — §6 tabla V2 |

Adicionalmente, el PRD añade exclusiones razonables no mencionadas en el brief:
- Login biométrico / Google Sign-In (V2)
- Múltiples usuarios por tenant (V2)
- Roles y permisos (V2)
- Cifrado local de SQLite (evaluación V2)
- Eliminación física de Productos (fuera de roadmap)

Estas adiciones son justificadas y coherentes con el alcance del brief.
