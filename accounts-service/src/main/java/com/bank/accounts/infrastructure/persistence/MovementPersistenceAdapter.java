package com.bank.accounts.infrastructure.persistence;

import com.bank.accounts.domain.exception.ResourceNotFoundException;
import com.bank.accounts.domain.model.Movement;
import com.bank.accounts.domain.pagination.PageQuery;
import com.bank.accounts.domain.pagination.PagedResult;
import com.bank.accounts.domain.port.MovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Implements the {@link MovementRepository} port with Spring Data JPA. */
@Component
@RequiredArgsConstructor
class MovementPersistenceAdapter implements MovementRepository {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("movementDate"), Sort.Order.desc("id"));

    private final MovementJpaRepository movements;
    private final AccountJpaRepository accounts;
    private final PersistenceMapper mapper;

    @Override
    public Movement save(Movement movement) {
        MovementEntity entity = movement.getId() == null ? newEntity(movement) : existing(movement.getId());
        mapper.copyToEntity(movement, entity);
        return mapper.toDomain(movements.save(entity));
    }

    @Override
    public Optional<Movement> findById(long id) {
        return movements.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Movement> findLatestByAccountNumber(String accountNumber) {
        return movements.findFirstByAccountAccountNumberOrderByMovementDateDescIdDesc(accountNumber)
                .map(mapper::toDomain);
    }

    @Override
    public PagedResult<Movement> findAll(String accountNumber, PageQuery query) {
        PageRequest pageable = PageRequest.of(query.page(), query.size(), NEWEST_FIRST);
        Page<MovementEntity> page = accountNumber == null
                ? movements.findAll(pageable)
                : movements.findByAccountAccountNumber(accountNumber, pageable);
        return new PagedResult<>(page.map(mapper::toDomain).getContent(), query.page(), query.size(),
                page.getTotalElements());
    }

    private MovementEntity newEntity(Movement movement) {
        MovementEntity entity = new MovementEntity();
        entity.setAccount(accounts.findByAccountNumber(movement.getAccountNumber())
                .orElseThrow(() -> ResourceNotFoundException.account(movement.getAccountNumber())));
        entity.setMovementDate(movement.getDate());
        return entity;
    }

    private MovementEntity existing(long id) {
        return movements.findById(id).orElseThrow(() -> ResourceNotFoundException.movement(id));
    }
}
