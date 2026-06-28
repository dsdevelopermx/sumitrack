# Sesión 2026-06-22 — PRD Sumitrack: Borrador completo con preguntas abiertas resueltas

## Resumen de lo hecho

Se creó el PRD completo del MVP de Sumitrack partiendo del brief finalizado el 2026-06-21. Se recorrió el flujo BMad PRD en **Ruta Rápida**: redacción directa con supuestos marcados, seguida de resolución de todas las preguntas abiertas en conversación.

Se completaron los pasos 1-3 de la Finalización (auditoría del log, reconciliación con brief, revisión de calidad). La sesión se detuvo antes del paso 4 (resolución de dos bloqueadores) — pendiente para la próxima sesión.

---

## Artefactos generados

| Archivo | Descripción |
|---------|-------------|
| `_bmad-output/planning-artifacts/prds/prd-sumitrack-2026-06-22/prd.md` | PRD borrador — 34 FRs, 6 NFRs, 3 UJs, 8 secciones. Status: `draft` |
| `_bmad-output/planning-artifacts/prds/prd-sumitrack-2026-06-22/addendum.md` | Recomendaciones de cloud, monetización SaaS, diseño de sync |
| `_bmad-output/planning-artifacts/prds/prd-sumitrack-2026-06-22/.decision-log.md` | 13 decisiones registradas + 5 OQs resueltas |
| `_bmad-output/planning-artifacts/prds/prd-sumitrack-2026-06-22/reconcile-brief.md` | Gaps brief→PRD (5 gaps menores identificados) |
| `_bmad-output/planning-artifacts/prds/prd-sumitrack-2026-06-22/review-rubric.md` | Review de calidad (veredicto: núcleo sólido, 4 findings high) |

---

## Decisiones tomadas en esta sesión

| Decisión | Detalle |
|----------|---------|
| Stack cloud | .NET API + Railway + PostgreSQL |
| DB | PostgreSQL (Railway addon) |
| Cancelación con cobros parciales | El sistema pregunta: cancelar parcialidades O generar Crédito a Favor |
| Crédito a Favor | Nuevo concepto en Glosario + FR-18b para su aplicación |
| Dispositivos v1 | Un solo dispositivo Android (dual improbable) |
| Folio de Ticket | Auto-incremental por Tenant, Serie configurable (default "A") — formato A1, A2…An |
| Envío del Ticket | Email descartado en v1 → compartir como imagen PNG vía Android share intent |
| Tipo de notificación | **PENDIENTE** — pregunta sin responder (ver bloqueadores) |
| Expiración de sesión | **PENDIENTE** — pregunta sin responder (ver bloqueadores) |

---

## Preguntas abiertas resueltas (OQ-1 a OQ-5)

Todas resueltas — ver Sección 7 del PRD y `.decision-log.md`.

---

## Bloqueadores pendientes para próxima sesión

Estas dos preguntas deben responderse antes de poder finalizar (polish + status: final) el PRD:

### Bloqueador 1 — Tipo de notificación (FR-21)
¿Notificaciones **locales** (WorkManager/AlarmManager, offline-first, sin backend) o **FCM** (Firebase, requiere conectividad y backend)?

Recomendación: **Opción A — Locales** para MVP offline-first.

### Bloqueador 2 — Expiración de sesión (FR-2)
¿Sesión **sin expiración automática** (logout solo manual, simple para v1) o **con TTL** (ej. 30 días)?

Recomendación: **Opción A — Sin expiración automática** para v1.

---

## Gaps menores a resolver inline (después de los bloqueadores)

Estos no requieren input del usuario — se resuelven durante el paso de polish:

1. **GAP-1**: Añadir nota de trazabilidad en FR-26 sobre la decisión email→share intent
2. **GAP-2**: Desarrollar FR-23 (calendario): tipo de vista, navegación, diferenciación visual de pagos próximos
3. **GAP-3**: Añadir criterios de aceptación a FR-7 (historial de compras por cliente)
4. **GAP-4**: Añadir métrica operacional de cobros tardíos con baseline en §8
5. **GAP-5**: Añadir nota de restricciones de diseño SaaS para agentes downstream
6. **FR-34b**: Añadir FR para pantalla de resolución de conflictos pendientes
7. **UJ-4**: Añadir jornada "Roberto recibe recordatorio → registra cobro"

---

## Estado del flujo BMad

| Paso | Estado |
|------|--------|
| Product Brief | ✅ Finalizado |
| PRD | 🔄 En finalización — pendiente 2 bloqueadores + polish |
| UX Design | ⏳ Opcional |
| Arquitectura | ⏳ Pendiente |
| Épicas y Historias | ⏳ Pendiente |
| Implementación | ⏳ Pendiente |

---

## Cómo retomar la próxima sesión

1. Abrir `/bmad-prd` (o continuar en contexto)
2. Responder los 2 bloqueadores (tipo de notificación + expiración de sesión)
3. Claude resuelve los 7 gaps menores inline
4. Polish final (editorial structure + prose)
5. Marcar PRD `status: final`
6. Siguiente paso natural: `/bmad-create-architecture` o `/bmad-ux`
