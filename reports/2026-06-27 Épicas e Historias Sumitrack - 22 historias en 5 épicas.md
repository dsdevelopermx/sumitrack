# Sesión 2026-06-27 — Épicas e Historias Sumitrack

## Resumen ejecutivo

Sesión que completó el workflow `bmad-create-epics-and-stories` en 4 pasos: extracción de requisitos, diseño de épicas, generación de historias y validación final. El resultado es el archivo `epics.md` con 22 historias distribuidas en 5 épicas, listas para desarrollo.

---

## Artefacto generado

**`_bmad-output/planning-artifacts/epics.md`** — 936 líneas

---

## Estructura aprobada

| Épica | Título | Historias | FRs |
|-------|--------|-----------|-----|
| Epic 1 | Fundación del Proyecto e Inicio de Sesión | 4 | FR-1..4 |
| Epic 2 | Gestión de Clientes y Catálogo de Productos | 4 | FR-5..11 |
| Epic 3 | Registro de Ventas, Cobros y Ticket | 7 | FR-12..20, FR-24..26 |
| Epic 4 | Sincronización Offline y Resolución de Conflictos | 4 | FR-32..34 |
| Epic 5 | Agenda de Cobros, Notificaciones y Configuración | 3 | FR-21..23, FR-27..31 |
| **Total** | | **22 historias** | **35/35 FRs ✅** |

---

## Historias por épica

### Epic 1
- 1.1 Configuración del Monorepo y Pipeline CI/CD
- 1.2 Infraestructura Backend — API .NET + PostgreSQL Multi-Tenant
- 1.3 Infraestructura Android — Estructura, Room, Hilt y Tema Visual
- 1.4 Inicio de Sesión, Sesión Persistente y Cierre de Sesión

### Epic 2
- 2.1 Lista y Búsqueda de Clientes
- 2.2 Alta y Edición de Cliente
- 2.3 Perfil de Cliente con Saldo y Órdenes Abiertas
- 2.4 Catálogo de Productos y Variantes

### Epic 3
- 3.1 Historial de Órdenes
- 3.2 Selección de Cliente e Ítems en Nueva Orden
- 3.3 Resumen de Orden y Configuración de Pago
- 3.4 Generación y Distribución del Ticket
- 3.5 Detalle de Orden e Historial de Cobros
- 3.6 Registro de Cobros sobre Ventas y Parcialidades
- 3.7 Crédito a Favor y Cancelación de Venta con Cobros

### Epic 4
- 4.1 Motor de Sincronización Push en Background
- 4.2 Pull Inicial y Folio del Servidor al Hacer Login
- 4.3 Indicadores de Sincronización en la UI
- 4.4 Detección y Resolución de Conflictos

### Epic 5
- 5.1 Pantalla de Configuración del Tenant
- 5.2 Notificaciones Push Locales para Recordatorios de Cobro
- 5.3 Agenda y Calendario de Cobros

---

## Decisiones tomadas en esta sesión

| Decisión | Detalle |
|----------|---------|
| 5 épicas (no 8) | Épicas grandes y cohesivas dado que arquitectura y UX están completamente definidos — minimiza churn de archivos |
| Epic 3 tiene 7 historias | Ventas+Cobros+Ticket son el núcleo del negocio y tocan los mismos archivos — consolidar fue correcto |
| Epic 5 no depende de Epic 4 | Agenda y notificaciones funcionan desde SQLite local; sync es independiente |
| CreditBalance chip en S-07 → Historia 3.7 | Dependencia hacia adelante detectada en validación y corregida; el chip se integra cuando la entidad ya existe |
| Entidades por demanda | public.tenants+users (1.2) → ClientEntity (2.1) → ProductEntity (2.4) → SaleEntity (3.1) → CreditBalance (3.7) → ConflictLog (4.4) |

---

## Corrección aplicada en validación

Historia 3.3 tenía un AC de dependencia hacia adelante: el chip de Crédito a Favor en S-07 referenciaba `CreditBalanceEntity` que se crea en Historia 3.7. El AC fue movido a 3.7 para mantener la secuencia correcta.

---

## Cobertura total

- **FRs:** 35/35 ✅
- **NFRs:** Integrados como restricciones en historias relevantes (BigDecimal, <2min, 100% offline, etc.)
- **ARs (Arquitectura):** 20 requisitos arquitectónicos integrados en historias de infraestructura y patrones de implementación
- **UX-DRs:** 24 requisitos de diseño cubiertos en las historias de pantallas correspondientes

---

## Estado BMad al cierre

| Paso | Estado |
|------|--------|
| Product Brief | ✅ Finalizado |
| PRD | ✅ Finalizado |
| UX Design | ✅ Completo |
| Arquitectura | ✅ Completa |
| Épicas y Historias | ✅ Completo — `_bmad-output/planning-artifacts/epics.md` |
| Sprint Planning | ⏳ Siguiente — `bmad-sprint-planning` |
| Implementación | ⏳ Pendiente |
