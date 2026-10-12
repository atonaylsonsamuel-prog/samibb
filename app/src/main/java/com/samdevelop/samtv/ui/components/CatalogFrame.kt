package com.samdevelop.samtv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.samdevelop.samtv.core.model.ChannelCategory
import com.samdevelop.samtv.ui.theme.SamColors

/**
 * Borda branca quando o item recebe foco do controle remoto. Colocar ANTES do clickable.
 * No toque não aparece (o toque não move o foco).
 */
@Composable
fun Modifier.focusHighlight(shape: Shape = RoundedCornerShape(10.dp)): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .border(if (focused) 2.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
}

/**
 * Catálogo com categorias: na horizontal (TV e tablet) as categorias ficam numa coluna à esquerda,
 * como nos painéis IPTV; na vertical (celular) viram uma fileira de chips no topo.
 */
@Composable
fun CatalogFrame(
    categories: List<ChannelCategory>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    allLabel: String = "Todos",
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight && maxWidth >= 600.dp && categories.isNotEmpty()) {
            Row(Modifier.fillMaxSize()) {
                CategoryColumn(categories, selectedId, onSelect, allLabel, Modifier.width(250.dp).fillMaxHeight())
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                CategoryChips(categories, selectedId, onSelect)
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        }
    }
}

@Composable
fun CategoryColumn(
    categories: List<ChannelCategory>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    allLabel: String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.background(SamColors.Surface.copy(alpha = 0.6f)),
        contentPadding = PaddingValues(8.dp),
    ) {
        item(key = "__all") { CategoryItem(allLabel, selectedId == null) { onSelect(null) } }
        items(categories, key = { it.id }) { c -> CategoryItem(c.name, selectedId == c.id) { onSelect(c.id) } }
    }
}

@Composable
private fun CategoryItem(name: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .heightIn(min = 40.dp)
            .background(if (selected) SamColors.Purple else Color.Transparent, shape)
            .focusHighlight(shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Nomes longos quebram em até duas linhas em vez de serem cortados.
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else SamColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
