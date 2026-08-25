package com.formatth.kitaldr.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Native Material icon set used throughout KitaLDR. No emoji/Unicode glyphs. */
object KitaIcons {
    val Home: ImageVector = Icons.Filled.Home
    val Love: ImageVector = Icons.Filled.Favorite
    val LoveOutline: ImageVector = Icons.Filled.FavoriteBorder
    val Calendar: ImageVector = Icons.Filled.CalendarMonth
    val More: ImageVector = Icons.Filled.MoreHoriz
    val Poke: ImageVector = Icons.Filled.Favorite
    val MissYou: ImageVector = Icons.Filled.AutoAwesome
    val WakeUp: ImageVector = Icons.Filled.WbSunny
    val EatWell: ImageVector = Icons.Filled.FavoriteBorder
    val ChevronRight: ImageVector = Icons.Filled.ChevronRight
    val Copy: ImageVector = Icons.Filled.ContentCopy
    val Share: ImageVector = Icons.Filled.Share
    val Send: ImageVector = Icons.Filled.Send
    val Back: ImageVector = Icons.Outlined.ArrowBack
}

@Composable
fun KitaIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

val KitaIconSize = 22.dp
val KitaNavIconSize = 23.dp
