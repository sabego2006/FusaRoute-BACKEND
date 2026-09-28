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

### `POST /api/auth/login` — Inicio de sesión (RF-02, SCRUM-13)

Endpoint público (no requiere token). Autentica con correo y contraseña y emite un
JWT (HS256) con validez de **una semana** (RNF-04); vencido, el usuario vuelve a
loguearse.

**Request** (`application/json`):

```json
{ "email": "ana@ejemplo.com", "password": "Clave123" }
```

El correo se normaliza (trim + minúsculas) igual que en el registro.

**`200 OK`** — nunca incluye el hash de la contraseña:

```json
{ "token": "eyJhbGciOi...", "tokenType": "Bearer", "expiresAt": "2026-10-05T12:00:00Z",
  "user": { "id": 1, "name": "Ana Pérez", "email": "ana@ejemplo.com", "role": "USER", "active": true } }
```

El token lleva el id del usuario en `sub` y el rol en `role`. El frontend lo guarda y lo
envía en cada petición autenticada:

```
Authorization: Bearer eyJhbGciOi...
```

**Errores** (cuerpo `ProblemDetail`, RFC 9457):

- **`401`** credenciales incorrectas: `detail` = "Correo o contrasena incorrectos". Es el
  **mismo mensaje** para correo inexistente, contraseña incorrecta, cuenta inactiva y
  formato de correo inválido, para no revelar qué correos tienen cuenta.

Cualquier otro endpoint (salvo `/health` y el registro) responde **`401`** sin token, o con
un token inválido o vencido.

Fuera de este sprint: el contador de intentos fallidos y el bloqueo temporal (SCRUM-155,
Sprint 3), y no hay logout ni revocación: un token es válido hasta que vence.

**Configuración:** requiere la variable `JWT_SECRET` (Base64, mínimo 32 bytes decodificados);
sin ella, o si es débil, la aplicación no arranca. Genérala con `openssl rand -base64 32`.
