package io.janda.vorwahlguard.data.events

import androidx.room.ColumnInfo

/** Room projection for [CallEventDao.observeTopRegions]: one region and its screened-call count. */
data class RegionCount(
    @ColumnInfo(name = "regionCode") val regionCode: String,
    val count: Int,
)

/** Room projection for [CallEventDao.observeTopRules]: one `matchedRuleId` and its call count. */
data class RuleIdCount(
    @ColumnInfo(name = "ruleId") val ruleId: String,
    val count: Int,
)
