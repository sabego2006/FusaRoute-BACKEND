package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.port.in.GetTotpStatusUseCase;
import com.fusaroute.domain.port.out.UserRepositoryPort;

/** Consulta trivial del estado del segundo factor (SCRUM-179). */
public class GetTotpStatusService implements GetTotpStatusUseCase {

    private final UserRepositoryPort userRepository;

    public GetTotpStatusService(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean isEnabled(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new)
                .isTotpEnabled();
    }
}
