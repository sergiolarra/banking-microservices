package com.bank.clients.domain.model;

import com.bank.clients.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariants of {@link Person} (SPEC §4.1), checked through a minimal subclass. */
class PersonTest {

    private static final class TestPerson extends Person {
        TestPerson(PersonalDetails details) {
            super(1L, details);
        }
    }

    private static PersonalDetails valid() {
        return new PersonalDetails("Jose Lema", Gender.MALE, 35, "1712345678", "Otavalo sn y principal", "098254785");
    }

    @Test
    void keepsValidDetails() {
        Person person = new TestPerson(valid());

        assertThat(person.getId()).isEqualTo(1L);
        assertThat(person.getDetails()).isEqualTo(valid());
        assertThat(person.getGender()).isEqualTo(Gender.MALE);
        assertThat(person.getIdentification()).isEqualTo("1712345678");
        assertThat(person.getAddress()).isEqualTo("Otavalo sn y principal");
        assertThat(person.getPhone()).isEqualTo("098254785");
    }

    @Test
    void trimsTextValues() {
        Person person = new TestPerson(
                new PersonalDetails("  Jose Lema ", Gender.MALE, 35, " 1712345678 ", " Otavalo sn ", " +593982547850 "));

        assertThat(person.getName()).isEqualTo("Jose Lema");
        assertThat(person.getIdentification()).isEqualTo("1712345678");
        assertThat(person.getAddress()).isEqualTo("Otavalo sn");
        assertThat(person.getPhone()).isEqualTo("+593982547850");
    }

    @Test
    void acceptsTheBoundaryValues() {
        Person person = new TestPerson(new PersonalDetails(
                "Jo", Gender.OTHER, 0, "A1B2C", "Calle", "1234567"));

        assertThat(person.getAge()).isZero();
        assertThat(new TestPerson(new PersonalDetails(
                "x".repeat(100), Gender.FEMALE, 120, "A".repeat(20), "x".repeat(200), "+" + "1".repeat(15))).getAge())
                .isEqualTo(120);
    }

    /** A Person may be a minor; only Client adds the adulthood rule (ADR-024, Liskov). */
    @Test
    void allowsMinorsBecauseAdulthoodIsAClientRule() {
        Person person = new TestPerson(new PersonalDetails(
                "Ana Lema", Gender.FEMALE, 10, "1712345679", "Otavalo sn y principal", "098254786"));

        assertThat(person.getAge()).isEqualTo(10);
    }

    static Stream<Arguments> invalidDetails() {
        PersonalDetails v = valid();
        return Stream.of(
                Arguments.of(new PersonalDetails(null, v.gender(), v.age(), v.identification(), v.address(), v.phone()),
                        "El nombre debe tener entre 2 y 100 caracteres"),
                Arguments.of(new PersonalDetails(" J ", v.gender(), v.age(), v.identification(), v.address(), v.phone()),
                        "El nombre debe tener entre 2 y 100 caracteres"),
                Arguments.of(new PersonalDetails("x".repeat(101), v.gender(), v.age(), v.identification(), v.address(), v.phone()),
                        "El nombre debe tener entre 2 y 100 caracteres"),
                Arguments.of(new PersonalDetails(v.name(), null, v.age(), v.identification(), v.address(), v.phone()),
                        "El género es obligatorio"),
                Arguments.of(new PersonalDetails(v.name(), v.gender(), null, v.identification(), v.address(), v.phone()),
                        "La edad debe estar entre 0 y 120"),
                Arguments.of(new PersonalDetails(v.name(), v.gender(), -1, v.identification(), v.address(), v.phone()),
                        "La edad debe estar entre 0 y 120"),
                Arguments.of(new PersonalDetails(v.name(), v.gender(), 121, v.identification(), v.address(), v.phone()),
                        "La edad debe estar entre 0 y 120"),
                Arguments.of(new PersonalDetails(v.name(), v.gender(), v.age(), null, v.address(), v.phone()),
                        "La identificación debe tener entre 5 y 20 caracteres alfanuméricos"),
                Arguments.of(new PersonalDetails(v.name(), v.gender(), v.age(), "1234", v.address(), v.phone()),
                        "La identificación debe tener entre 5 y 20 caracteres alfanuméricos"),
                Arguments.of(new PersonalDetails(v.name(), v.gender(), v.age(), "17123-45678", v.address(), v.phone()),
                        "La identificación debe tener entre 5 y 20 caracteres alfanuméricos"),
                Arguments.of(new PersonalDetails(v.name(), v.gender(), v.age(), v.identification(), "Av 1", v.phone()),
                        "La dirección debe tener entre 5 y 200 caracteres"),
                Arguments.of(new PersonalDetails(v.name(), v.gender(), v.age(), v.identification(), "x".repeat(201), v.phone()),
                        "La dirección debe tener entre 5 y 200 caracteres"),
                Arguments.of(new PersonalDetails(v.name(), v.gender(), v.age(), v.identification(), v.address(), null),
                        "El teléfono debe tener entre 7 y 15 dígitos, opcionalmente precedido de +"),
                Arguments.of(new PersonalDetails(v.name(), v.gender(), v.age(), v.identification(), v.address(), "123456"),
                        "El teléfono debe tener entre 7 y 15 dígitos, opcionalmente precedido de +"),
                Arguments.of(new PersonalDetails(v.name(), v.gender(), v.age(), v.identification(), v.address(), "09-825-4785"),
                        "El teléfono debe tener entre 7 y 15 dígitos, opcionalmente precedido de +"));
    }

    @ParameterizedTest
    @MethodSource("invalidDetails")
    void rejectsInvalidDetails(PersonalDetails details, String expectedMessage) {
        assertThatThrownBy(() -> new TestPerson(details))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(expectedMessage);
    }
}
