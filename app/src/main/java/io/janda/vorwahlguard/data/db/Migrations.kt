package io.janda.vorwahlguard.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Issue #59 / ADR 0014: `call_events.matched_rule_id` becomes nullable (a logged allowed call has
 * no rule id) and a new `reason` column is added. SQLite cannot relax a `NOT NULL` constraint in
 * place, so this recreates the table — existing rows backfill `reason = 'RULE_MATCH'`, which is
 * true for every row written before this migration (only rule matches were ever recorded).
 *
 * The recreated index name must match Room's expected `index_call_events_occurred_at` exactly, or
 * Room's schema validation fails at open time.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `call_events_new` (
              `id` TEXT NOT NULL, `occurred_at` INTEGER NOT NULL, `number_or_hash` TEXT NOT NULL,
              `is_hashed` INTEGER NOT NULL, `region_code` TEXT NOT NULL, `matched_rule_id` TEXT,
              `action` TEXT NOT NULL, `reason` TEXT NOT NULL, PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `call_events_new` (`id`,`occurred_at`,`number_or_hash`,`is_hashed`,`region_code`,`matched_rule_id`,`action`,`reason`)
              SELECT `id`,`occurred_at`,`number_or_hash`,`is_hashed`,`region_code`,`matched_rule_id`,`action`,'RULE_MATCH' FROM `call_events`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `call_events`")
        db.execSQL("ALTER TABLE `call_events_new` RENAME TO `call_events`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_events_occurred_at` ON `call_events` (`occurred_at`)")
    }
}
