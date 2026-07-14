package io.janda.vorwahlguard.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [MIGRATION_1_2] is the first real schema migration this app ships. `DatabaseModule` deliberately
 * does *not* configure a destructive-migration fallback (`fallbackToDestructiveMigration`) — an
 * unhandled version bump must fail loudly during development, not silently wipe every existing
 * install's `call_events` table on update. That guarantee only holds if the migration that
 * *is* declared is actually correct: a broken `MIGRATION_1_2` would crash `onCreate`/`onOpen` for
 * every user who already has a v1 database on disk, the exact "worst possible failure mode" this
 * project's screening hot path also guards against (CLAUDE.md §3, rule 3 — never let a domain bug
 * turn into a hard crash for calls that used to work). A JVM unit test cannot exercise Room's
 * generated `AutoMigrationSpec`/`Migration` wiring against a real SQLite file, so this has to run
 * as an instrumented test against an on-device (or emulator) SQLite implementation, the same
 * reasoning as [RoomInstrumentedTest] for DAO behaviour.
 *
 * v1 `call_events`: `matched_rule_id` is `NOT NULL`.
 * v2 `call_events`: `matched_rule_id` is nullable, plus a new `NOT NULL` `reason` column that
 * migrated rows are backfilled to `'RULE_MATCH'` — the only reason a v1 row could ever have
 * existed, since v1 had no other code path that wrote to `call_events`.
 */
@RunWith(AndroidJUnit4::class)
class CallEventMigration1To2Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VorwahlGuardDatabase::class.java,
    )

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getDatabasePath(TEST_DB)
            .delete()
    }

    @Test
    fun migrationFrom1To2PreservesRowsAndBackfillsReason() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO call_events
                    (id, occurred_at, number_or_hash, is_hashed, region_code, matched_rule_id, action)
                VALUES
                    ('event-1', 1700000000000, '+436631234567', 0, 'AT', 'rule-1', 'BLOCK')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT matched_rule_id, reason FROM call_events WHERE id = 'event-1'").use { cursor ->
                assertTrue("migrated row must still be present", cursor.moveToFirst())
                assertEquals("rule-1", cursor.getString(cursor.getColumnIndexOrThrow("matched_rule_id")))
                assertEquals("RULE_MATCH", cursor.getString(cursor.getColumnIndexOrThrow("reason")))
            }
        }
    }

    @Test
    fun migratedSchemaAcceptsNullMatchedRuleId() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO call_events
                    (id, occurred_at, number_or_hash, is_hashed, region_code, matched_rule_id, action)
                VALUES
                    ('event-1', 1700000000000, '+436631234567', 0, 'AT', 'rule-1', 'BLOCK')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2).use { db ->
            db.execSQL(
                """
                INSERT INTO call_events
                    (id, occurred_at, number_or_hash, is_hashed, region_code, matched_rule_id, action, reason)
                VALUES
                    ('event-2', 1700000001000, '+15551234567', 0, 'US', NULL, 'ALLOW', 'NO_MATCH')
                """.trimIndent(),
            )

            db.query("SELECT matched_rule_id FROM call_events WHERE id = 'event-2'").use { cursor ->
                assertTrue("row inserted with a null matched_rule_id must still be readable", cursor.moveToFirst())
                assertNull(cursor.getString(cursor.getColumnIndexOrThrow("matched_rule_id")))
            }
        }
    }

    private companion object {
        const val TEST_DB = "call-event-migration-1-2-test.db"
    }
}
