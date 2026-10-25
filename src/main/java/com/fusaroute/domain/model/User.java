package com.fusaroute.domain.model;

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

    /**
     * Constructor completo, para rehidratar desde persistencia. Mantiene la
     * sobrecarga de 8 parametros abajo por retrocompatibilidad: al momento de
     * SCRUM-179 varios tests ya construyen al usuario asi, y pedir que TODOS se
     * actualicen a mano para dos campos que arrancan en falso/null seria mas
     * ruido que seguridad. Esa sobrecarga delega aqui con TOTP apagado.
     */
    public User(Long id, String name, Email email, String passwordHash, String phone,
                UserRole role, boolean active, int failedLoginAttempts,
                boolean totpEnabled, String totpSecret) {
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
    }

    /**
     * Sobrecarga por retrocompatibilidad: usuario sin TOTP. Nuevos sitios deben
     * usar el constructor completo; esta queda para no reescribir cada test
     * existente al introducir TOTP.
     */
    public User(Long id, String name, Email email, String passwordHash, String phone,
                UserRole role, boolean active, int failedLoginAttempts) {
        this(id, name, email, passwordHash, phone, role, active, failedLoginAttempts,
                false, null);
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
                false, null);
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

    /**
     * Actualiza los datos editables del perfil (RF-03). Devuelve un nuevo User
     * inmutable — el original no se muta. El nombre y el correo ya vienen
     * validados por el caso de uso; el telefono puede ser null (opcional).
     */
    public User updateProfile(String newName, Email newEmail, String newPhone) {
        return new User(id, newName, newEmail, passwordHash, newPhone, role, active,
                failedLoginAttempts, totpEnabled, totpSecret);
    }

    /**
     * Devuelve una copia con el hash de contrasena actualizado (RF-03, cambio de
     * contrasena). El caso de uso ya verifico la contrasena actual y valido la nueva.
     */
    public User withPasswordHash(String newPasswordHash) {
        return new User(id, name, email, newPasswordHash, phone, role, active,
                failedLoginAttempts, totpEnabled, totpSecret);
    }

    /**
     * Copia con un intento fallido mas (RF-02, SCRUM-36). El caso de uso la llama
     * solo cuando el correo existe y la contrasena no coincide, o cuando el codigo
     * TOTP es incorrecto (SCRUM-179).
     */
    public User registerFailedLogin() {
        return new User(id, name, email, passwordHash, phone, role, active,
                failedLoginAttempts + 1, totpEnabled, totpSecret);
    }

    /** Copia con el contador en cero, tras un inicio de sesion exitoso. */
    public User resetFailedLogins() {
        return new User(id, name, email, passwordHash, phone, role, active, 0,
                totpEnabled, totpSecret);
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
                failedLoginAttempts, false, newSecret);
    }

    /** Activa el segundo factor: el usuario demostro que su app genera codigos validos. */
    public User confirmTotp() {
        if (totpSecret == null) {
            throw new IllegalStateException("No hay un enrolamiento TOTP en curso");
        }
        return new User(id, name, email, passwordHash, phone, role, active,
                failedLoginAttempts, true, totpSecret);
    }

    /**
     * Borra el secreto y apaga el segundo factor. En el alcance actual
     * (TOTP obligatorio) se usa solo para re-enrolar: el usuario autenticado con
     * un token full llama {@code /enroll} de nuevo y pasa por aqui antes de
     * guardar el secreto nuevo.
     */
    public User resetTotp() {
        return new User(id, name, email, passwordHash, phone, role, active,
                failedLoginAttempts, false, null);
    }
}
