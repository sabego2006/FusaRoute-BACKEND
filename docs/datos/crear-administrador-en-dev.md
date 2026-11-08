# Crear un administrador en DEV, a mano

Para probar los endpoints de `/api/admin/**` (RF-11, RF-13, RF-14) hace falta una
cuenta con rol `ADMIN`. **No existe un registro público que cree administradores, y no
debe existir:** el registro de RF-01 siempre nace con rol `USER`, y esa regla vive en
el dominio (`User.register`), no en un *default* de la base.

**Tampoco se crea con una migración.** Una migración versionada queda en el
repositorio para siempre, así que meter ahí un correo y un hash de contraseña sería
publicar una credencial — exactamente lo que prohíbe la regla de secretos del
`CLAUDE.md`. El administrador se crea a mano, una vez, en cada ambiente.

## Lo que hay que saber antes

El login exige **segundo factor TOTP obligatorio** (SCRUM-179). Un `INSERT` suelto en
`users` **no alcanza** para entrar: deja la cuenta a medias y el login responde 401 con
`error = TOTP_ENROLLMENT_REQUIRED`. Eso no es un problema, es el camino previsto, y los
pasos de abajo lo aprovechan en vez de pelearse con él.

## Procedimiento

### 1. Registrar la cuenta por el endpoint público

Se registra como cualquier usuario, con el formulario o con la API:

```http
POST /api/auth/register
Content-Type: application/json

{ "name": "Admin FusaRoute", "email": "admin@fusaroute.dev", "password": "<una propia>" }
```

Hacerlo así y no con un `INSERT` tiene una razón concreta: la contraseña queda hasheada
con BCrypt por el mismo adaptador que usa la aplicación. Un hash pegado a mano desde
otra herramienta puede no corresponder al *cost* configurado y el login falla sin decir
por qué.

> La contraseña no se escribe en este archivo ni en ningún otro del repositorio. Cada
> integrante usa la suya en su ambiente.

### 2. Enrolar el segundo factor

Iniciar sesión una vez. El login responde 401 con `error = TOTP_ENROLLMENT_REQUIRED` y
un `enrollmentToken`; con ese token se llama a `POST /api/users/me/totp/enroll` y luego
a `POST /api/users/me/totp/confirm` con el primer código de la app autenticadora. Es el
mismo flujo que ve cualquier usuario nuevo, así que lo más simple es hacerlo desde el
frontend.

Al confirmar quedan los 10 códigos de respaldo. **Guardarlos**: son la única forma de
entrar si se pierde el dispositivo con la app autenticadora.

### 3. Subir el rol a ADMIN

Este es el único paso que se hace en la base, y es una sola línea. Conectarse al
proyecto Supabase de DEV **con el rol `fusaroute_dev`**, nunca con el administrador del
proyecto (regla del `CLAUDE.md`):

```sql
UPDATE users
   SET role = 'ADMIN'
 WHERE email = 'admin@fusaroute.dev';
```

El `CHECK ck_users_role` (V1) solo admite `'USER'` y `'ADMIN'`, así que un error de
escritura se rechaza en vez de dejar una cuenta con un rol que nadie reconoce.

### 4. Volver a iniciar sesión

**Este paso no es opcional.** El rol viaja dentro del JWT (claim `role`), así que el
token que ya está en el navegador sigue diciendo `USER` hasta que venza — y los tokens
duran una semana (RNF-04). Cerrar sesión y entrar de nuevo es lo que hace que el token
nuevo traiga `role = ADMIN` y que `/api/admin/**` deje de responder 403.

## Comprobar que quedó bien

```sql
SELECT id, name, email, role, active, totp_enabled, last_login_at
  FROM users
 WHERE role = 'ADMIN';
```

Se espera `role = ADMIN`, `active = true` y `totp_enabled = true`. Si
`totp_enabled` está en `false`, falta el paso 2 y la cuenta no puede iniciar sesión.

## Estado al 2026-11-07

**No verificado.** Las credenciales de DEV no están en el repositorio (y no deben
estarlo), así que esta sesión no pudo consultar si la base compartida ya tiene un
administrador. Antes de probar `/api/admin/**` por primera vez, correr la consulta de
arriba; si devuelve cero filas, seguir el procedimiento.
