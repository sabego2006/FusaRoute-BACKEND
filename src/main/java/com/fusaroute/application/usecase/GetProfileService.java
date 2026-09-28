package com.fusaroute.application.usecase;

import com.fusaroute.domain.exception.UserNotFoundException;
import com.fusaroute.domain.model.User;
import com.fusaroute.domain.port.in.GetProfileUseCase;
import com.fusaroute.domain.port.out.UserRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class GetProfileService implements GetProfileUseCase {

    private final UserRepositoryPort userRepository;

    public GetProfileService(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public User execute(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
