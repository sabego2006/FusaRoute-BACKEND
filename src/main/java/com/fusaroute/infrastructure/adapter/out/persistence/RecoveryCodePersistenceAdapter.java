package com.fusaroute.infrastructure.adapter.out.persistence;

import com.fusaroute.domain.model.RecoveryCode;
import com.fusaroute.domain.port.out.RecoveryCodeRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Implementacion del puerto de respaldos TOTP sobre Spring Data (SCRUM-179). */
@Component
public class RecoveryCodePersistenceAdapter implements RecoveryCodeRepositoryPort {

    private final SpringDataUserRecoveryCodeRepository repository;

    public RecoveryCodePersistenceAdapter(SpringDataUserRecoveryCodeRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void replaceAllForUser(Long userId, List<RecoveryCode> newCodes) {
        repository.deleteAllByUserId(userId);
        List<UserRecoveryCodeJpaEntity> toSave = new ArrayList<>(newCodes.size());
        for (RecoveryCode code : newCodes) {
            toSave.add(new UserRecoveryCodeJpaEntity(null, userId, code.getCodeHash(), code.isUsed()));
        }
        repository.saveAll(toSave);
    }

    @Override
    public List<RecoveryCode> findActiveByUser(Long userId) {
        List<UserRecoveryCodeJpaEntity> rows = repository.findByUserIdAndUsedFalse(userId);
        List<RecoveryCode> domain = new ArrayList<>(rows.size());
        for (UserRecoveryCodeJpaEntity row : rows) {
            domain.add(new RecoveryCode(row.getId(), row.getUserId(), row.getCodeHash(), row.isUsed()));
        }
        return domain;
    }

    @Override
    @Transactional
    public void markUsed(RecoveryCode code) {
        // Se actualiza SIN cargar la fila entera: el id es suficiente y asi la
        // transaccion no choca con un dirty check de otro lado.
        repository.findById(code.getId()).ifPresent(entity -> {
            entity.markUsed();
            repository.save(entity);
        });
    }

    @Override
    @Transactional
    public void deleteAllForUser(Long userId) {
        repository.deleteAllByUserId(userId);
    }
}
