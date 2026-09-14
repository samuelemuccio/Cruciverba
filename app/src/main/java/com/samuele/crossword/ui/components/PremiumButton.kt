package com.samuele.crossword.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samuele.crossword.ui.theme.RoyalIndigoDark
import com.samuele.crossword.ui.theme.RoyalIndigoLight

enum class ButtonVariant {
    Primary,
    Secondary,
    Accent,
    Glass
}

@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
    variant: ButtonVariant = ButtonVariant.Primary
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val targetScale = when {
        isLoading -> 0.98f
        isPressed && enabled -> 0.96f
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "btnScale"
    )

    val shape = CircleShape // Pillola perfetta stile iOS

    val primaryGradient = Brush.horizontalGradient(
        listOf(RoyalIndigoDark, RoyalIndigoLight)
    )

    val luminescentBorder = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.22f),
            Color.White.copy(alpha = 0.04f)
        )
    )

    val buttonModifier = modifier
        .height(52.dp)
        .scale(scale)
        .then(
            if (variant == ButtonVariant.Primary && enabled) {
                Modifier.shadow(
                    elevation = 8.dp,
                    shape = shape,
                    ambientColor = RoyalIndigoDark.copy(alpha = 0.35f),
                    spotColor = RoyalIndigoDark.copy(alpha = 0.45f)
                )
            } else {
                Modifier
            }
        )
        .clip(shape)
        .then(
            when (variant) {
                ButtonVariant.Primary -> {
                    if (enabled) {
                        Modifier.background(primaryGradient)
                    } else {
                        Modifier.background(RoyalIndigoDark.copy(alpha = 0.35f))
                    }
                }
                ButtonVariant.Secondary, ButtonVariant.Glass -> {
                    Modifier
                        .background(Color(0xFF161926).copy(alpha = 0.85f))
                        .border(BorderStroke(1.dp, luminescentBorder), shape)
                }
                ButtonVariant.Accent -> {
                    Modifier.background(MaterialTheme.colorScheme.tertiary)
                }
            }
        )
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled && !isLoading,
            onClick = onClick
        )

    Box(
        modifier = buttonModifier.padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = Triple(isLoading, isSuccess, text),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "buttonContent"
        ) { (loading, success, currentText) ->
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    loading -> {
                        CircularProgressIndicator(
                            strokeWidth = 2.5.dp,
                            color = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Download...",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    success -> {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Successo",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Salvato!",
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    else -> {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = currentText,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }
        }
    }
}


