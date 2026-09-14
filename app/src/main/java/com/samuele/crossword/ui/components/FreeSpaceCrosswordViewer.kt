package com.samuele.crossword.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samuele.crossword.engine.CrosswordResult
import com.samuele.crossword.engine.CrosswordTheme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GridView

@Composable
fun FreeSpaceCrosswordViewer(
    result: CrosswordResult,
    theme: CrosswordTheme,
    showSolution: Boolean,
    onToggleSolution: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val aspectRatio = remember(result) {
        val r = result.rows.coerceAtLeast(1)
        result.cols.toFloat() / r.toFloat()
    }

    val whiteCellsCount = remember(result) {
        result.whiteCells.sumOf { row -> row.count { it } }
    }
    val densityPct = remember(result) {
        val area = result.rows * result.cols
        if (area > 0) (whiteCellsCount * 100) / area else 0
    }

    GlassmorphicCard(
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = 380f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header del Cruciverba con icone sottili stile iOS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GridView,
                        contentDescription = null,
                        tint = Color(0xFF9EA3AE),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Griglia ${result.cols}×${result.rows}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF9EA3AE)
                    )
                }

                if (result.mysteryInfo != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "🔑 Cardine: ${result.mysteryInfo.revealedCount}/${result.mysteryInfo.totalLetters} incroci",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFBBF24)
                            )
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF9EA3AE),
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "${result.assignments.size} parole (${densityPct}% densità)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF9EA3AE)
                        )
                    }
                }
            }

            // Vista del Cruciverba 2D con animazione fluida di espansione/riduzione delle dimensioni
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val availW = (maxWidth - 16.dp).coerceAtLeast(120.dp)
                val maxCardH = 380.dp

                // Dimensioni dinamiche proporzionate al rapporto d'aspetto della griglia
                val targetGridW: Dp
                val targetGridH: Dp
                if (availW.value / maxCardH.value > aspectRatio) {
                    targetGridH = maxCardH
                    targetGridW = maxCardH * aspectRatio
                } else {
                    targetGridW = availW
                    targetGridH = availW / aspectRatio
                }

                val animatedGridW by animateDpAsState(
                    targetValue = targetGridW,
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = 420f
                    ),
                    label = "animatedCrosswordWidth"
                )
                val animatedGridH by animateDpAsState(
                    targetValue = targetGridH,
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = 420f
                    ),
                    label = "animatedCrosswordHeight"
                )

                // Contenitore rigorosamente Obsidian Scuro: NESSUN bianco spurio durante le animazioni di allargamento
                Box(
                    modifier = Modifier
                        .size(width = animatedGridW, height = animatedGridH)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0B0C10))
                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    CrosswordGridView(
                        result = result,
                        theme = theme,
                        showSolution = showSolution,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
