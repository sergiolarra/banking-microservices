package com.bank.clients.infrastructure.rest;

import com.bank.clients.application.usecase.CreateClientUseCase;
import com.bank.clients.application.usecase.DeleteClientUseCase;
import com.bank.clients.application.usecase.GetClientUseCase;
import com.bank.clients.application.usecase.ListClientsUseCase;
import com.bank.clients.application.usecase.UpdateClientUseCase;
import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.pagination.PageQuery;
import com.bank.clients.infrastructure.rest.dto.ClientCreateRequest;
import com.bank.clients.infrastructure.rest.dto.ClientPatchRequest;
import com.bank.clients.infrastructure.rest.dto.ClientResponse;
import com.bank.clients.infrastructure.rest.dto.ClientUpdateRequest;
import com.bank.clients.infrastructure.rest.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/** F1: CRUD of clients. Translates HTTP to use cases and nothing else (ADR-024). */
@RestController
@RequestMapping("/clientes")
@RequiredArgsConstructor
class ClientController {

    private final CreateClientUseCase createClient;
    private final GetClientUseCase getClient;
    private final ListClientsUseCase listClients;
    private final UpdateClientUseCase updateClient;
    private final DeleteClientUseCase deleteClient;
    private final ClientRestMapper mapper;

    @PostMapping
    ResponseEntity<ClientResponse> create(@Valid @RequestBody ClientCreateRequest request) {
        Client client = createClient.execute(mapper.toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{clientId}")
                .buildAndExpand(client.getClientId())
                .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(client));
    }

    @GetMapping
    PageResponse<ClientResponse> list(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page must be greater than or equal to 0") int page,
            @RequestParam(defaultValue = "" + PageQuery.DEFAULT_SIZE)
            @Min(value = 1, message = "size must be between 1 and 100")
            @Max(value = PageQuery.MAX_SIZE, message = "size must be between 1 and 100") int size) {
        return PageResponse.from(listClients.execute(new PageQuery(page, size)).map(mapper::toResponse));
    }

    @GetMapping("/{clientId}")
    ClientResponse get(@PathVariable UUID clientId) {
        return mapper.toResponse(getClient.execute(clientId));
    }

    @PutMapping("/{clientId}")
    ClientResponse replace(@PathVariable UUID clientId, @Valid @RequestBody ClientUpdateRequest request) {
        return mapper.toResponse(updateClient.execute(clientId, mapper.toChanges(request)));
    }

    @PatchMapping("/{clientId}")
    ClientResponse patch(@PathVariable UUID clientId, @Valid @RequestBody ClientPatchRequest request) {
        return mapper.toResponse(updateClient.execute(clientId, mapper.toChanges(request)));
    }

    @DeleteMapping("/{clientId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID clientId) {
        deleteClient.execute(clientId);
    }
}
