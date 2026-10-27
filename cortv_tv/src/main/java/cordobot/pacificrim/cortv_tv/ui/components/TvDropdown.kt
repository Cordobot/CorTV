package cordobot.pacificrim.cortv_tv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import cordobot.pacificrim.cortv_tv.ui.theme.CorTvColors
import kotlinx.coroutines.delay

/**
 * Remote-friendly dropdown: a button showing "[label]: [value]" that opens a menu of options.
 * Focus jumps to the selected option when the menu opens.
 */
@Composable
fun <T> TvDropdown(
    label: String?,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedText = options.firstOrNull { it.first == selected }?.second.orEmpty()
    val selectedFocus = remember { FocusRequester() }

    Box(modifier) {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.colors(
                containerColor = CorTvColors.Neutral,
                contentColor = Color.White,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black
            ),
            contentPadding = PaddingValues(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
        ) {
            if (label != null) {
                Text("$label: ", style = MaterialTheme.typography.labelLarge)
            }
            Text(selectedText, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = CorTvColors.CardFocused,
            shape = RoundedCornerShape(12.dp),
            scrollState = rememberScrollState()
        ) {
            options.forEach { (value, text) ->
                val isSelected = value == selected
                Surface(
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                    modifier = Modifier
                        .widthIn(min = 220.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .then(if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(20.dp)) {
                            if (isSelected) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            delay(120) // the menu popup needs a frame to attach
            runCatching { selectedFocus.requestFocus() }
        }
    }
}
