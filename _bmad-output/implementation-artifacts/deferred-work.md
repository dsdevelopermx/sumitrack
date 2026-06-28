
## Deferred from: code review de 1-2-infraestructura-backend-api-net-postgresql-multi-tenant (2026-06-28)

- **JWT 365 días sin revocación** — Decisión de diseño v1. Sin refresh token ni blacklist. Revisar cuando se añadan roles o multi-sesión.
- **User.UpdatedAt nunca se actualiza tras INSERT** — Siempre muestra fecha de creación. Requiere trigger BD o SaveChanges interceptor cuando se implemente endpoint de actualización de perfil.
- **Race condition en seed con dos instancias simultáneas** — Solo aplica a horizontal scaling; seed es Development-only en v1. Resolver con transacción SERIALIZABLE cuando se implemente multi-instancia.
- **ExpiresAt es DateTime en vez de DateTimeOffset** — Sin impacto funcional (UTC serializa con Z). Refactorizar si se adopta DateTimeOffset como convención del proyecto.

## Deferred from: code review de 1-1-configuracion-del-monorepo-y-pipeline-ci-cd (2026-06-27)

- **Placeholder `<local_password>` en `appsettings.Development.json`** — se reemplaza con valores reales (Connection string Railway dev) en Historia 1.2
- **`enableEdgeToEdge()` sin tema edge-to-edge** — `themes.xml` usa `android:Theme.Material.Light.NoActionBar` como base; Historia 1.3 actualiza a Material3-compatible con soporte correcto de edge-to-edge
