package com.bank.clients.domain.model;

import com.bank.clients.domain.exception.BusinessRuleException;
import com.bank.clients.domain.exception.ErrorCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F5-AC1: unit tests of the Client domain entity, covering CLI-1 to CLI-5 (SPEC §4.2). */
class ClientTest {

    private static final String PASSWORD_HASH = "$2a$10$hashedPasswordValue";

    private static PersonalDetails detailsWithAge(int age) {
        return new PersonalDetails("Jose Lema", Gender.MALE, age, "1712345678", "Otavalo sn y principal", "098254785");
    }

    private static Client newClient() {
        return Client.create(detailsWithAge(35), PASSWORD_HASH, true);
    }

    @Nested
    @DisplayName("CLI-1 a client must be an adult")
    class Adulthood {

        @Test
        void rejectsAnUnderageClient() {
            assertThatThrownBy(() -> Client.create(detailsWithAge(17), PASSWORD_HASH, true))
                    .isInstanceOfSatisfying(BusinessRuleException.class, error -> {
                        assertThat(error).hasMessage("El cliente debe ser mayor de edad");
                        assertThat(error.code()).isEqualTo("BUSINESS_RULE_VIOLATION");
                        assertThat(error.category()).isEqualTo(ErrorCategory.BUSINESS_RULE);
                        assertThat(error.title()).isEqualTo("Regla de negocio incumplida");
                    });
        }

        @Test
        void acceptsAClientWhoIsExactlyEighteen() {
            assertThat(Client.create(detailsWithAge(Client.MINIMUM_AGE), PASSWORD_HASH, true).getAge())
                    .isEqualTo(18);
        }

        @Test
        void stillAppliesThePersonAgeRangeAboveTheMinimum() {
            assertThatThrownBy(() -> Client.create(detailsWithAge(121), PASSWORD_HASH, true))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("La edad debe estar entre 0 y 120");
        }

        @Test
        void rejectsMissingDetails() {
            assertThatThrownBy(() -> Client.create(null, PASSWORD_HASH, true))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Los datos personales son obligatorios");
        }
    }

    @Nested
    @DisplayName("CLI-2 create generates the clientId and defaults to active")
    class Creation {

        @Test
        void generatesAClientIdAndKeepsTheGivenData() {
            Client client = newClient();

            assertThat(client.getClientId()).isNotNull();
            assertThat(client.getId()).isNull();
            assertThat(client.getVersion()).isNull();
            assertThat(client.getName()).isEqualTo("Jose Lema");
            assertThat(client.getPasswordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(client.isActive()).isTrue();
        }

        @Test
        void generatesADifferentClientIdForEachClient() {
            assertThat(newClient().getClientId()).isNotEqualTo(newClient().getClientId());
        }

        @Test
        void isActiveWhenNoStateIsGiven() {
            assertThat(Client.create(detailsWithAge(35), PASSWORD_HASH, null).isActive()).isTrue();
        }

        @Test
        void keepsAnExplicitInactiveState() {
            assertThat(Client.create(detailsWithAge(35), PASSWORD_HASH, false).isActive()).isFalse();
        }

        @Test
        void restoresAnExistingClientAsItWasStored() {
            UUID clientId = UUID.randomUUID();

            Client client = Client.restore(7L, clientId, 3L, detailsWithAge(40), PASSWORD_HASH, false);

            assertThat(client.getId()).isEqualTo(7L);
            assertThat(client.getClientId()).isEqualTo(clientId);
            assertThat(client.getVersion()).isEqualTo(3L);
            assertThat(client.isActive()).isFalse();
        }

        @Test
        void requiresAClientIdWhenRestoring() {
            assertThatThrownBy(() -> Client.restore(7L, null, 3L, detailsWithAge(40), PASSWORD_HASH, true))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("CLI-3 activate and deactivate are idempotent")
    class ActiveState {

        @Test
        void deactivatesAnActiveClient() {
            Client client = newClient();

            client.deactivate();
            client.deactivate();

            assertThat(client.isActive()).isFalse();
        }

        @Test
        void activatesAnInactiveClient() {
            Client client = Client.create(detailsWithAge(35), PASSWORD_HASH, false);

            client.activate();
            client.activate();

            assertThat(client.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("CLI-4 changePassword rejects an empty hash")
    class PasswordChange {

        @Test
        void replacesTheHash() {
            Client client = newClient();

            client.changePassword("$2a$10$anotherHash");

            assertThat(client.getPasswordHash()).isEqualTo("$2a$10$anotherHash");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void rejectsANullOrBlankHash(String hash) {
            Client client = newClient();

            assertThatThrownBy(() -> client.changePassword(hash))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("La contraseña es obligatoria");
            assertThat(client.getPasswordHash()).isEqualTo(PASSWORD_HASH);
        }

        @Test
        void requiresAHashOnCreation() {
            assertThatThrownBy(() -> Client.create(detailsWithAge(35), " ", true))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("La contraseña es obligatoria");
        }
    }

    @Nested
    @DisplayName("CLI-5 updateDetails validates like create and keeps the clientId")
    class DetailsUpdate {

        @Test
        void appliesNewDetailsAndKeepsTheClientId() {
            Client client = newClient();
            UUID clientId = client.getClientId();
            PersonalDetails newDetails =
                    new PersonalDetails("Marianela Montalvo", Gender.FEMALE, 30, "0912345678", "Amazonas y NNUU", "097548965");

            client.updateDetails(newDetails);

            assertThat(client.getDetails()).isEqualTo(newDetails);
            assertThat(client.getClientId()).isEqualTo(clientId);
        }

        @Test
        void rejectsAnUnderageUpdateAndKeepsThePreviousDetails() {
            Client client = newClient();
            PersonalDetails before = client.getDetails();

            assertThatThrownBy(() -> client.updateDetails(detailsWithAge(16)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("El cliente debe ser mayor de edad");
            assertThat(client.getDetails()).isEqualTo(before);
        }

        @Test
        void rejectsAnInvalidUpdateAndKeepsThePreviousDetails() {
            Client client = newClient();
            PersonalDetails before = client.getDetails();
            PersonalDetails invalidPhone =
                    new PersonalDetails("Jose Lema", Gender.MALE, 35, "1712345678", "Otavalo sn y principal", "abc");

            assertThatThrownBy(() -> client.updateDetails(invalidPhone)).isInstanceOf(BusinessRuleException.class);
            assertThat(client.getDetails()).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("identity is the clientId")
    class Identity {

        @Test
        void clientsWithTheSameClientIdAreEqual() {
            UUID clientId = UUID.randomUUID();
            Client stored = Client.restore(1L, clientId, 0L, detailsWithAge(35), PASSWORD_HASH, true);
            Client reloaded = Client.restore(1L, clientId, 1L, detailsWithAge(50), "$2a$10$other", false);

            assertThat(stored).isEqualTo(reloaded).hasSameHashCodeAs(reloaded);
        }

        @Test
        void clientsWithDifferentClientIdsAreNotEqual() {
            Client client = newClient();

            assertThat(client).isEqualTo(client).isNotEqualTo(newClient()).isNotEqualTo("not a client");
        }

        @Test
        void toStringDoesNotExposeThePasswordHash() {
            Client client = newClient();

            assertThat(client.toString()).contains(client.getClientId().toString()).doesNotContain(PASSWORD_HASH);
        }
    }
}
