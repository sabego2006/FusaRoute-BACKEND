package com.fusaroute.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void register_crea_usuario_activo_con_rol_user_y_sin_totp() {
        User user = User.register("Ana", Email.of("ana@x.co"), "$2a$12$hash");

        assertThat(user.getId()).isNull();
        assertThat(user.getName()).isEqualTo("Ana");
        assertThat(user.getEmail().value()).isEqualTo("ana@x.co");
        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$hash");
        assertThat(user.getPhone()).isNull();
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.isActive()).isTrue();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.isTotpEnabled()).isFalse();
        assertThat(user.getTotpSecret()).isNull();
    }

    @Test
    void registerFailedLogin_devuelve_copia_con_un_intento_mas() {
        User original = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash",
                null, UserRole.USER, true, 2);

        User updated = original.registerFailedLogin(Instant.now());

        assertThat(updated.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getEmail()).isEqualTo(original.getEmail());
        // El original no se muto
        assertThat(original.getFailedLoginAttempts()).isEqualTo(2);
    }

    // --- Bloqueo temporal (SCRUM-155). -------------------------------------------

    @Test
    void registerFailedLogin_por_debajo_del_umbral_no_bloquea() {
        User original = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash",
                null, UserRole.USER, true, 2);

        User updated = original.registerFailedLogin(Instant.now());

        assertThat(updated.getFailedLoginAttempts()).isEqualTo(3);
        assertThat(updated.getLockedUntil()).isNull();
    }

    @Test
    void registerFailedLogin_al_alcanzar_el_umbral_bloquea_por_la_duracion_configurada() {
        User original = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash",
                null, UserRole.USER, true, User.MAX_FAILED_ATTEMPTS - 1);
        Instant now = Instant.now();

        User updated = original.registerFailedLogin(now);

        assertThat(updated.getFailedLoginAttempts()).isEqualTo(User.MAX_FAILED_ATTEMPTS);
        assertThat(updated.getLockedUntil()).isEqualTo(now.plus(User.LOCK_DURATION));
        assertThat(updated.isLocked(now)).isTrue();
    }

    @Test
    void isLocked_es_falso_sin_lockedUntil_o_cuando_ya_expiro() {
        User sinBloqueo = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash",
                null, UserRole.USER, true, 0);
        Instant now = Instant.now();
        User bloqueoExpirado = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash",
                null, UserRole.USER, true, User.MAX_FAILED_ATTEMPTS, false, null,
                now.minus(Duration.ofSeconds(1)));

        assertThat(sinBloqueo.isLocked(now)).isFalse();
        assertThat(bloqueoExpirado.isLocked(now)).isFalse();
    }

    @Test
    void clearLockAndResetAttempts_borra_el_bloqueo_y_reinicia_el_contador() {
        Instant now = Instant.now();
        User bloqueado = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash",
                null, UserRole.USER, true, User.MAX_FAILED_ATTEMPTS, false, null,
                now.plus(User.LOCK_DURATION));

        User limpio = bloqueado.clearLockAndResetAttempts();

        assertThat(limpio.getFailedLoginAttempts()).isZero();
        assertThat(limpio.getLockedUntil()).isNull();
        assertThat(limpio.isLocked(now)).isFalse();
    }

    @Test
    void resetFailedLogins_devuelve_copia_con_el_contador_en_cero() {
        User original = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash",
                null, UserRole.USER, true, 4);

        User updated = original.resetFailedLogins();

        assertThat(updated.getFailedLoginAttempts()).isZero();
        assertThat(original.getFailedLoginAttempts()).isEqualTo(4);
    }

    @Test
    void un_contador_negativo_se_rechaza() {
        assertThatThrownBy(() -> new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash",
                null, UserRole.USER, true, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateProfile_y_withPasswordHash_conservan_el_contador() {
        User original = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash",
                null, UserRole.USER, true, 3);

        assertThat(original.updateProfile("Ana M", Email.of("a@x.co"), null)
                .getFailedLoginAttempts()).isEqualTo(3);
        assertThat(original.withPasswordHash("$2a$12$nuevo")
                .getFailedLoginAttempts()).isEqualTo(3);
    }

    @Test
    void updateProfile_devuelve_copia_con_datos_nuevos() {
        User original = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash",
                null, UserRole.USER, true, 0);

        User updated = original.updateProfile("Ana Maria", Email.of("nueva@x.co"), "3101234567");

        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getName()).isEqualTo("Ana Maria");
        assertThat(updated.getEmail().value()).isEqualTo("nueva@x.co");
        assertThat(updated.getPhone()).isEqualTo("3101234567");
        assertThat(updated.getPasswordHash()).isEqualTo("$2a$12$hash");
        // El original no se muto
        assertThat(original.getName()).isEqualTo("Ana");
        assertThat(original.getPhone()).isNull();
    }

    @Test
    void withPasswordHash_devuelve_copia_con_nuevo_hash() {
        User original = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$viejo",
                "3101234567", UserRole.USER, true, 0);

        User updated = original.withPasswordHash("$2a$12$nuevo");

        assertThat(updated.getPasswordHash()).isEqualTo("$2a$12$nuevo");
        assertThat(updated.getName()).isEqualTo("Ana");
        assertThat(updated.getPhone()).isEqualTo("3101234567");
        assertThat(original.getPasswordHash()).isEqualTo("$2a$12$viejo");
    }

    // --- Segundo factor TOTP (SCRUM-179). ----------------------------------------

    @Test
    void startTotpEnrollment_guarda_secreto_y_deja_el_segundo_factor_apagado() {
        User original = User.register("Ana", Email.of("ana@x.co"), "$2a$12$hash");

        User enrolling = original.startTotpEnrollment("JBSWY3DPEHPK3PXP");

        assertThat(enrolling.getTotpSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(enrolling.isTotpEnabled()).isFalse();
        // El original no se muto
        assertThat(original.getTotpSecret()).isNull();
    }

    @Test
    void startTotpEnrollment_con_secreto_nulo_o_vacio_lanza() {
        User user = User.register("Ana", Email.of("ana@x.co"), "$2a$12$hash");

        assertThatThrownBy(() -> user.startTotpEnrollment(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.startTotpEnrollment("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirmTotp_activa_el_segundo_factor_cuando_hay_secreto() {
        User enrolling = User.register("Ana", Email.of("ana@x.co"), "$2a$12$hash")
                .startTotpEnrollment("JBSWY3DPEHPK3PXP");

        User confirmed = enrolling.confirmTotp();

        assertThat(confirmed.isTotpEnabled()).isTrue();
        assertThat(confirmed.getTotpSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void confirmTotp_sin_secreto_lanza_para_evitar_estado_incoherente() {
        User user = User.register("Ana", Email.of("ana@x.co"), "$2a$12$hash");

        assertThatThrownBy(user::confirmTotp)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resetTotp_borra_el_secreto_y_apaga() {
        User activated = User.register("Ana", Email.of("ana@x.co"), "$2a$12$hash")
                .startTotpEnrollment("JBSWY3DPEHPK3PXP")
                .confirmTotp();

        User reset = activated.resetTotp();

        assertThat(reset.isTotpEnabled()).isFalse();
        assertThat(reset.getTotpSecret()).isNull();
    }

    @Test
    void totp_activo_sin_secreto_se_rechaza_en_el_constructor() {
        // Protege contra una fila incoherente (totp_enabled=true, totp_secret=null)
        // que la restriccion ck_users_totp_consistency de V7 ya impide a nivel BD.
        assertThatThrownBy(() -> new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash",
                null, UserRole.USER, true, 0, true, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void copias_conservan_el_estado_totp() {
        User activated = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash",
                null, UserRole.USER, true, 0, true, "JBSWY3DPEHPK3PXP", null);

        assertThat(activated.registerFailedLogin(Instant.now()).isTotpEnabled()).isTrue();
        assertThat(activated.registerFailedLogin(Instant.now()).getTotpSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(activated.resetFailedLogins().isTotpEnabled()).isTrue();
        assertThat(activated.updateProfile("Ana M", Email.of("ana@x.co"), null).isTotpEnabled()).isTrue();
        assertThat(activated.withPasswordHash("$2a$12$otro").getTotpSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    // --- Registro de ingresos (SCRUM-101) ---------------------------------------

    /**
     * El ingreso CORRE el valor: lo que era el ultimo pasa a ser el anterior. Es la
     * pieza de la que depende el contador de respuestas nuevas de RF-14; si en vez de
     * correrse se sobreescribiera, el contador compararia contra un instante que
     * acaba de ocurrir y daria cero siempre.
     */
    @Test
    void registrar_un_ingreso_corre_el_ultimo_al_anterior() {
        Instant primero = Instant.parse("2026-11-01T08:00:00Z");
        Instant segundo = Instant.parse("2026-11-07T08:00:00Z");
        User conUnIngreso = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash",
                null, UserRole.USER, true, 0, false, null, null, primero, null);

        User conDos = conUnIngreso.registerSuccessfulLogin(segundo);

        assertThat(conDos.getLastLoginAt()).isEqualTo(segundo);
        assertThat(conDos.getPreviousLoginAt()).isEqualTo(primero);
    }

    @Test
    void el_primer_ingreso_deja_el_anterior_en_nulo() {
        User nueva = User.register("Ana", Email.of("ana@x.co"), "$2a$12$hash");

        User despues = nueva.registerSuccessfulLogin(Instant.parse("2026-11-07T08:00:00Z"));

        assertThat(despues.getLastLoginAt()).isEqualTo(Instant.parse("2026-11-07T08:00:00Z"));
        // Nulo significa "no hay un antes": el contador devuelve 0 en vez de contar
        // todo el historial de la cuenta.
        assertThat(despues.getPreviousLoginAt()).isNull();
    }

    @Test
    void registrar_un_ingreso_limpia_el_contador_de_fallidos_y_el_bloqueo() {
        User conFallos = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                UserRole.USER, true, 3, false, null,
                Instant.parse("2026-11-07T09:00:00Z"), null, null);

        User despues = conFallos.registerSuccessfulLogin(Instant.parse("2026-11-07T10:00:00Z"));

        assertThat(despues.getFailedLoginAttempts()).isZero();
        assertThat(despues.getLockedUntil()).isNull();
    }

    /**
     * Cualquier copia del usuario tiene que arrastrar el registro de ingresos. Sin
     * esto, cambiar la contrasena o enrolar el segundo factor borraria el instante
     * del ultimo ingreso y el contador de respuestas nuevas se reiniciaria sin que
     * nadie lo pidiera.
     */
    @Test
    void las_copias_del_usuario_conservan_el_registro_de_ingresos() {
        Instant ultimo = Instant.parse("2026-11-07T08:00:00Z");
        Instant anterior = Instant.parse("2026-11-01T08:00:00Z");
        User original = new User(1L, "Ana", Email.of("ana@x.co"), "$2a$12$hash", null,
                UserRole.USER, true, 0, true, "JBSWY3DPEHPK3PXP", null, ultimo, anterior);

        assertThat(original.withPasswordHash("$2a$12$nuevo").getLastLoginAt()).isEqualTo(ultimo);
        assertThat(original.updateProfile("Ana Maria", Email.of("otra@x.co"), null)
                .getPreviousLoginAt()).isEqualTo(anterior);
        assertThat(original.resetTotp().getLastLoginAt()).isEqualTo(ultimo);
        assertThat(original.registerFailedLogin(ultimo).getPreviousLoginAt()).isEqualTo(anterior);
        assertThat(original.resetFailedLogins().getLastLoginAt()).isEqualTo(ultimo);
        assertThat(original.startTotpEnrollment("OTROSECRETO").getLastLoginAt()).isEqualTo(ultimo);
        assertThat(original.confirmTotp().getPreviousLoginAt()).isEqualTo(anterior);
        assertThat(original.clearLockAndResetAttempts().getLastLoginAt()).isEqualTo(ultimo);
    }
}
