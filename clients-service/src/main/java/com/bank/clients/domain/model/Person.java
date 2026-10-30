package com.bank.clients.domain.model;

import com.bank.clients.domain.exception.BusinessRuleException;

import java.util.regex.Pattern;

/**
 * A person as defined in SPEC §4.1.
 *
 * <p>Invariants are checked every time details are applied, and there are no public
 * mutators, so a subclass can add stricter rules without breaking the contract of
 * this class (ADR-024, Liskov).
 */
public abstract class Person {

    static final int NAME_MIN_LENGTH = 2;
    static final int NAME_MAX_LENGTH = 100;
    static final int AGE_MIN = 0;
    static final int AGE_MAX = 120;
    static final int ADDRESS_MIN_LENGTH = 5;
    static final int ADDRESS_MAX_LENGTH = 200;

    private static final Pattern IDENTIFICATION_PATTERN = Pattern.compile("^[A-Za-z0-9]{5,20}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{7,15}$");

    private final Long id;
    private String name;
    private Gender gender;
    private int age;
    private String identification;
    private String address;
    private String phone;

    protected Person(Long id, PersonalDetails details) {
        this.id = id;
        applyDetails(details);
    }

    /**
     * Validates and applies the given details. Text values are trimmed before
     * validation, so stored values never have leading or trailing spaces.
     */
    protected final void applyDetails(PersonalDetails details) {
        if (details == null) {
            throw new BusinessRuleException("Personal details are required");
        }
        String validName = requireLength(details.name(), NAME_MIN_LENGTH, NAME_MAX_LENGTH, "Name");
        Gender validGender = requireGender(details.gender());
        int validAge = requireAge(details.age());
        String validIdentification = requirePattern(details.identification(), IDENTIFICATION_PATTERN,
                "Identification must be 5 to 20 alphanumeric characters");
        String validAddress = requireLength(details.address(), ADDRESS_MIN_LENGTH, ADDRESS_MAX_LENGTH, "Address");
        String validPhone = requirePattern(details.phone(), PHONE_PATTERN,
                "Phone must have 7 to 15 digits, optionally preceded by +");

        this.name = validName;
        this.gender = validGender;
        this.age = validAge;
        this.identification = validIdentification;
        this.address = validAddress;
        this.phone = validPhone;
    }

    private static String requireLength(String value, int min, int max, String field) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < min || trimmed.length() > max) {
            throw new BusinessRuleException("%s must be between %d and %d characters long".formatted(field, min, max));
        }
        return trimmed;
    }

    private static String requirePattern(String value, Pattern pattern, String message) {
        String trimmed = value == null ? "" : value.trim();
        if (!pattern.matcher(trimmed).matches()) {
            throw new BusinessRuleException(message);
        }
        return trimmed;
    }

    private static Gender requireGender(Gender gender) {
        if (gender == null) {
            throw new BusinessRuleException("Gender is required");
        }
        return gender;
    }

    private static int requireAge(Integer age) {
        if (age == null || age < AGE_MIN || age > AGE_MAX) {
            throw new BusinessRuleException("Age must be between %d and %d".formatted(AGE_MIN, AGE_MAX));
        }
        return age;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Gender getGender() {
        return gender;
    }

    public int getAge() {
        return age;
    }

    public String getIdentification() {
        return identification;
    }

    public String getAddress() {
        return address;
    }

    public String getPhone() {
        return phone;
    }

    /** Returns the current details, e.g. to apply a partial update on top of them. */
    public PersonalDetails getDetails() {
        return new PersonalDetails(name, gender, age, identification, address, phone);
    }
}
