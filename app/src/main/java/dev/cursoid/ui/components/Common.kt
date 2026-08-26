package dev.cursoid.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cursoid.model.AgentStatus
import dev.cursoid.model.RunStatus
import dev.cursoid.ui.theme.StatusArchived
import dev.cursoid.ui.theme.StatusFailure
import dev.cursoid.ui.theme.StatusIdle
import dev.cursoid.ui.theme.StatusRunning
import dev.cursoid.ui.theme.StatusSuccess

@Composable
fun StatusPill(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false,
) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "pulse")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "pulse-alpha",
        ).value
    } else {
        1f
    }

    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .alpha(alpha)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
fun AgentStatusPill(status: AgentStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        AgentStatus.ACTIVE -> "Working" to StatusRunning
        AgentStatus.IDLE -> "Your turn" to StatusIdle
        AgentStatus.ARCHIVED -> "Archived" to StatusArchived
        AgentStatus.UNKNOWN -> "Unknown" to StatusIdle
    }
    StatusPill(label, color, pulsing = status == AgentStatus.ACTIVE, modifier = modifier)
}

@Composable
fun RunStatusPill(status: RunStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        RunStatus.CREATING -> "Starting" to StatusRunning
        RunStatus.RUNNING -> "Running" to StatusRunning
        RunStatus.FINISHED -> "Finished" to StatusSuccess
        RunStatus.ERROR -> "Error" to StatusFailure
        RunStatus.CANCELLED -> "Cancelled" to StatusIdle
        RunStatus.EXPIRED -> "Expired" to StatusIdle
        RunStatus.UNKNOWN -> "Unknown" to StatusIdle
    }
    StatusPill(label, color, pulsing = status.isActive, modifier = modifier)
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorCard(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

/** Skeleton rows shown on first load, so the list does not pop in from a blank screen. */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 5,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha = transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "skeleton-alpha",
    ).value

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = alpha),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.65f)
                        .height(14.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            RoundedCornerShape(6.dp),
                        ),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.4f)
                        .height(10.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                            RoundedCornerShape(6.dp),
                        ),
                )
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
