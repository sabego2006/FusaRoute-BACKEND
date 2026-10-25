package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.InvalidTotpException;
import com.fusaroute.domain.exception.TotpAlreadyEnabledException;
import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.IssuedToken;
import com.fusaroute.domain.model.RecoveryCode;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.ConfirmTotpCommand;
import com.fusaroute.domain.port.in.ConfirmTotpResult;
import com.fusaroute.domain.port.in.ConfirmTotpUseCase;
import com.fusaroute.domain.port.out.RecoveryCodePort;
import com.fusaroute.domain.port.out.RecoveryCodeRepositoryPort;
import com.fusaroute.domain.port.out.TokenIssuerPort;
import com.fusaroute.domain.port.out.TotpPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Paso 2 del enrolamiento TOTP (SCRUM-179): verifica el primer codigo, activa
 * el segundo factor, genera los codigos de respaldo (hasheados) y emite el JWT
 * completo de 7 dias.
 *
 * Confirmar = quedar logueado: no obligamos a un segundo relogin. El token
 * completo que devuelve es la "llave de 7 dias" definida en SCRUM-13/179.
 */
public class ConfirmTotpService implements ConfirmTotpUseCase {

    /** Vigencia del JWT completo, duplicada aqui para no cruzar con LoginService. */
    static final Duration TOKEN_VALIDITY = Duration.ofDays(7);

    /** Diez respaldos de un solo uso: estandar de la industria. */
    static final int RECOVERY_CODE_COUNT = 10;

    private final UserRepositoryPort userRepository;
    private final TotpPort totp;
    private final RecoveryCodePort recoveryCodes;
    private final RecoveryCodeRepositoryPort recoveryCodeRepository;
    private final TokenIssuerPort tokenIssuer;

    public ConfirmTotpService(UserRepositoryPort userRepository,
                              TotpPort totp,
                              RecoveryCodePort recoveryCodes,
                              RecoveryCodeRepositoryPort recoveryCodeRepository,
                              TokenIssuerPort tokenIssuer) {
        this.userRepository = userRepository;
        this.totp = totp;
        this.recoveryCodes = recoveryCodes;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.tokenIssuer = tokenIssuer;
    }

    @Override
    public ConfirmTotpResult confirm(ConfirmTotpCommand command) {
        User user = userRepository.findById(command.userId()).orElseThrow(UserNotFoundException::new);
        if (user.isTotpEnabled()) {
            throw new TotpAlreadyEnabledException();
        }
        if (user.getTotpSecret() == null) {
            // No hay enrolamiento en curso: el frontend se salto el /enroll.
            throw new InvalidTotpException();
        }
        if (command.code() == null || !totp.verifyCode(user.getTotpSecret(), command.code().trim())) {
            throw new InvalidTotpException();
        }

        User activated = userRepository.save(user.confirmTotp());

        List<String> plainCodes = recoveryCodes.generatePlainCodes(RECOVERY_CODE_COUNT);
        List<RecoveryCode> toPersist = new ArrayList<>(plainCodes.size());
        for (String plain : plainCodes) {
            toPersist.add(RecoveryCode.newFor(activated.getId(), recoveryCodes.hash(plain)));
        }
        recoveryCodeRepository.replaceAllForUser(activated.getId(), toPersist);

        IssuedToken token = tokenIssuer.issue(activated, TOKEN_VALIDITY);
        return new ConfirmTotpResult(activated, token, plainCodes);
    }
}
