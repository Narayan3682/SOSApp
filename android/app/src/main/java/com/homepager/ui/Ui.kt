package com.homepager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small shared building blocks so every screen looks like the same app. */

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
fun Dot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

@Composable
fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
}

@Composable
fun Muted(text: String, size: Int = 13) {
    Text(text, fontSize = size.sp, lineHeight = (size + 5).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun StatusPill(text: String, color: Color) {
    Row(
        Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(color, 8); Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
fun BigButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, tonal: Boolean = false, onClick: () -> Unit) {
    val m = modifier.fillMaxWidth().height(56.dp)
    val shape = RoundedCornerShape(16.dp)
    if (tonal) FilledTonalButton(onClick, m, enabled = enabled, shape = shape) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
    else Button(onClick, m, enabled = enabled, shape = shape) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick, modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
        Text(text, fontSize = 15.sp)
    }
}

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    placeholder: String? = null, error: String? = null, help: String? = null, singleLine: Boolean = true,
) {
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value, onChange, Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) } },
            isError = error != null, singleLine = singleLine, shape = RoundedCornerShape(14.dp),
        )
        val msg = error ?: help
        if (msg != null) Text(msg, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
