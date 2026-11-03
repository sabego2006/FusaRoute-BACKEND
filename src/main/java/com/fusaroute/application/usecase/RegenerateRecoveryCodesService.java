package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.TotpNotEnabledException;
import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.RecoveryCode;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.RegenerateRecoveryCodesResult;
import com.fusaroute.domain.port.in.RegenerateRecoveryCodesUseCase;
import com.fusaroute.domain.port.out.RecoveryCodePort;
import com.fusaroute.domain.port.out.RecoveryCodeRepositoryPort;
import com.fusaroute.domain.port.out.UserRepositoryPort;

import java.util.ArrayList;
import java.util.List;

/**
 * Regenera los codigos de respaldo del segundo factor (SCRUM-179). Sin anotaciones
 * de Spring: recibe sus puertos por constructor y el bean lo arma
 * {@code UseCaseConfig}.
 *
 * Reemplaza el conjunto completo, igual que {@link ConfirmTotpService}: dejar
 * vivos los anteriores haria que un codigo filtrado hace meses siguiera abriendo
 * la cuenta.
 */
public class RegenerateRecoveryCodesService implements RegenerateRecoveryCodesUseCase {

    /**
     * Diez respaldos de un solo uso. La constante esta duplicada con
     * {@link ConfirmTotpService#RECOVERY_CODE_COUNT} a proposito, igual que
     * {@code TOKEN_VALIDITY} entre {@code LoginService} y {@code ConfirmTotpService}:
     * sacar un int a una clase de configuracion compartida seria mas indireccion
     * que valor.
     */
    static final int RECOVERY_CODE_COUNT = 10;

    private final UserRepositoryPort userRepository;
    private final RecoveryCodePort recoveryCodes;
    private final RecoveryCodeRepositoryPort recoveryCodeRepository;

    public RegenerateRecoveryCodesService(UserRepositoryPort userRepository,
                                          RecoveryCodePort recoveryCodes,
                                          RecoveryCodeRepositoryPort recoveryCodeRepository) {
        this.userRepository = userRepository;
        this.recoveryCodes = recoveryCodes;
        this.recoveryCodeRepository = recoveryCodeRepository;
    }

    @Override
    public RegenerateRecoveryCodesResult regenerate(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        if (!user.isTotpEnabled()) {
            // Sin factor activo no hay nada que respaldar, y emitir codigos aqui
            // crearia una via de entrada que no exige el segundo factor.
            throw new TotpNotEnabledException();
        }

        List<String> plainCodes = recoveryCodes.generatePlainCodes(RECOVERY_CODE_COUNT);
        List<RecoveryCode> toPersist = new ArrayList<>(plainCodes.size());
        for (String plain : plainCodes) {
            toPersist.add(RecoveryCode.newFor(userId, recoveryCodes.hash(plain)));
        }
        recoveryCodeRepository.replaceAllForUser(userId, toPersist);

        return new RegenerateRecoveryCodesResult(plainCodes);
    }
}
