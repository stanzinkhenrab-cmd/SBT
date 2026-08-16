package com.kvk.leh.seabuckthorn.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kvk.leh.seabuckthorn.domain.model.MaturityStage
import com.kvk.leh.seabuckthorn.ui.theme.StageIntermediate
import com.kvk.leh.seabuckthorn.ui.theme.StageOverripe
import com.kvk.leh.seabuckthorn.ui.theme.StageRipe
import com.kvk.leh.seabuckthorn.ui.theme.StageUnripe

fun colorForStage(stage: MaturityStage): Color = when (stage) {
    MaturityStage.UNRIPE -> StageUnripe
    MaturityStage.INTERMEDIATE -> StageIntermediate
    MaturityStage.RIPE -> StageRipe
    MaturityStage.OVERRIPE -> StageOverripe
}

/** Large, thumb-friendly card used to pick the dominant fruit maturity stage. */
@Composable
fun MaturityStageCard(
    stage: MaturityStage,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stageColor = colorForStage(stage)
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) stageColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) stageColor else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(stageColor)
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = stage.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
