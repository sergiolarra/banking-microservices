package com.bank.accounts.application.usecase;

import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.Movement;
import com.bank.accounts.domain.port.MovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F1: reads one movement. */
@Service
public class GetMovementUseCase {

    private final MovementRepository movements;

    public GetMovementUseCase(MovementRepository movements) {
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public Movement execute(long id) {
        return movements.findById(id).orElseThrow(() -> ResourceNotFoundException.movement(id));
    }
}
