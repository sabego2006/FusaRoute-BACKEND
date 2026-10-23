package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.TotpAlreadyEnabledException;
import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.EnrollTotpUseCase;
import com.fusaroute.domain.port.in.TotpEnrollmentResult;
import com.fusaroute.domain.port.out.TotpPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;

/**
 * Paso 1 del enrolamiento TOTP (SCRUM-179): genera un secreto nuevo, lo guarda
 * en la fila del usuario y devuelve el URI {@code otpauth://} + el QR en PNG
 * Base64 para que la app autenticadora los registre. El segundo factor queda
 * sin activar hasta la confirmacion.
 *
 * Si el usuario llama otra vez antes de confirmar, se reemplaza el secreto
 * pendiente — el usuario pudo haber borrado la cuenta de la app autenticadora o
 * cambiado de telefono. Si YA tiene TOTP activo, se lanza
 * {@link TotpAlreadyEnabledException}: el flujo de re-enrolamiento debe
 * desactivarlo primero (fuera del alcance actual).
 */
public class EnrollTotpService implements EnrollTotpUseCase {

    private final UserRepositoryPort userRepository;
    private final TotpPort totp;
    private final String issuer;

    public EnrollTotpService(UserRepositoryPort userRepository, TotpPort totp, String issuer) {
        this.userRepository = userRepository;
        this.totp = totp;
        this.issuer = issuer;
    }

    @Override
    public TotpEnrollmentResult enroll(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (user.isTotpEnabled()) {
            throw new TotpAlreadyEnabledException();
        }

        String secret = totp.generateSecret();
        userRepository.save(user.startTotpEnrollment(secret));

        String otpauthUri = totp.provisioningUri(secret, user.getEmail().value(), issuer);
        String qr = totp.qrPngBase64(otpauthUri);
        return new TotpEnrollmentResult(secret, otpauthUri, qr);
    }
}
