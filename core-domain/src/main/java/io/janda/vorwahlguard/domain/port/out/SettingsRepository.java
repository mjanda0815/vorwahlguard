package io.janda.vorwahlguard.domain.port.out;

import io.janda.vorwahlguard.domain.model.Settings;

/** Current {@link Settings} snapshot, cached by the adapter the same way rules are (PROJECT.md §4). */
public interface SettingsRepository {

    Settings current();
}
