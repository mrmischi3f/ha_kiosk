@file:OptIn(ExperimentalFoundationApi::class)

package com.hakiosk.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hakiosk.data.ha.HaState
import com.hakiosk.ui.prettyState

@Composable
fun EntityTile(
    entity: HaState,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    height: Dp = 108.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val active = entity.isActive
    val accent = entityAccent(entity, scheme.primary)
    val intensity = if (entity.domain == "light") {
        0.14f + (entity.brightnessPct ?: 100) / 100f * 0.22f
    } else {
        0.24f
    }
    val container = if (active) accent.copy(alpha = intensity).compositeOver(scheme.surfaceContainerHigh)
    else scheme.surfaceContainerHigh
    val shape = RoundedCornerShape(20.dp)

    Surface(
        color = container,
        shape = shape,
        modifier = modifier
            .height(height)
            .clip(shape)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .alpha(if (entity.state == "unavailable") 0.5f else 1f),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (active) accent.copy(alpha = 0.25f) else scheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        entityIcon(entity),
                        contentDescription = null,
                        tint = if (active) accent else scheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (isFavorite) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Pinned",
                        tint = scheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                entity.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                prettyState(entity),
                style = MaterialTheme.typography.bodySmall,
                color = if (active) accent else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
