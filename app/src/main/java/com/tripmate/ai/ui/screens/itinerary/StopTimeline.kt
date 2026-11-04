package com.tripmate.ai.ui.screens.itinerary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FreeBreakfast
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripmate.ai.R
import com.tripmate.ai.domain.model.StopCategory
import com.tripmate.ai.domain.model.StopStatus
import com.tripmate.ai.domain.model.TripStop
import com.tripmate.ai.ui.components.SoftCard
import com.tripmate.ai.ui.components.VerificationBadge
import com.tripmate.ai.ui.components.labelRes
import com.tripmate.ai.util.Formatters

/** One row of the vertical stop timeline, shared by the itinerary and the live companion. */
@Composable
fun StopTimelineRow(
    stop: TripStop,
    isFirst: Boolean,
    isLast: Boolean,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val dotColor = when {
        stop.status == StopStatus.DONE -> MaterialTheme.colorScheme.primary
        stop.status == StopStatus.SKIPPED -> MaterialTheme.colorScheme.outline
        highlighted || stop.status.isInProgress -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val time = Formatters.time(context, stop.actualArrivalMinutes ?: stop.startMinutes)
    val description = "$time, ${stop.title}, ${stringResource(stop.status.labelRes())}"
    Column {
        if (!isFirst && stop.travelMinutesFromPrev > 0 && stop.status != StopStatus.SKIPPED) {
            Row(Modifier.padding(start = 64.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DirectionsCar, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.travel_line, Formatters.duration(context, stop.travelMinutesFromPrev), stringResource(stop.travelSource.labelRes())),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).semantics(mergeDescendants = true) { contentDescription = description }) {
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.size(22.dp).background(dotColor, CircleShape), contentAlignment = Alignment.Center) {
                    val icon = when {
                        stop.status == StopStatus.DONE -> Icons.Filled.Check
                        stop.status == StopStatus.SKIPPED -> Icons.Filled.SkipNext
                        stop.category == StopCategory.MEAL -> Icons.Filled.Restaurant
                        stop.category == StopCategory.BREAK -> Icons.Filled.FreeBreakfast
                        else -> Icons.Filled.Place
                    }
                    Icon(icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.surface)
                }
                if (!isLast) Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
            }
            Spacer(Modifier.width(12.dp))
            SoftCard(Modifier.weight(1f).padding(bottom = 12.dp), onClick = onClick) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stop.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (stop.status == StopStatus.SKIPPED) TextDecoration.LineThrough else null,
                            color = if (stop.status == StopStatus.SKIPPED) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${Formatters.duration(context, stop.durationMinutes)} · ${stringResource(stop.status.labelRes())}" +
                                (stop.priceEstimate?.let { " · " + context.getString(R.string.price_estimate, Formatters.money(context, it.amount, it.currency)) } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (stop.category == StopCategory.SIGHT || stop.category == StopCategory.CUSTOM) {
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                VerificationBadge(stop.verification)
                                if (stop.opensMinutes != null && stop.closesMinutes != null) {
                                    Text(
                                        stringResource(R.string.hours_typical, Formatters.time(context, stop.opensMinutes), Formatters.time(context, stop.closesMinutes)),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                        if (stop.notes.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(stop.notes, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    trailing()
                }
            }
        }
    }
}

