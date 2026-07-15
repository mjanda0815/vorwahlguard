package io.janda.vorwahlguard.ui.einstellungen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.janda.vorwahlguard.R

/** The retention presets offered by [RetentionRow] (PROJECT.md §7 / CLAUDE.md §12). */
private val RETENTION_PRESETS_DAYS = listOf(30, 90, 180, 365)

/**
 * Stateless Einstellungen body (issue #29): role status, contacts-bypass toggle, retention
 * preset picker, pseudonymisation and notify-on-block toggles, and the About section — mirrors
 * `RuleList.kt`/`UebersichtContent.kt`'s private-sub-composable style. Laid out in a scrollable
 * [Column], matching `AddRuleSheet`'s scroll idiom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EinstellungenContent(
    state: EinstellungenUiState,
    onRequestRole: () -> Unit,
    onContactsBypassToggled: (Boolean) -> Unit,
    onPseudonymiseToggled: (Boolean) -> Unit,
    onNotifyToggled: (Boolean) -> Unit,
    onRetentionSelected: (Int) -> Unit,
    onLogAllowedToggled: (Boolean) -> Unit,
    onOpenRepo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.loaded) {
        Column(modifier = modifier) {}
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        RoleStatusRow(
            roleAvailable = state.roleAvailable,
            roleHeld = state.roleHeld,
            onRequestRole = onRequestRole,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        ContactsBypassRow(
            checked = state.contactsBypassEnabled,
            permissionDenied = state.contactsPermissionDenied,
            onCheckedChange = onContactsBypassToggled,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        RetentionRow(
            selectedDays = state.retentionDays,
            onRetentionSelected = onRetentionSelected,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        LogAllowedCallsRow(
            checked = state.logAllowedCalls,
            onCheckedChange = onLogAllowedToggled,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        PseudonymiseRow(
            checked = state.pseudonymiseNumbers,
            onCheckedChange = onPseudonymiseToggled,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        NotifyRow(
            checked = state.notifyOnBlock,
            onCheckedChange = onNotifyToggled,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        AboutSection(appVersion = state.appVersion, copyrightYear = state.copyrightYear, onOpenRepo = onOpenRepo)
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = modifier,
    )
}

@Composable
private fun RoleStatusRow(
    roleAvailable: Boolean,
    roleHeld: Boolean,
    onRequestRole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.settings_role_section_title))
        Text(
            text = stringResource(
                if (roleHeld) R.string.settings_role_status_held else R.string.settings_role_status_not_held,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        // Only offer the button where the role can actually be requested — same gating the
        // Übersicht card uses (roleAvailable && !roleHeld). Otherwise createRoleRequestIntent()
        // returns null and the button would silently no-op.
        if (roleAvailable && !roleHeld) {
            Button(onClick = onRequestRole, modifier = Modifier.padding(top = 12.dp)) {
                // Reuses the identical Übersicht role-warning copy (CLAUDE.md §5's action-neutral
                // wording) rather than duplicating a second string with the same text.
                Text(stringResource(R.string.overview_role_warning_button))
            }
        }
    }
}

@Composable
private fun ContactsBypassRow(
    checked: Boolean,
    permissionDenied: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionTitle(stringResource(R.string.settings_contacts_bypass_title))
                Text(
                    text = stringResource(R.string.settings_contacts_bypass_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (permissionDenied) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(
                    text = stringResource(R.string.settings_contacts_permission_denied),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RetentionRow(
    selectedDays: Int,
    onRetentionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.settings_retention_title))
        Text(
            text = stringResource(R.string.settings_retention_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = pluralStringResource(R.plurals.settings_retention_days, selectedDays, selectedDays),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                RETENTION_PRESETS_DAYS.forEach { days ->
                    DropdownMenuItem(
                        text = { Text(pluralStringResource(R.plurals.settings_retention_days, days, days)) },
                        onClick = {
                            expanded = false
                            onRetentionSelected(days)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LogAllowedCallsRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    ToggleRow(
        title = stringResource(R.string.settings_log_allowed_title),
        body = stringResource(R.string.settings_log_allowed_body),
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
    )
}

@Composable
private fun PseudonymiseRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    ToggleRow(
        title = stringResource(R.string.settings_pseudonymise_title),
        body = stringResource(R.string.settings_pseudonymise_body),
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
    )
}

@Composable
private fun NotifyRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    ToggleRow(
        title = stringResource(R.string.settings_notify_title),
        body = stringResource(R.string.settings_notify_body),
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SectionTitle(title)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AboutSection(
    appVersion: String,
    copyrightYear: Int,
    onOpenRepo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.settings_about_section_title))
        Text(
            text = stringResource(R.string.settings_about_version, appVersion),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.settings_about_license),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(onClick = onOpenRepo, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.settings_about_repo))
        }
        Text(
            text = stringResource(R.string.settings_about_copyright, copyrightYear),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
