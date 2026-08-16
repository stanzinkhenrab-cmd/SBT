package com.kvkleh.sbtsurvey.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kvkleh.sbtsurvey.data.location.GpsStatus

/** What the auto-save pill is currently reporting. */
enum class AutoSaveState { Idle, Saving, Saved, Failed }

/**
 * The small pill in the app bar that tells the surveyor their work is on disk.
 * Auto-save is the app's central promise, so it is always visible while a form
 * is open rather than hidden behind a toast.
 */
@Composable
fun AutoSaveIndicator(state: AutoSaveState, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(
        targetValue = if (state == AutoSaveState.Idle) 0.75f else 1f,
        label = "autoSaveAlpha"
    )
    val (label, icon, tint) = when (state) {
        AutoSaveState.Idle -> Triple("Auto-save on", Icons.Filled.Save, MaterialTheme.colorScheme.onPrimary)
        AutoSaveState.Saving -> Triple("Saving…", null, MaterialTheme.colorScheme.onPrimary)
        AutoSaveState.Saved -> Triple("Auto-saved", Icons.Filled.Save, MaterialTheme.colorScheme.onPrimary)
        AutoSaveState.Failed -> Triple("Not saved", Icons.Filled.ErrorOutline, MaterialTheme.colorScheme.onPrimary)
    }

    StatusPill(
        label = label,
        icon = icon,
        contentColor = tint,
        containerColor = if (state == AutoSaveState.Failed) {
            MaterialTheme.colorScheme.error
        } else {
            Color.White.copy(alpha = 0.18f)
        },
        showSpinner = state == AutoSaveState.Saving,
        modifier = modifier.alpha(alpha)
    )
}

/** Status of the GPS receiver, shown on the location card. */
@Composable
fun GpsStatusPill(status: GpsStatus, modifier: Modifier = Modifier) {
    val (label, icon, container, content) = when (status) {
        is GpsStatus.Ready -> StatusStyle(
            "GPS Ready",
            Icons.Filled.GpsFixed,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )

        GpsStatus.Acquiring -> StatusStyle(
            "Acquiring GPS…",
            Icons.Filled.GpsNotFixed,
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer
        )

        GpsStatus.PermissionRequired -> StatusStyle(
            "Location permission needed",
            Icons.Filled.GpsOff,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )

        GpsStatus.ServicesDisabled -> StatusStyle(
            "Location services off",
            Icons.Filled.GpsOff,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )

        GpsStatus.Unavailable -> StatusStyle(
            "Location unavailable",
            Icons.Filled.CloudOff,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    StatusPill(
        label = label,
        icon = icon,
        contentColor = content,
        containerColor = container,
        showSpinner = status == GpsStatus.Acquiring,
        modifier = modifier
    )
}

private data class StatusStyle(
    val label: String,
    val icon: ImageVector,
    val container: Color,
    val content: Color
)

@Composable
fun StatusPill(
    label: String,
    icon: ImageVector?,
    contentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    showSpinner: Boolean = false
) {
    Row(
        modifier = modifier
            .background(containerColor, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = contentColor
            )
            Spacer(Modifier.width(6.dp))
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}
