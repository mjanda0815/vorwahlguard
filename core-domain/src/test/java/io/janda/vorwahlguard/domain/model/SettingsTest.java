package io.janda.vorwahlguard.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SettingsTest {

    @Test
    void isPlainImmutableData() {
        Settings settings = new Settings(true, 90, false, true);

        assertThat(settings.contactsBypassEnabled()).isTrue();
        assertThat(settings.retentionDays()).isEqualTo(90);
        assertThat(settings.pseudonymiseNumbers()).isFalse();
        assertThat(settings.notifyOnBlock()).isTrue();
    }
}
