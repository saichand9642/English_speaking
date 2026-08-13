package com.speak.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.speak.app.ui.theme.MinTouchTarget
import com.speak.app.ui.theme.SpeakIcons

/** A titled card. The only container style the app uses, for visual calm. */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(Modifier.padding(18.dp)) {
            if (title != null) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
    }
}

/** The single primary action on a screen. Full width, large target. */
@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
        shape = RoundedCornerShape(16.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * The microphone button: the centre of the app, and the largest target in it.
 *
 * The ring around it tracks the live input level so it is obvious the app is
 * hearing something, which is the one piece of feedback that stops a learner
 * repeating themselves into a dead microphone.
 */
@Composable
fun MicButton(
    listening: Boolean,
    level: Float,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ringScale by animateFloatAsState(
        targetValue = if (listening) 1f + (level.coerceIn(0f, 1f) * 0.35f) else 1f,
        animationSpec = tween(120),
        label = "micLevelRing"
    )
    val primary = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.size(184.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            Box(
                Modifier
                    .size(150.dp)
                    .scale(ringScale)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(primary.copy(alpha = 0.16f))
            )
        }
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(132.dp),
            shape = RoundedCornerShape(percent = 50),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (listening) MaterialTheme.colorScheme.tertiary else primary,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = if (listening) SpeakIcons.Stop else SpeakIcons.Mic,
                contentDescription = if (listening) "Stop listening" else "Start speaking",
                modifier = Modifier.size(52.dp)
            )
        }
    }
}

/** An inline note the user can act on. Never blocks the screen. */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.NEUTRAL,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    val container = when (tone) {
        BannerTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
        BannerTone.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
        BannerTone.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val onContainer = when (tone) {
        BannerTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        BannerTone.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
        BannerTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .padding(16.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = onContainer)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(actionText)
            }
        }
    }
}

enum class BannerTone { NEUTRAL, WARNING, ERROR }

/** A labelled statistic. Used across progress and weekly summary. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null
) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LabelledProgress(label: String, progress: Float, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
