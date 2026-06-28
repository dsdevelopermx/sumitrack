---
title: "Sumitrack — Product Brief"
status: final
created: 2026-06-21
updated: 2026-06-21
---

# Product Brief: Sumitrack

## Resumen Ejecutivo

Sumitrack es una plataforma de gestión de ventas a crédito y cobros diseñada para proveedores de materiales que operan con esquemas de crédito hacia sus clientes. La versión inicial atiende a un proveedor de insumos para reparación de llantas en la Península de Yucatán que atiende a más de 100 clientes, donde el volumen de operaciones a crédito ha vuelto inmanejable el seguimiento manual en papel.

El sistema está compuesto por una app Android offline-first para el proveedor en campo, respaldada por una API y base de datos en la nube. Cada venta genera un ticket imprimible vía Bluetooth y queda registrada con sus condiciones de pago — ya sea pago en una sola exhibición o en parcialidades acordadas. El proveedor puede consultar en tiempo real el estado de cuentas de cada cliente, registrar pagos recibidos, y recibir recordatorios de fechas próximas de cobro.

Sumitrack se construye desde el principio como plataforma multi-tenant, con la visión de escalar como solución SaaS para otros proveedores con necesidades equivalentes — distribuidores de insumos B2B que venden a crédito y cobran en campo.

## El Problema

Un proveedor de materiales para llantas gestiona más de 100 relaciones de crédito activas. Cada cliente llama para solicitar material, el proveedor entrega y acuerdan una fecha de pago. Este ciclo se repite decenas de veces por semana.

Hoy el seguimiento vive en notas a mano y apuntes dispersos. El costo de ese status quo es concreto:

- **Cobros olvidados o tardíos**: sin recordatorios sistemáticos, las fechas de pago dependen de la memoria del proveedor
- **Falta de visibilidad**: no hay forma rápida de saber qué clientes deben, cuánto, y desde cuándo
- **Errores de reconciliación**: cuando un cliente paga parcialmente, registrar y rastrear cada parcialidad en papel genera confusión y disputas
- **Escalabilidad bloqueada**: sumar nuevos clientes amplifica el problema; el sistema manual no escala

La operación en campo (fuera de oficina, con conectividad variable) hace que soluciones de escritorio o sistemas ERP genéricos no sean opción práctica.

## La Solución

Sumitrack digitaliza el ciclo completo de venta a crédito y cobro para el proveedor en campo:

1. **Gestión de clientes**: catálogo de clientes con historial de compras, saldos pendientes y notas de seguimiento
2. **Catálogo de materiales**: productos con precios e impuestos configurables
3. **Operación de venta**: el proveedor registra una venta al recibir la orden del cliente, selecciona productos, define condiciones de pago (exhibición o parcialidades con fechas) y genera un ticket impreso vía Bluetooth o enviado por correo con los datos fiscales del proveedor
4. **Seguimiento de cobros**: registro de cada pago o parcialidad recibida, con estatus actualizado por venta
5. **Recordatorios**: notificaciones push automáticas para alertar al proveedor de pagos próximos a vencer
6. **Operación offline**: la app funciona sin conexión usando SQLite local y sincroniza automáticamente al recuperar internet

Todo el flujo ocurre desde el dispositivo Android del proveedor. No se requiere acceso a computadora para la operación diaria.

## Diferenciadores Clave

**Offline-first nativa**: diseñada para campo, no adaptada a él. La pérdida de señal no interrumpe la operación; la sincronización ocurre en segundo plano al recuperar conexión.

**Multi-tenant desde el origen**: la arquitectura soporta múltiples proveedores independientes desde el día uno — no como retrofit posterior. Cada tenant opera con datos completamente aislados.

**Construida por alguien que conoce el dominio fiscal**: el desarrollador tiene experiencia directa con sistemas de punto de venta y facturación operando en más de 30,000 negocios, lo que reduce el riesgo de diseñar un flujo que rompa en la integración fiscal futura (CFDI).

