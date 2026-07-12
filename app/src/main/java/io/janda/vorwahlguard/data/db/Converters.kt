package io.janda.vorwahlguard.data.db

import androidx.room.TypeConverter
import java.time.Instant

/**
 * Room [androidx.room.TypeConverter]s between domain types and the primitives SQLite stores.
 * Deliberately no [io.janda.vorwahlguard.domain.model.RuleAction] converter: a converter has no
 * way to *skip* a corrupt row, only to throw and kill the whole query. Enum parsing happens at
 * the entity↔domain mapping edge instead, where a bad value degrades to a dropped row.
 */
class Converters {

    @TypeConverter
    fun instantToEpochMillis(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun epochMillisToInstant(epochMillis: Long?): Instant? = epochMillis?.let(Instant::ofEpochMilli)
}
