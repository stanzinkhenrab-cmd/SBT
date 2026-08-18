package com.kvkleh.sbtsurvey.ui.survey

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kvkleh.sbtsurvey.location.GpsState
import com.kvkleh.sbtsurvey.location.GpsStatus
import com.kvkleh.sbtsurvey.ui.components.Fmt
import com.kvkleh.sbtsurvey.ui.components.SectionCard
import com.kvkleh.sbtsurvey.ui.theme.StatusOff
import com.kvkleh.sbtsurvey.ui.theme.StatusOk
import com.kvkleh.sbtsurvey.ui.theme.StatusWarn

/**
 * GPS acquisition card.
 *
 * Acquisition never blocks the form: the surveyor can keep filling in observations while
 * a fix is being searched for, and a survey can be completed with no coordinates at all
 * if the sky view is hopeless.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GpsSection(
    gps: GpsState,
    onGetLocation: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var permissionAsked by remember { mutableStateOf(false) }
    var permissionPermanentlyDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        permissionAsked = true
        if (granted.values.any { it }) {
            onGetLocation()
        } else {
            permissionPermanentlyDenied = true
        }
    }

    fun ensurePermissionThen(action: () -> Unit) {
        if (gps.status == GpsStatus.PERMISSION_REQUIRED && !permissionAsked) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            action()
        }
    }

    // Start looking as soon as the section appears, so a fix is usually ready by the time
    // the surveyor has finished typing the village name.
    LaunchedEffect(Unit) {
        if (gps.status == GpsStatus.IDLE || gps.status == GpsStatus.PERMISSION_REQUIRED) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    SectionCard(
        title = "GPS & Elevation",
        number = 4,
        subtitle = "Recorded automatically from the device",
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor(gps.status))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "GPS Status: ${gps.statusLabel}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            if (gps.searching) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
            }
        }

        if (gps.searching) {
            Column {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Searching for satellites… ${gps.elapsedSeconds}s" +
                        if (gps.fix != null) " (keeping best reading so far)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val fix = gps.fix
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GpsValueRow("Latitude", Fmt.coordinate(fix?.latitude))
            GpsValueRow("Longitude", Fmt.coordinate(fix?.longitude))
            GpsValueRow("Altitude", Fmt.metres(fix?.altitude))
            GpsValueRow("Accuracy", Fmt.accuracy(fix?.accuracy))
            GpsValueRow("Acquired at", Fmt.dateTime(fix?.timestamp))
        }

        gps.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (gps.status == GpsStatus.UNAVAILABLE ||
                    gps.status == GpsStatus.LOCATION_OFF
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        if (permissionPermanentlyDenied && !gps.hasFix) {
            Text(
                text = "Location permission was declined. Coordinates will be left blank; " +
                    "you can still complete and save the survey. Grant location access in " +
                    "Android Settings › Apps › SBT Field Survey to record coordinates.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { ensurePermissionThen(onGetLocation) },
                enabled = !gps.searching,
                modifier = Modifier.heightIn(min = 54.dp)
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Get Current Location")
            }
            OutlinedButton(
                onClick = { ensurePermissionThen(onRefresh) },
                modifier = Modifier.heightIn(min = 54.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Refresh GPS")
            }
        }

        if (gps.hasFix) {
            Text(
                text = "The best reading is kept. Refresh GPS replaces it with a new one.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GpsValueRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        )
    }
}

private fun statusColor(status: GpsStatus): Color = when (status) {
    GpsStatus.CONNECTED -> StatusOk
    GpsStatus.IMPROVING, GpsStatus.ACQUIRING -> StatusWarn
    GpsStatus.UNAVAILABLE, GpsStatus.LOCATION_OFF, GpsStatus.PERMISSION_REQUIRED ->
        Color(0xFFBA1A1A)
    GpsStatus.IDLE -> StatusOff
}
