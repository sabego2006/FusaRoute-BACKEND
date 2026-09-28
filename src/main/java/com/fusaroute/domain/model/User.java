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
 */
public class User {

    private final Long id;
    private final String name;
    private final Email email;
    private final String passwordHash;
    private final String phone;
    private final UserRole role;
    private final boolean active;

    /** Constructor completo, para rehidratar desde persistencia. */
    public User(Long id, String name, Email email, String passwordHash, String phone,
                UserRole role, boolean active) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.phone = phone;
        this.role = Objects.requireNonNull(role, "role");
        this.active = active;
    }

    /**
     * Alta nueva desde el registro publico (RF-01). Aqui vive la regla de
     * activacion inmediata (SCRUM-30): el usuario nace {@code active = true}, sin
     * verificacion por correo, y con rol {@link UserRole#USER}. La regla esta en
     * el dominio, no en un default de la base, para que sea explicita y testeable
     * sin levantar Postgres. El telefono se completa luego en el perfil (RF-03).
     */
    public static User register(String name, Email email, String passwordHash) {
        return new User(null, name, email, passwordHash, null, UserRole.USER, true);
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

    /**
     * Actualiza los datos editables del perfil (RF-03). Devuelve un nuevo User
     * inmutable — el original no se muta. El nombre y el correo ya vienen
     * validados por el caso de uso; el telefono puede ser null (opcional).
     */
    public User updateProfile(String newName, Email newEmail, String newPhone) {
        return new User(id, newName, newEmail, passwordHash, newPhone, role, active);
    }

    /**
     * Devuelve una copia con el hash de contrasena actualizado (RF-03, cambio de
     * contrasena). El caso de uso ya verifico la contrasena actual y valido la nueva.
     */
    public User withPasswordHash(String newPasswordHash) {
        return new User(id, name, email, newPasswordHash, phone, role, active);
    }
}
