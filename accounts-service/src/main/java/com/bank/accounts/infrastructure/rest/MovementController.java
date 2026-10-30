package com.bank.accounts.infrastructure.rest;

import com.bank.accounts.application.usecase.CorrectMovementUseCase;
import com.bank.accounts.application.usecase.GetMovementUseCase;
import com.bank.accounts.application.usecase.ListMovementsUseCase;
import com.bank.accounts.application.usecase.RegisterMovementUseCase;
import com.bank.accounts.domain.model.Movement;
import com.bank.accounts.domain.pagination.PageQuery;
import com.bank.accounts.infrastructure.rest.dto.MovementCorrectionRequest;
import com.bank.accounts.infrastructure.rest.dto.MovementCreateRequest;
import com.bank.accounts.infrastructure.rest.dto.MovementResponse;
import com.bank.accounts.infrastructure.rest.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/** F1/F2/F3: register, read and correct movements. No PATCH or DELETE (ADR-012, F1-AC8). */
@RestController
@RequestMapping("/movements")
@RequiredArgsConstructor
class MovementController {

    private final RegisterMovementUseCase registerMovement;
    private final GetMovementUseCase getMovement;
    private final ListMovementsUseCase listMovements;
    private final CorrectMovementUseCase correctMovement;
    private final AccountRestMapper mapper;

    @PostMapping
    ResponseEntity<MovementResponse> register(@Valid @RequestBody MovementCreateRequest request) {
        Movement movement = registerMovement.execute(request.accountNumber(), request.amount());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(movement.getId())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(movement));
    }

    @GetMapping
    PageResponse<MovementResponse> list(
            @RequestParam(required = false) String accountNumber,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be greater than or equal to 0") int page,
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE)
            @Min(value = 1, message = "size must be between 1 and 100")
            @Max(value = PageQuery.MAX_SIZE, message = "size must be between 1 and 100") int size) {
        return PageResponse.from(listMovements.execute(accountNumber, new PageQuery(page, size))
                .map(mapper::toResponse));
    }

    @GetMapping("/{id}")
    MovementResponse get(@PathVariable long id) {
        return mapper.toResponse(getMovement.execute(id));
    }

    @PutMapping("/{id}")
    MovementResponse correct(@PathVariable long id, @Valid @RequestBody MovementCorrectionRequest request) {
        return mapper.toResponse(correctMovement.execute(id, request.amount()));
    }
}
