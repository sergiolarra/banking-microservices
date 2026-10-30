package com.bank.accounts.domain.model;

import com.bank.accounts.domain.exception.ClientUnavailableException;
import com.bank.accounts.domain.exception.ErrorCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Replica rules: newer versions win, older or repeated ones are ignored (ADR-010). */
class ClientSnapshotTest {

    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-10-30T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-30T11:00:00Z");

    private static ClientSnapshot snapshotAtVersion(long version) {
        return ClientSnapshot.create(CLIENT_ID, "Jose Lema", "1712345678", true, version, T0);
    }

    @Test
    void anActiveClientCanOperate() {
        ClientSnapshot snapshot = snapshotAtVersion(0);

        assertThat(snapshot.canOperate()).isTrue();
        assertThat(snapshot.isDeleted()).isFalse();
        assertThatNoException().isThrownBy(snapshot::ensureCanOperate);
    }

    @Test
    void appliesANewerUpdate() {
        ClientSnapshot snapshot = snapshotAtVersion(1);

        boolean applied = snapshot.update("Jose A. Lema", "0912345678", false, 2, T1);

        assertThat(applied).isTrue();
        assertThat(snapshot.getName()).isEqualTo("Jose A. Lema");
        assertThat(snapshot.getIdentification()).isEqualTo("0912345678");
        assertThat(snapshot.isActive()).isFalse();
        assertThat(snapshot.getVersion()).isEqualTo(2);
        assertThat(snapshot.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void ignoresARepeatedOrOlderUpdate() {
        ClientSnapshot snapshot = snapshotAtVersion(2);

        assertThat(snapshot.update("Old name", "1712345678", false, 2, T1)).isFalse();
        assertThat(snapshot.update("Older name", "1712345678", false, 1, T1)).isFalse();
        assertThat(snapshot.getName()).isEqualTo("Jose Lema");
        assertThat(snapshot.getVersion()).isEqualTo(2);
        assertThat(snapshot.getUpdatedAt()).isEqualTo(T0);
    }

    @Test
    void aDeletedClientKeepsItsDataButCannotOperate() {
        ClientSnapshot snapshot = snapshotAtVersion(2);

        assertThat(snapshot.markDeleted(3, T1)).isTrue();

        assertThat(snapshot.isDeleted()).isTrue();
        assertThat(snapshot.isActive()).isFalse();
        assertThat(snapshot.getName()).isEqualTo("Jose Lema");
        assertThat(snapshot.canOperate()).isFalse();
        assertThatThrownBy(snapshot::ensureCanOperate)
                .isInstanceOfSatisfying(ClientUnavailableException.class, error -> {
                    assertThat(error.code()).isEqualTo("CLIENT_UNAVAILABLE");
                    assertThat(error.category()).isEqualTo(ErrorCategory.BUSINESS_RULE);
                    assertThat(error.title()).isEqualTo("Cliente no disponible");
                    assertThat(error).hasMessageContaining(CLIENT_ID.toString());
                });
    }

    @Test
    void ignoresAnOutdatedDeletion() {
        ClientSnapshot snapshot = snapshotAtVersion(5);

        assertThat(snapshot.markDeleted(5, T1)).isFalse();
        assertThat(snapshot.isDeleted()).isFalse();
    }

    @Test
    void anInactiveClientCannotOperate() {
        ClientSnapshot snapshot = ClientSnapshot.restore(CLIENT_ID, "Jose Lema", "1712345678", false, false, 1, T0);

        assertThat(snapshot.canOperate()).isFalse();
        assertThat(snapshot.getClientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    void requiresAClientId() {
        assertThatThrownBy(() -> ClientSnapshot.create(null, "Jose Lema", "1712345678", true, 0, T0))
                .isInstanceOf(NullPointerException.class);
    }
}
