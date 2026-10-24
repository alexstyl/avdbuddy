package com.avdbuddy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composeunstyled.DialogPanel
import com.composeunstyled.DialogProperties
import com.composeunstyled.Scrim
import com.composeunstyled.UnstyledDialog
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.theme.popupContainerStyle

@Composable
fun AppDialog(
    title: String,
    tag: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val style = JewelTheme.popupContainerStyle
    val shape = RoundedCornerShape(style.metrics.cornerSize)
    UnstyledDialog(
        visible = true,
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnClickOutside = false),
        overlay = { Scrim(scrimColor = style.colors.shadow) },
    ) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            DialogPanel(
                modifier = modifier.clip(shape).background(style.colors.background)
                    .border(style.metrics.borderWidth, style.colors.border, shape)
                    .semantics { dialog() }.testTag(tag),
                paneTitle = title,
                content = content,
            )
        }
    }
}
