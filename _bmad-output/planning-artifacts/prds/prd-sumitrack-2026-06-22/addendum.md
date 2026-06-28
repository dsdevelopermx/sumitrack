# Addendum — Sumitrack PRD

*Contenido que aporta profundidad al PRD pero pertenece a documentos downstream (arquitectura, infraestructura, diseño de solución) o no cabe en la narrativa de requisitos.*

---

## A1. Recomendaciones de Cloud Provider (Bajo Costo — MVP)

Relacionado con OQ-1 del PRD.

### Criterios de selección para MVP
- Costo mínimo inicial (validación de producto, un solo tenant activo)
- Compatible con .NET API
- Base de datos relacional gestionada disponible
- Escalable a multi-tenant sin migración de proveedor
- Documentación y soporte amplio

### Opciones candidatas

| Proveedor | Ventaja principal | Desventaja | Costo estimado MVP |
|-----------|-------------------|------------|-------------------|
| **Railway** | Despliegue muy simple, PaaS managed, PostgreSQL incluido | Menos maduro para producción a escala | ~$5–20/mes |
| **Supabase** | PostgreSQL + Auth + API auto-generada, muy bajo costo inicial | ORM propio, requiere adaptación si se usa .NET puro | Gratis → $25/mes |
| **Azure App Service + Azure SQL** | Mismo ecosistema que .NET, integración nativa, experiencia del equipo | Costo puede crecer rápido sin optimización | ~$15–50/mes iniciales |
| **Fly.io** | Barato, soporta contenedores .NET, PostgreSQL como addon | Menor ecosistema managed | ~$5–15/mes |
| **AWS (Elastic Beanstalk + RDS)** | Madurez, escalabilidad probada | Curva de configuración, costo base mayor | ~$30–80/mes |

### Decisión tomada ✅

**Stack seleccionado:** .NET API en **Railway** + **PostgreSQL** (Railway Postgres addon o Neon como alternativa serverless).

**Justificación:** Railway ofrece el menor costo inicial con soporte nativo para contenedores .NET vía Docker, PostgreSQL incluido como addon, y escala sin cambio de proveedor. El costo estimado para MVP con un solo tenant activo es ~$5–15/mes.

**Siguiente paso para arquitectura:** Definir si se usa Railway Postgres (addon gestionado, más simple) o Neon (serverless, escala a cero, mejor para multi-tenant a escala). Recomendación: Railway Postgres en MVP, migración a Neon evaluable en V2 si el volumen de tenants justifica serverless.

---

## A2. Modelo de Monetización SaaS (V2+)

Modelo base definido: **suscripción mensual o anual por Tenant.**

### Consideraciones de diseño para v1 que habilitan esto
- Multi-tenant desde día uno → onboarding de nuevos Tenants sin cambio de código.
- Settings por Tenant en nube → configuración independiente por proveedor.
- Aislamiento de datos completo → cumplimiento de expectativas de privacidad por cliente.

### Estructura tentativa (para validación futura)
- **Plan Único MVP:** acceso completo a todas las funcionalidades del MVP. Un dispositivo principal por Tenant.
- **Plan Pro (V2):** portal web, múltiples usuarios/roles, CFDI, reportes avanzados.
- Precio sugerido a explorar: $200–$500 MXN/mes por Tenant en el mercado mexicano (comparables: CONTPAQi, Aspel en segmentos similares, aunque Sumitrack es más específico y liviano).

---

## A3. Mecanismo de Sync — Consideraciones de Diseño

*(Para referencia del arquitecto — no pertenece al PRD de requisitos.)*

- Estrategia sugerida: **timestamp-based sync con vector clocks simplificado** por registro. Cada entidad tiene `updated_at` local y `server_updated_at`.
- Definición de Conflicto: `updated_at_local > server_updated_at_last_known` Y `server_updated_at > updated_at_local_last_seen` — ambas versiones evolucionaron desde el último punto común conocido.
- Resolución "conservar ambos": crear un segundo registro con flag `is_conflict_duplicate = true` y referencia al registro original. El Usuario los reconcilia manualmente; la vista de pendientes de resolución es una pantalla accesible.
- Entidades priorizadas para sync: Ventas, Cobros, Parcialidades. Clientes y Productos tienen menor probabilidad de conflicto pero siguen el mismo mecanismo.
