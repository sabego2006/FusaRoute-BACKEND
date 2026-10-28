package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.GetProfileUseCase;
import com.fusaroute.domain.port.out.UserRepositoryPort;

/**
 * Consulta del perfil del usuario autenticado (RF-03). Sin anotaciones de
 * Spring: el bean lo arma {@code UseCaseConfig}.
 */
public class GetProfileService implements GetProfileUseCase {

    private final UserRepositoryPort userRepository;

    public GetProfileService(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User getProfile(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }
}
