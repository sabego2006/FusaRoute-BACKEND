package com.fusaroute.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Usuario del sistema. Objeto de dominio puro: no conoce JPA ni Spring.
 *
 * Se crea de dos formas distintas a proposito:
 * <ul>
 *   <li>{@link #register} para un alta nueva desde el registro publico, donde
 *       viven las reglas de negocio (rol USER y activacion inmediata);</li>
 *   <li>el constructor completo para rehidratar una fila ya existente desde la
 *       persistencia, sin volver a aplicar esas reglas.</li>
 * </ul>
 *
 * El segundo factor (SCRUM-179) vive en dos campos: {@code totpEnabled} y
 * {@code totpSecret}. El secreto se guarda en claro aqui a proposito — es un
 * objeto de dominio en memoria —; el cifrado en reposo es responsabilidad del
 * adaptador de persistencia, no del dominio.
 */
public class User {

    /**
     * Umbral de bloqueo temporal (SCRUM-155): al llegar a este numero de
     * intentos fallidos consecutivos, la cuenta se bloquea. El intento que lo
     * alcanza (el quinto) es el que responde con el aviso de bloqueo, no el
     * generico.
     */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** Duracion del bloqueo temporal (SCRUM-155). */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Long id;
    private final String name;
    private final Email email;
    private final String passwordHash;
    private final String phone;
    private final UserRole role;
    private final boolean active;
    private final int failedLoginAttempts;
    private final boolean totpEnabled;
    private final String totpSecret;
    private final Instant lockedUntil;
    private final Instant lastLoginAt;
    private final Instant previousLoginAt;

    /**
     * Constructor completo, para rehidratar desde persistencia. Mantiene las
     * sobrecargas de abajo por retrocompatibilidad: al momento de SCRUM-179 varios
     * tests ya construian al usuario con 8 parametros, y pedir que TODOS se
     * actualicen a mano cada vez que se agrega un campo que arranca en falso/null
     * seria mas ruido que seguridad. Esas sobrecargas delegan aqui.
     *
     * {@code lockedUntil} es nulo cuando la cuenta no esta bloqueada (SCRUM-155).
     *
     * {@code lastLoginAt} y {@code previousLoginAt} son los dos ultimos ingresos
     * exitosos, y son DOS campos a proposito (SCRUM-101). Con uno solo no se puede
     * responder "que me respondieron desde la ultima vez que entre": el login lo
     * acaba de mover a "ahora", asi que la comparacion daria cero justo cuando el
     * frontend pregunta. {@code lastLoginAt} es el dato de auditoria (cuando entro)
     * y {@code previousLoginAt} es contra el que cuenta las respuestas nuevas.
     * Ambos son nulos mientras el usuario no haya iniciado sesion nunca.
     */
    public User(Long id, String name, Email email, String passwordHash, String phone,
                UserRole role, boolean active, int failedLoginAttempts,
                boolean totpEnabled, String totpSecret, Instant lockedUntil,
                Instant lastLoginAt, Instant previousLoginAt) {
        if (failedLoginAttempts < 0) {
            throw new IllegalArgumentException("failedLoginAttempts no puede ser negativo");
        }
        if (totpEnabled && (totpSecret == null || totpSecret.isBlank())) {
            // Un usuario con TOTP activo SIEMPRE debe tener secreto. Si la fila
            // llega inconsistente, es mejor romper el arranque de la peticion
            // que dejar pasar un login con un segundo factor que nadie puede
            // verificar.
            throw new IllegalArgumentException("totpEnabled sin totpSecret");
        }
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.phone = phone;
        this.role = Objects.requireNonNull(role, "role");
        this.active = active;
        this.failedLoginAttempts = failedLoginAttempts;
        this.totpEnabled = totpEnabled;
        this.totpSecret = totpSecret;
        this.lockedUntil = lockedUntil;
        this.lastLoginAt = lastLoginAt;
        this.previousLoginAt = previousLoginAt;
    }

    /**
     * Sobrecarga por retrocompatibilidad: usuario sin registro de ingresos. Queda
     * para no reescribir los tests que ya construian al usuario con 11 parametros
     * antes de SCRUM-101.
     */
    public User(Long id, String name, Email email, String passwordHash, String phone,
                UserRole role, boolean active, int failedLoginAttempts,
                boolean totpEnabled, String totpSecret, Instant lockedUntil) {
        this(id, name, email, passwordHash, phone, role, active, failedLoginAttempts,
                totpEnabled, totpSecret, lockedUntil, null, null);
    }

    /**
     * Sobrecarga por retrocompatibilidad: usuario sin TOTP y sin bloqueo. Nuevos
     * sitios deben usar el constructor completo; esta queda para no reescribir
     * cada test existente al introducir TOTP y, luego, el bloqueo temporal.
     */
    public User(Long id, String name, Email email, String passwordHash, String phone,
                UserRole role, boolean active, int failedLoginAttempts) {
        this(id, name, email, passwordHash, phone, role, active, failedLoginAttempts,
                false, null, null);
    }

    /**
     * Alta nueva desde el registro publico (RF-01). Aqui vive la regla de
     * activacion inmediata (SCRUM-30): el usuario nace {@code active = true}, sin
     * verificacion por correo, y con rol {@link UserRole#USER}. La regla esta en
     * el dominio, no en un default de la base, para que sea explicita y testeable
     * sin levantar Postgres. El telefono se completa luego en el perfil (RF-03).
     * El TOTP arranca apagado: el usuario lo enrola en su primer login (SCRUM-179).
     */
    public static User register(String name, Email email, String passwordHash) {
        return new User(null, name, email, passwordHash, null, UserRole.USER, true, 0,
                false, null, null);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Email getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getPhone() {
        return phone;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public boolean isTotpEnabled() {
        return totpEnabled;
    }

    public String getTotpSecret() {
        return totpSecret;
    }

    /** Instante hasta el cual la cuenta esta bloqueada, o {@code null} si no lo esta (SCRUM-155). */
    public Instant getLockedUntil() {
        return lockedUntil;
    }

    /**
     * La cuenta esta bloqueada si {@code lockedUntil} existe y aun no se alcanza
     * (SCRUM-155). Un {@code lockedUntil} ya pasado no cuenta como bloqueo: el
     * caso de uso debe limpiarlo con {@link #clearLockAndResetAttempts()} antes
     * de evaluar credenciales.
     */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /**
     * Actualiza los datos editables del perfil (RF-03). Devuelve un nuevo User
     * inmutable — el original no se muta. El nombre y el correo ya vienen
     * validados por el caso de uso; el telefono puede ser null (opcional).
     */
    public User updateProfile(String newName, Email newEmail, String newPhone) {
        return new User(id, newName, newEmail, passwordHash, newPhone, role, active,
                failedLoginAttempts, totpEnabled, totpSecret, lockedUntil, lastLoginAt, previousLoginAt);
    }

    /**
     * Devuelve una copia con el hash de contrasena actualizado (RF-03, cambio de
     * contrasena). El caso de uso ya verifico la contrasena actual y valido la nueva.
     */
    public User withPasswordHash(String newPasswordHash) {
        return new User(id, name, email, newPasswordHash, phone, role, active,
                failedLoginAttempts, totpEnabled, totpSecret, lockedUntil, lastLoginAt, previousLoginAt);
    }

    /**
     * Copia con un intento fallido mas (RF-02, SCRUM-36). El caso de uso la llama
     * solo cuando el correo existe y la contrasena no coincide, o cuando el codigo
     * TOTP es incorrecto (SCRUM-179). Si este intento alcanza
     * {@link #MAX_FAILED_ATTEMPTS}, la copia resultante queda bloqueada
     * {@link #LOCK_DURATION} a partir de {@code now} (SCRUM-155): es el intento
     * que recibe el aviso de bloqueo, no el generico.
     */
    public User registerFailedLogin(Instant now) {
        int newAttempts = failedLoginAttempts + 1;
        Instant newLockedUntil = newAttempts >= MAX_FAILED_ATTEMPTS
                ? now.plus(LOCK_DURATION)
                : null;
        return new User(id, name, email, passwordHash, phone, role, active,
                newAttempts, totpEnabled, totpSecret, newLockedUntil, lastLoginAt, previousLoginAt);
    }

    /** Copia con el contador en cero y sin bloqueo, tras un inicio de sesion exitoso. */
    public User resetFailedLogins() {
        return new User(id, name, email, passwordHash, phone, role, active, 0,
                totpEnabled, totpSecret, null, lastLoginAt, previousLoginAt);
    }

    /**
     * Copia sin bloqueo y con el contador en cero. El caso de uso la llama
     * cuando {@code lockedUntil} ya paso: la ventana de bloqueo expiro y el
     * intento actual se evalua de cero, como si el contador nunca hubiera
     * llegado al limite (SCRUM-155).
     */
    public User clearLockAndResetAttempts() {
        return new User(id, name, email, passwordHash, phone, role, active, 0,
                totpEnabled, totpSecret, null, lastLoginAt, previousLoginAt);
    }

    /**
     * Guarda un secreto TOTP recien generado, dejando el segundo factor sin
     * activar hasta que el usuario confirme con un codigo valido (SCRUM-179).
     */
    public User startTotpEnrollment(String newSecret) {
        if (newSecret == null || newSecret.isBlank()) {
            throw new IllegalArgumentException("totpSecret vacio");
        }
        return new User(id, name, email, passwordHash, phone, role, active,
                failedLoginAttempts, false, newSecret, lockedUntil, lastLoginAt, previousLoginAt);
    }

    /** Activa el segundo factor: el usuario demostro que su app genera codigos validos. */
    public User confirmTotp() {
        if (totpSecret == null) {
            throw new IllegalStateException("No hay un enrolamiento TOTP en curso");
        }
        return new User(id, name, email, passwordHash, phone, role, active,
                failedLoginAttempts, true, totpSecret, lockedUntil, lastLoginAt, previousLoginAt);
    }

    /**
     * Borra el secreto y apaga el segundo factor. En el alcance actual
     * (TOTP obligatorio) se usa solo para re-enrolar: el usuario autenticado con
     * un token full llama {@code /enroll} de nuevo y pasa por aqui antes de
     * guardar el secreto nuevo.
     */
    /**
     * Copia que registra un ingreso exitoso (SCRUM-101): el ultimo ingreso pasa a
     * ser el anterior y {@code now} queda como el ultimo. Ademas limpia el contador
     * de fallidos y el bloqueo, porque entrar bien es justo lo que los reinicia.
     *
     * Corre el valor en vez de sobreescribirlo porque el contador de respuestas
     * nuevas de RF-14 se cuenta "desde la ultima vez que entre", y en el momento en
     * que el frontend pregunta, "la ultima vez" ya es este mismo ingreso. Sin el
     * valor corrido el contador seria cero siempre.
     *
     * En el PRIMER ingreso el anterior queda nulo, y eso significa "no hay un
     * antes": el contador devuelve 0 en vez de contar todo el historial del usuario.
     */
    public User registerSuccessfulLogin(Instant now) {
        return new User(id, name, email, passwordHash, phone, role, active, 0,
                totpEnabled, totpSecret, null, now, lastLoginAt);
    }

    /** Ultimo ingreso exitoso, o {@code null} si nunca ha iniciado sesion (SCRUM-101). */
    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    /**
     * Ingreso exitoso ANTERIOR al ultimo, o {@code null} si solo ha entrado una vez
     * (SCRUM-101). Es el instante contra el que se cuentan las respuestas nuevas.
     */
    public Instant getPreviousLoginAt() {
        return previousLoginAt;
    }

    public User resetTotp() {
        return new User(id, name, email, passwordHash, phone, role, active,
                failedLoginAttempts, false, null, lockedUntil, lastLoginAt, previousLoginAt);
    }
}
