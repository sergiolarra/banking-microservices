package com.bank.clients.infrastructure.rest;

import com.bank.clients.application.command.ClientChanges;
import com.bank.clients.application.command.CreateClientCommand;
import com.bank.clients.domain.model.Client;
import com.bank.clients.domain.model.PersonalDetails;
import com.bank.clients.infrastructure.rest.dto.ClientCreateRequest;
import com.bank.clients.infrastructure.rest.dto.ClientPatchRequest;
import com.bank.clients.infrastructure.rest.dto.ClientResponse;
import com.bank.clients.infrastructure.rest.dto.ClientUpdateRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Translates between HTTP DTOs and application commands / domain objects. */
@Mapper
interface ClientRestMapper {

    @Mapping(target = "details", source = ".")
    @Mapping(target = "rawPassword", source = "password")
    CreateClientCommand toCommand(ClientCreateRequest request);

    PersonalDetails toDetails(ClientCreateRequest request);

    @Mapping(target = "rawPassword", source = "password")
    ClientChanges toChanges(ClientUpdateRequest request);

    @Mapping(target = "rawPassword", source = "password")
    ClientChanges toChanges(ClientPatchRequest request);

    ClientResponse toResponse(Client client);
}
