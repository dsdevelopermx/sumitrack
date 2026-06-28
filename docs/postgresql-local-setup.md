# PostgreSQL — Configuración Local (macOS)

## Versión instalada

- **PostgreSQL 15.18** vía Homebrew (`postgresql@15`)
- **Arquitectura:** aarch64-apple-darwin (Apple Silicon)

## Instalación

```bash
brew install postgresql@15
brew services start postgresql@15
```

## Problema inicial: rol "postgres" no existe

Homebrew crea el superusuario con el nombre del usuario de macOS, no con `postgres`. Al intentar conectarse con `-U postgres` se obtiene:

```
FATAL: role "postgres" does not exist
```

**Solución:** conectarse con el usuario de macOS y crear el rol manualmente.

```bash
psql postgres
```

Dentro de la consola de PostgreSQL:

```sql
CREATE ROLE postgres WITH SUPERUSER LOGIN PASSWORD 'postgres';
CREATE DATABASE sumitrack_01;
\q
```

## Cadena de conexión del API (Development)

Archivo: `backend/src/Sumitrack.Api/appsettings.Development.json`

```json
{
  "ConnectionStrings": {
    "DefaultConnection": "Host=localhost;Database=sumitrack_01;Username=postgres;Password=postgres"
  }
}
```

## Verificación

```bash
# Confirmar que el servicio está corriendo
brew services list | grep postgresql

# Verificar conexión y versión
psql -U postgres -c "SELECT version();"

# Listar bases de datos
psql -U postgres -c "\l"
```

## Comandos útiles del servicio

```bash
brew services start postgresql@15   # Iniciar
brew services stop postgresql@15    # Detener
brew services restart postgresql@15 # Reiniciar
```

> El API aplica las migraciones de EF Core automáticamente al arrancar en modo Development, por lo que no es necesario ejecutar migraciones manualmente.
