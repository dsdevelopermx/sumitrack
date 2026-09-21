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

## Flujos

| Flujo | Verifica |
|-------|----------|
| `00-login.yaml` | Historia 1.4: credenciales incorrectas → mensaje sin códigos; válidas → pantalla principal |
| `01-cliente-alta.yaml` | Historia 2.2: "Guardar" no procede con obligatorios vacíos; el cliente aparece de inmediato en la lista |

## Notas

- Cada flujo arranca con `clearState` (sesión limpia). Los datos creados en el servidor **persisten**; por eso los
  nombres llevan una marca de tiempo (`${output.clienteNombre}`) y no chocan entre corridas.
- Usar `extendedWaitUntil` (no `assertVisible`) para lo que depende de la carga inicial o de la red.
- `eraseText` es poco fiable con el cursor a mitad de texto: preferir relanzar la app con `clearState`.
- Las pruebas de criterios finos (48dp, `contentDescription` exactos) no caben aquí: son de Compose UI tests.
