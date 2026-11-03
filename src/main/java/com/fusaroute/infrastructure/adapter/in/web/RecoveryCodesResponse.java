package com.fusaroute.infrastructure.adapter.in.web;

import com.fusaroute.domain.port.in.RegenerateRecoveryCodesResult;

import java.util.List;

/**
 * Respuesta de {@code POST /api/users/me/totp/recovery-codes} (SCRUM-179). Trae
 * los codigos en claro, que se muestran UNA sola vez. Mismo nombre de campo que
 * {@link ConfirmTotpResponse#recoveryCodes()} para que el frontend reuse la
 * misma vista.
 */
public record RecoveryCodesResponse(List<String> recoveryCodes) {

    static RecoveryCodesResponse from(RegenerateRecoveryCodesResult result) {
        return new RecoveryCodesResponse(result.codes());
    }
}
