package io.janda.vorwahlguard.data.rules

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.janda.vorwahlguard.domain.model.PatternSyntax
import io.janda.vorwahlguard.domain.model.Rule
import io.janda.vorwahlguard.domain.model.RuleAction
import java.time.Instant

/**
 * Room row for a [Rule]. `pattern` is the canonical [io.janda.vorwahlguard.domain.model.Pattern]
 * text (CLAUDE.md §4) — Room stores it as plain text and [toDomain] re-validates it through
 * [PatternSyntax] on the way back out, since the grammar lives in `:core-domain`, not here.
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey val id: String,
    val pattern: String,
    val action: String,
    val enabled: Boolean,
    val label: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
) {

    /**
     * Parses [pattern] and [action] back into a [Rule]. Returns `null` if either stored text is
     * no longer valid — a dropped rule degrades toward "no match" (allow), never a thrown
     * exception on the warm path (CLAUDE.md §3 rule 1).
     */
    fun toDomain(): Rule? {
        if (!PatternSyntax.isValid(pattern)) {
            return null
        }
        val parsedAction = runCatching { RuleAction.valueOf(action) }.getOrNull() ?: return null
        // core-domain is not compiled with -parameters, so named arguments are not available
        // here; order matches Rule's canonical constructor: id, pattern, action, enabled,
        // label, createdAt.
        return Rule(
            id,
            PatternSyntax.parse(pattern),
            parsedAction,
            enabled,
            label,
            createdAt,
        )
    }
}

fun Rule.toEntity(): RuleEntity = RuleEntity(
    id = id(),
    pattern = pattern().text(),
    action = action().name,
    enabled = enabled(),
    label = label(),
    createdAt = createdAt(),
)
