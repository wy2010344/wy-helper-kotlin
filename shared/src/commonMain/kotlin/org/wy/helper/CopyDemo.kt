package org.wy.helper

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CopyDemo() {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(annotatedString = buildRichText()))
    }

    val selectedRichText = remember(textFieldValue.selection, textFieldValue.annotatedString) {
        val sel = textFieldValue.selection
        if (!sel.collapsed) {
            textFieldValue.annotatedString.subSequence(sel.min, sel.max)
        } else {
            null
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                readOnly = true
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Selected Content",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            if (selectedRichText != null) {
                BasicTextField(
                    value = TextFieldValue(annotatedString = selectedRichText),
                    onValueChange = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    readOnly = true
                )
            } else {
                Text(
                    text = "No selection",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun buildRichText() = buildAnnotatedString {
    withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)) {
        append("Rich Text ")
    }
    withStyle(SpanStyle(fontSize = 20.sp, color = Color(0xFFFDD835))) {
        append("\u2605") // ★
    }
    withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)) {
        append(" Image")
    }
    withStyle(SpanStyle(fontSize = 20.sp, color = Color(0xFF43A047))) {
        append("\u25C6") // ◆
    }
    append("\n\n")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))) {
        append("KMP Compose")
    }
    append(" mixed content demo ")
    withStyle(SpanStyle(fontSize = 18.sp, color = Color(0xFFE53935))) {
        append("\u2665") // ♥
    }
    append("\n\n")
    withStyle(SpanStyle(color = Color(0xFFE53935), fontWeight = FontWeight.SemiBold)) {
        append("Red bold text. ")
    }
    withStyle(SpanStyle(color = Color(0xFF1E88E5), fontStyle = FontStyle.Italic)) {
        append("Blue italic text. ")
    }
    withStyle(SpanStyle(fontSize = 18.sp, color = Color(0xFFFDD835))) {
        append("\u2605") // ★
    }
    append("\n")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
        append("Bold large text. ")
    }
    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
        append("Underlined text. ")
    }
    withStyle(SpanStyle(fontSize = 18.sp, color = Color(0xFFE53935))) {
        append("\u2665") // ♥
    }
    append(" ")
    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color(0xFF8E24AA))) {
        append("Purple strikethrough text. ")
    }
    append("\n\n")
    append("Select any text across different styles ")
    withStyle(SpanStyle(fontSize = 18.sp, color = Color(0xFFFDD835))) {
        append("\u2605") // ★
    }
    append(" inline symbols ")
    withStyle(SpanStyle(fontSize = 20.sp, color = Color(0xFF43A047))) {
        append("\u25C6") // ◆
    }
    append(" and the selection below ")
    withStyle(SpanStyle(fontSize = 18.sp, color = Color(0xFFE53935))) {
        append("\u2665") // ♥
    }
    append(" preserves the original formatting.")
}
