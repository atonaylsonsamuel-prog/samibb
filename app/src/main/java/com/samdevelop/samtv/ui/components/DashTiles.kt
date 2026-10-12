package com.samdevelop.samtv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samdevelop.samtv.ui.theme.SamColors

/*
 * Tela inicial no estilo "painel IPTV": cartões grandes navegáveis pelo controle remoto.
 * Em foco o cartão ganha borda branca, brilho e leve aumento — visível a 3 metros da TV.
 */

private val TileShape = RoundedCornerShape(14.dp)

private val TileIdle = Brush.linearGradient(listOf(Color(0xFF2A1260), Color(0xFF140A33)))
private val TileFocused = Brush.linearGradient(listOf(SamColors.Purple, Color(0xFF3B0FA0)))
private val HeroIdle = Brush.linearGradient(listOf(Color(0xFF4A16B8), Color(0xFF1B0B4A)))
private val HeroFocused = Brush.linearGradient(listOf(SamColors.PurpleAccent, SamColors.Purple, SamColors.Blue))
private val SideIdle = Brush.linearGradient(listOf(Color(0xFF151B33), Color(0xFF10162A)))
private val SideFocused = Brush.linearGradient(listOf(Color(0xFF3B1A8A), Color(0xFF241060)))

/** Base comum: foco, clique por toque/OK do controle e realce. */
@Composable
private fun FocusTile(
    onClick: () -> Unit,
    idle: Brush,
    focusedBrush: Brush,
    modifier: Modifier,
    focusRequester: FocusRequester?,
    enabled: Boolean,
    content: @Composable (focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, label = "escala")
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(TileShape)
            .background(if (focused) focusedBrush else idle)
            .border(if (focused) 3.dp else 1.dp, if (focused) Color.White else SamColors.Outline, TileShape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        content(focused)
    }
}

/** Cartão principal (TV ao vivo). */
@Composable
fun HeroTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    focusRequester: FocusRequester? = null,
) {
    FocusTile(onClick, HeroIdle, HeroFocused, modifier, focusRequester, enabled = true) {
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
            Spacer(Modifier.size(10.dp))
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

/** Cartão médio (Filmes, Séries, Conta, Trocar lista). */
@Composable
fun MenuTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    note: String? = null,
    focusRequester: FocusRequester? = null,
) {
    FocusTile(onClick, TileIdle, TileFocused, modifier.graphicsLayer { alpha = if (enabled) 1f else 0.45f }, focusRequester, enabled) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            Spacer(Modifier.size(8.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) Text(note, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Botão lateral (Ajustes, Recarregar, Sair…). */
@Composable
fun SideTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    accent: Color = Color.White,
) {
    FocusTile(onClick, SideIdle, SideFocused, modifier, null, enabled = true) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(12.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Botão de texto que também funciona com o controle remoto (usado nas telas de ativação). */
@Composable
fun FocusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = true,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    FocusTile(
        onClick,
        if (primary) TileIdle else SideIdle,
        if (primary) HeroFocused else SideFocused,
        modifier.graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        focusRequester,
        enabled,
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}
