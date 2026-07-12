package io.janda.vorwahlguard.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.janda.vorwahlguard.data.events.CallEventDao
import io.janda.vorwahlguard.data.events.CallEventEntity
import io.janda.vorwahlguard.data.rules.RuleDao
import io.janda.vorwahlguard.data.rules.RuleEntity

@Database(
    entities = [RuleEntity::class, CallEventEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class VorwahlGuardDatabase : RoomDatabase() {

    abstract fun ruleDao(): RuleDao

    abstract fun callEventDao(): CallEventDao
}
