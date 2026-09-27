# FusaRoute Backend

API REST para el sistema de información de transporte público de Fusagasugá.

## API

### `POST /api/auth/register` — Registro de usuario (RF-01, SCRUM-12)

Endpoint público (no requiere token). Crea un usuario con rol `USER`, activado de
inmediato (sin verificación por correo este semestre).

**Request** (`application/json`):

```json
{ "name": "Ana Pérez", "email": "ana@ejemplo.com", "password": "Clave123" }
```

- `name`: obligatorio, máximo 120 caracteres.
- `email`: obligatorio, formato válido, único; se normaliza (trim + minúsculas).
- `password`: mínimo 8 caracteres, al menos 1 mayúscula y 1 número, máximo 72 bytes UTF-8.
- El teléfono no se envía en el registro; se edita luego en el perfil (RF-03).

**`201 Created`** — nunca incluye el hash de la contraseña:

```json
{ "id": 1, "name": "Ana Pérez", "email": "ana@ejemplo.com", "role": "USER", "active": true }
```

**Errores** (cuerpo `ProblemDetail`, RFC 9457):

- **`400`** validación. Reúne todos los incumplimientos en la propiedad `errors`:

  ```json
  { "status": 400, "detail": "Datos de registro invalidos",
    "errors": [ { "field": "password", "message": "La contrasena debe incluir al menos un numero" } ] }
  ```

  Un JSON malformado también da `400`, con `detail` genérico y sin `errors`.

- **`409`** el correo ya tiene cuenta: `detail` = "Ya existe una cuenta con ese correo".

El login y el JWT llegan con SCRUM-13.
