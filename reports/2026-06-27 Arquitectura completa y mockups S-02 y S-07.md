# Sesión 2026-06-27 — Arquitectura completa y mockups S-02/S-07

## Resumen ejecutivo

Sesión de continuación que completó dos fases del flujo BMad: generación de mockups HTML pendientes (S-02 y S-07) y el workflow completo de arquitectura en 8 pasos. Finalizó con el primer commit real del repositorio con todos los artefactos de planificación.

---

## Artefactos generados

### Mockups HTML (Paige — Tech Writer)

| Superficie | Archivo |
|------------|---------|
| S-02 Historial de Órdenes | `_bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/mockups/s-02-historial-ordenes.html` |
| S-07 Pago con Parcialidades | `_bmad-output/planning-artifacts/ux-designs/ux-sumitrack-2026-06-26/mockups/s-07-pago-parcialidades.html` |

- **S-02**: 3 pantallas — lista con 4 estados de badge (Activa, Pagada, Vencida, Parcial) + íconos sync, estado vacío, filtro "Atraso" activo
- **S-07**: 3 pantallas — modo parcialidades (3 mensualidades, suma verificada), pago inmediato con saldo crédito (Restante $0, botón habilitado), pago inmediato con Restante > $0 (botón deshabilitado)

### Arquitectura (`_bmad-output/planning-artifacts/architecture/architecture.md`)

Completada en 8 pasos colaborativos:

1. **Análisis de contexto** — entendimiento del proyecto
2. **Evaluación de starter templates** — stack definido
3. **Decisiones arquitectónicas núcleo** — multi-tenancy, sync, IDs, folios
4. **Patrones e implementación** — BigDecimal, UiState, UTC dates
5. **Estructura del proyecto** — capas Android y .NET
6. **Validación** — 3 entidades faltantes encontradas y resueltas
7. **Correcciones post-validación** — ProductVariantEntity, CreditBalance, ConflictLog
8. **Completado**

---

## Decisiones arquitectónicas clave tomadas

| Área | Decisión |
|------|----------|
| Multi-tenancy | Schema-per-tenant en PostgreSQL; tabla control `public.tenants`; `TenantSchemaResolver` con `SET search_path` |
| Naming DB | `sumitrack_01` — sufijo numérico para escalar a múltiples DBs |
| Convenciones DB | snake_case, inglés, FK con prefijo `fk_entidad` |
| Sync conflictos | Timestamp-based: si `server.updated_at > client.updated_at` → conflicto manual antes de push |
| IDs | UUID generado en cliente (`UUID.randomUUID()`); servidor es repositorio |
| Folios | App consulta último folio en pull inicial; genera consecutivos offline; V2 maneja multi-dispositivo |
| Notificaciones | WorkManager local (sin FCM en v1) |
| Sync pull | Solo en login inicial + botón manual en Configuración |
| Sync push | Continuo en background con WorkManager mientras haya conexión |
| API docs | Scalar (no Swagger) |
| Errores API | `CustomError` — lista errores conocidos al consumidor; stack trace en Serilog únicamente |
| Precisión monetaria | `NUMERIC(18,6)` en PostgreSQL; `BigDecimal` + TEXT TypeConverter en SQLite; `[Precision(18,6)]` en EF Core |
| Fechas | Siempre UTC en storage y API; conversión a timezone del dispositivo solo en UI |
| Entidades faltantes | `ProductVariantEntity` (S-05), `CreditBalance` (entidad separada, no campo), `ConflictLog` (FR-34 auditoría) |

---

## Stack versions confirmadas

| Componente | Versión |
|------------|---------|
| Compose BOM | 2026.06.00 |
| Room | 2.8.4 stable |
| .NET | 10 |
| EF Core | 10.0.9 |
| Npgsql | 10.x |

---

## Git

- Commit `c02a6e2`: primer commit real del repositorio
- 24 archivos, 5601 líneas agregadas
- Incluye: `.gitignore`, brief, PRD, UX design (DESIGN+EXPERIENCE+mockups+working), arquitectura, 3 reportes de sesiones previas

---

## Estado BMad al cierre

| Paso | Estado |
|------|--------|
| Product Brief | ✅ Finalizado |
| PRD | ✅ Finalizado |
| UX Design | ✅ Completo |
| Arquitectura | ✅ Completa |
| Épicas y Historias | ⏳ Siguiente paso — `bmad-create-epics-and-stories` |
| Implementación | ⏳ Pendiente |
