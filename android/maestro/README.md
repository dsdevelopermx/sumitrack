# Flujos de prueba en dispositivo (Maestro)

Pruebas de punta a punta contra la **API real** que validan criterios de aceptación en un dispositivo o emulador.
Cada flujo cita la historia que verifica.

## Prerrequisitos

1. App debug instalada en el dispositivo (`./gradlew installDebug`) — apunta a `http://localhost:5600/`.
2. API local arriba: `cd backend && dotnet run --project src/Sumitrack.Api --urls http://localhost:5600`
   (el `--urls` solo con HTTP evita la redirección a HTTPS, que no existe en el teléfono).
3. Maestro: `brew tap mobile-dev-inc/tap && brew install maestro` (requiere Java 17+).
4. Usuario de desarrollo sembrado: `admin` / `Admin123!` (tenant `local`).

## Uso

```bash
./run.sh                      # todos los flujos
./run.sh 01-cliente-alta.yaml # uno solo
```

`run.sh` verifica el dispositivo, hace `adb reverse tcp:5600 tcp:5600` y comprueba que la API responde.

`./verify-sync.sh` corre `sync-orden.yaml` (cliente + producto + orden a 3 parcialidades + un cobro + pull-to-refresh) y
comprueba en PostgreSQL que llegó todo al servidor: 1 cliente, 1 producto, 1 venta, 1 ítem, 3 parcialidades y 1 cobro.
Los flujos `0*` NO sincronizan: el push periódico es de 15 min y ellos duran menos (y arrancan con `clearState`), así que
sus datos solo viven en el teléfono; la sincronización se valida aparte con este script.

`./reset-tenant.sh [slug]` borra los datos operativos del tenant de pruebas en PostgreSQL (clientes, productos, ventas,
parcialidades, cobros; conserva usuarios y settings). Pide confirmación. El teléfono no necesita reinicio: los flujos
arrancan con `clearState`.

## Flujos

| Flujo | Verifica |
|-------|----------|
| `00-login.yaml` | Historia 1.4: credenciales incorrectas → mensaje sin códigos; válidas → pantalla principal |
| `01-cliente-alta.yaml` | Historia 2.2: "Guardar" no procede con obligatorios vacíos; el cliente aparece de inmediato en la lista |
| `02-producto-alta.yaml` | Historia 2.4: "Guardar" no procede sin nombre o precio; el producto aparece en el catálogo |
| `03-orden-parcialidades.yaml` | Historias 3.x: orden de $300 a 3 parcialidades mensuales, detalle con 3 × $100 pendientes y cobro de la primera |
| `04-agenda-cobros.yaml` | Historia 5.3: el cobro aparece en el calendario del mes siguiente (descripción por día), lista del día y salto al detalle |
| `05-configuracion-sesion.yaml` | Historia 5.1: datos fiscales, parámetros (AC-6: fuera de rango → error al guardar y no se guarda) y cierre de sesión con confirmación |

`subflows/` reúne los pasos compartidos (`login`, `crear-cliente`, `crear-producto`, `crear-orden-parcialidades`); no se
corren solos (`run.sh` solo toma `0*.yaml`).

## Notas

- Cada flujo arranca con `clearState` (sesión limpia). Los nombres llevan una marca de tiempo (`${output.cliente}`,
  `${output.producto}`) para no chocar con datos de corridas anteriores que el servidor conserve.
- Gestos de pull-to-refresh: esperar a que la lista esté visible y estable antes del `swipe` (sin esa espera el gesto
  no disparó el push); el swipe es largo y lento (38%→80%, 900 ms).
- Listas largas: usar `scrollUntilVisible` antes de tocar un elemento (el catálogo acumula productos de corridas previas).
- Un texto que aparece en un campo de búsqueda Y en su tarjeta de resultado coincide dos veces: `index: 1` toca la tarjeta.
- Un elemento sin texto en el árbol de accesibilidad (p. ej. el FAB "Nueva Orden" antes de declarar su semántica)
  tampoco lo lee TalkBack: si Maestro no lo encuentra, revisar `maestro hierarchy` antes de forzar coordenadas.
- Usar `extendedWaitUntil` (no `assertVisible`) para lo que depende de la carga inicial o de la red.
- Un botón deshabilitado NO se puede verificar por estado: Compose marca `enabled` en el nodo `Button` padre, no en el
  `TextView` "Guardar", y `childOf: { enabled: false }` resultó depender de la estructura de nodos (coincidió en un
  formulario y en otro no). Verificar el **comportamiento**: tocar el botón no guarda / se sigue en la pantalla.
- `eraseText` es poco fiable con el cursor a mitad de texto: preferir relanzar la app con `clearState`.
- Las pruebas de criterios finos (48dp, `contentDescription` exactos) no caben aquí: son de Compose UI tests.
