package io.janda.vorwahlguard.ui.uebersicht

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.janda.vorwahlguard.R
import io.janda.vorwahlguard.ui.regeln.RuleLabel
import io.janda.vorwahlguard.ui.regeln.RuleRowUi
import io.janda.vorwahlguard.ui.regeln.addrule.labelRes

/**
 * Stateless Übersicht dashboard body (issue #27): a role-status warning (shown only while the
 * role is available but not held), the hero counter, a 30-day sparkline, and the top-countries/
 * top-rules lists — mirrors [io.janda.vorwahlguard.ui.protokoll.ProtokollListContent]'s private-
 * sub-composable style. Laid out in a scrollable [Column] since the combined content can exceed
 * one screen, matching `AddRuleSheet`'s scroll idiom.
 */
@Composable
fun UebersichtContent(
    state: UebersichtUiState,
    onRequestRole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.loaded) {
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (state.roleAvailable && !state.roleHeld) {
            RoleWarningCard(onRequestRole, modifier = Modifier.padding(bottom = 16.dp))
        }

        if (!state.hasAnyEvents) {
            EmptyDashboard()
            return@Column
        }

        HeroCounter(state.totalScreened, modifier = Modifier.padding(bottom = 16.dp))
        SparklineChart(state.sparkline, modifier = Modifier.padding(bottom = 16.dp))
        BreakdownSection(state.actionBreakdown, modifier = Modifier.padding(bottom = 16.dp))
        TopCountriesSection(state.topCountries, modifier = Modifier.padding(bottom = 16.dp))
        TopRulesSection(state.topRules)
    }
}

@Composable
private fun RoleWarningCard(onRequestRole: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.overview_role_warning_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.overview_role_warning_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(onClick = onRequestRole, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.overview_role_warning_button))
            }
        }
    }
}

@Composable
private fun HeroCounter(totalScreened: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = totalScreened.toString(),
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            text = stringResource(R.string.overview_hero_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SparklineChart(points: List<DaySparkPoint>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.overview_sparkline_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val description = stringResource(R.string.overview_sparkline_description)
        val barColor = MaterialTheme.colorScheme.primary
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .padding(top = 8.dp)
                .semantics { contentDescription = description },
        ) {
            if (points.isEmpty()) {
                return@Canvas
            }
            val maxCount = points.maxOf { it.count }
            val barWidth = size.width / points.size
            points.forEachIndexed { index, point ->
                // A flat "all zero" window still draws a visible baseline rather than nothing.
                val heightFraction = if (maxCount > 0) point.count.toFloat() / maxCount else 0f
                val barHeight = size.height * heightFraction
                drawRect(
                    color = barColor,
                    topLeft = Offset(x = index * barWidth, y = size.height - barHeight),
                    size = Size(width = barWidth * 0.8f, height = barHeight.coerceAtLeast(2f)),
                )
            }
        }
    }
}

/**
 * The action/reason breakdown (issue #59): a horizontal segmented bar sized by call count, plus a
 * legend beneath — mirrors [SparklineChart]'s "title, then a Canvas-adjacent visual" section
 * style. Hidden entirely when every category is zero (nothing has been screened, or nothing
 * matches [state.actionBreakdown][UebersichtUiState.actionBreakdown]'s categories yet).
 */
@Composable
private fun BreakdownSection(entries: List<BreakdownEntry>, modifier: Modifier = Modifier) {
    val nonZero = entries.filter { it.count > 0 }
    if (nonZero.isEmpty()) {
        return
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.overview_breakdown_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val descriptionParts = nonZero.map { entry ->
            "${stringResource(entry.category.labelRes())}: ${entry.count}"
        }
        val description = descriptionParts.joinToString(", ")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .semantics { contentDescription = description },
        ) {
            nonZero.forEach { entry ->
                Box(
                    modifier = Modifier
                        .weight(entry.count.toFloat())
                        .fillMaxHeight()
                        .background(colorForCategory(entry.category)),
                )
            }
        }
        Column(modifier = Modifier.padding(top = 8.dp)) {
            nonZero.forEach { entry -> BreakdownLegendRow(entry) }
        }
    }
}

@Composable
private fun BreakdownLegendRow(entry: BreakdownEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(colorForCategory(entry.category)),
        )
        Text(
            text = stringResource(entry.category.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = entry.count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun colorForCategory(category: BreakdownCategory): Color = when (category) {
    BreakdownCategory.BLOCK -> MaterialTheme.colorScheme.error
    BreakdownCategory.SILENCE -> MaterialTheme.colorScheme.tertiary
    BreakdownCategory.ALLOW_RULE -> MaterialTheme.colorScheme.primary
    BreakdownCategory.CONTACT -> MaterialTheme.colorScheme.secondary
    BreakdownCategory.NO_RULE -> MaterialTheme.colorScheme.outline
}

private fun BreakdownCategory.labelRes(): Int = when (this) {
    BreakdownCategory.BLOCK -> R.string.action_block
    BreakdownCategory.SILENCE -> R.string.action_silence
    BreakdownCategory.ALLOW_RULE -> R.string.action_allow
    BreakdownCategory.CONTACT -> R.string.log_reason_contact
    BreakdownCategory.NO_RULE -> R.string.log_reason_no_rule
}

@Composable
private fun TopCountriesSection(countries: List<TopCountryUi>, modifier: Modifier = Modifier) {
    if (countries.isEmpty()) {
        return
    }
    Column(modifier = modifier) {
        SectionHeader(stringResource(R.string.overview_top_countries_title))
        countries.forEach { country ->
            TopCountryRow(country)
        }
    }
}

@Composable
private fun TopCountryRow(country: TopCountryUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (country) {
            is TopCountryUi.Known -> Text(text = country.flagEmoji)
            is TopCountryUi.Unknown -> Unit
        }
        Text(text = topCountryLabelText(country), modifier = Modifier.weight(1f))
        Text(text = topCountryCount(country).toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun topCountryLabelText(country: TopCountryUi): String = when (country) {
    is TopCountryUi.Known -> country.name
    is TopCountryUi.Unknown -> country.regionCode
}

private fun topCountryCount(country: TopCountryUi): Int = when (country) {
    is TopCountryUi.Known -> country.count
    is TopCountryUi.Unknown -> country.count
}

@Composable
private fun TopRulesSection(rules: List<TopRuleUi>, modifier: Modifier = Modifier) {
    if (rules.isEmpty()) {
        return
    }
    Column(modifier = modifier) {
        SectionHeader(stringResource(R.string.overview_top_rules_title))
        rules.forEach { rule ->
            TopRuleRow(rule)
        }
    }
}

@Composable
private fun TopRuleRow(rule: TopRuleUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (rule) {
            is TopRuleUi.Known -> {
                Text(text = topRuleLabelText(rule.row), modifier = Modifier.weight(1f))
                Text(text = stringResource(rule.row.action.labelRes()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = rule.count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is TopRuleUi.Deleted -> {
                Text(
                    text = stringResource(R.string.overview_rule_deleted),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = rule.count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun topRuleLabelText(row: RuleRowUi): String = when (val label = row.label) {
    is RuleLabel.Country -> label.name
    is RuleLabel.AmbiguousCode -> row.patternText
    RuleLabel.Private -> stringResource(R.string.rules_private_label)
    RuleLabel.Raw -> row.patternText
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun EmptyDashboard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.overview_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.overview_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