**Ticket impreso sin necesidad de CFDI en MVP**: el ticket de venta contiene los datos fiscales del proveedor y sirve como comprobante de control interno. La ruta hacia CFDI es diseñada, no improvisada, pero no bloquea el lanzamiento.

## A Quién Sirve

### Usuario Primario — El Proveedor en Campo
Persona única (sin empleados en esta fase) que surte materiales a llanteras, opera desde su vehículo, recibe órdenes por teléfono, entrega material y cobra en fechas acordadas. Necesita una herramienta que le funcione rápido, sin capacitación, y sin depender de señal. El éxito para él es: nunca olvidar un cobro, saber en segundos cuánto le debe cada cliente, y poder imprimir un ticket en el momento de la entrega.

### Usuario Futuro — Administrador / Equipo
Con el crecimiento del negocio o la adopción por otros proveedores, el portal web permitirá acceso a administradores y empleados con perfiles y permisos diferenciados. Este perfil necesita visibilidad de operación, reportes y gestión de configuración sin tocar la app móvil.

### Tenant Futuro — Otros Proveedores B2B en LATAM
Distribuidores de insumos que venden a crédito a decenas o cientos de clientes y cobran en campo. El mismo modelo de negocio en otros giros: ferretería, abarrotes, artículos de limpieza, insumos para otros talleres.

## Criterios de Éxito

**Para el proveedor actual (v1):**
- Cero ventas o parcialidades perdidas en el sistema — toda operación queda registrada
- Tiempo de registro de una venta: bajo 2 minutos desde el dispositivo
- El proveedor puede consultar el saldo de cualquier cliente en menos de 10 segundos
- Los recordatorios de pago reducen los cobros tardíos de forma medible (baseline: estado actual en papel)
- La app opera sin interrupción ante pérdida de señal

**Para la plataforma (visión SaaS):**
- Arquitectura multi-tenant soporta onboarding de nuevos proveedores sin cambios de código
- Stack y modelo de datos soportan integración futura con CFDI/SAT sin refactorización mayor

## Alcance del MVP

### Incluido (v1 — Android + API + Cloud DB)

**App Android:**
- Login y autenticación por usuario
- Catálogo de clientes (alta, consulta, notas)
- Catálogo de materiales con precios e impuestos
- Registro de venta (orden → ticket → condiciones de pago)
- Pago en una sola exhibición o en parcialidades con fechas acordadas
- Registro de cobros y parcialidades recibidas
- Estatus de ventas (pendiente, parcial, liquidado)
- Fechas de pagos programadas con vista de calendario interno
- Recordatorios push para pagos próximos
- Ticket imprimible vía Bluetooth y opcionalmente enviable por correo
- Operación offline-first con SQLite y sincronización automática

**Backend:**
- API REST en la nube (.NET)
- Base de datos relacional en la nube
- Arquitectura multi-tenant (aislamiento por tenant)
- Modelo de entidades: cuentas, empresas, usuarios, clientes, productos, impuestos, ventas, pagos

### Explícitamente fuera del MVP

- Portal web (administración y empleados)
- Integración CFDI/SAT (facturación electrónica)
- Integración con Google Calendar u otras apps de calendario
- SMS, WhatsApp o email de recordatorio hacia el cliente final
- Reportes y dashboards avanzados
- App para clientes finales

## Visión a Futuro

Si Sumitrack resuelve el problema para este proveedor, el camino natural es escalar como SaaS para proveedores B2B similares en México y LATAM — distribuidores de insumos que venden a crédito y cobran en campo.

La V2 añade el portal web con control de acceso por roles, la integración fiscal (CFDI) — soportada por la experiencia del equipo en sistemas de punto de venta — y herramientas de reporte para el administrador del negocio.

A mediano plazo, Sumitrack puede crecer hacia un CRM de campo ligero para el sector distribución: seguimiento de visitas, captura de órdenes, gestión de rutas y reconciliación automática de cartera — todo sin la complejidad ni el costo de un ERP.

La ventaja real es la ejecución: conocimiento profundo del dominio fiscal mexicano, experiencia en sistemas multi-tenant a escala, y una arquitectura diseñada para crecer desde la primera línea de código.
