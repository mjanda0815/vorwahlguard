package io.janda.vorwahlguard.data.events

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Room row for one recorded screening result. No foreign key to `rules` — events must outlive
 * rule deletion (CLAUDE.md §12 statistics survive a rule being edited/removed). `isHashed`
 * records whether [numberOrHash] is the raw E.164 number or a `sha256(number + salt)` hash
 * (CLAUDE.md §12, decided by [io.janda.vorwahlguard.data.settings.SettingsStore] at record time,
 * not carried by the domain [io.janda.vorwahlguard.domain.model.CallEvent]).
 */
@Entity(
    tableName = "call_events",
    indices = [Index(value = ["occurred_at"])],
)
data class CallEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Instant,
    @ColumnInfo(name = "number_or_hash") val numberOrHash: String,
    @ColumnInfo(name = "is_hashed") val isHashed: Boolean,
    @ColumnInfo(name = "region_code") val regionCode: String,
    @ColumnInfo(name = "matched_rule_id") val matchedRuleId: String,
    /** [io.janda.vorwahlguard.domain.model.RuleAction] name; parsed leniently on the read side. */
    val action: String,
)
