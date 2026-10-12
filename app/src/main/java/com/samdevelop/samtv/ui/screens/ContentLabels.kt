package com.samdevelop.samtv.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.VideoLibrary
import com.samdevelop.samtv.core.model.ContentType

fun iconFor(type: ContentType) = when (type) {
    ContentType.CHANNEL -> Icons.Outlined.LiveTv
    ContentType.MOVIE -> Icons.Outlined.Movie
    ContentType.SERIES, ContentType.EPISODE -> Icons.Outlined.VideoLibrary
}

fun labelFor(type: ContentType) = when (type) {
    ContentType.CHANNEL -> "Canal"
    ContentType.MOVIE -> "Filme"
    ContentType.SERIES -> "Série"
    ContentType.EPISODE -> "Episódio"
}
