package org.wy.helper

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ImageTextDemo() {
    val inlineContent = remember {
        mapOf(
            "star" to InlineTextContent(
                Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.TextCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xFFFDD835))
                )
            },
            "heart" to InlineTextContent(
                Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.TextCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                )
            },
            "diamond" to InlineTextContent(
                Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.TextCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Color(0xFF43A047))
                )
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            SelectionContainer {
                Text(
                    text = buildRichImageText(),
                    modifier = Modifier.padding(16.dp),
                    inlineContent = inlineContent
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Note",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.tertiaryContainer
        ) {
            Text(
                text = "Text with inline images is selectable (OS native selection), but Compose does not expose a programmatic API to read current selection from SelectionContainer. For selection tracking, use the CopyDemo with styled symbols instead.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

private fun buildRichImageText() = buildAnnotatedString {
    withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)) {
        append("Rich Text ")
    }
    appendInlineContent("star", "[star]")
    withStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)) {
        append(" Image")
    }
    appendInlineContent("diamond", "[diamond]")
    append("\n\n")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))) {
        append("KMP Compose")
    }
    append(" mixed content demo ")
    appendInlineContent("heart", "[heart]")
    append("\n\n")
    withStyle(SpanStyle(color = Color(0xFFE53935), fontWeight = FontWeight.SemiBold)) {
        append("Red bold text. ")
    }
    withStyle(SpanStyle(color = Color(0xFF1E88E5), fontStyle = FontStyle.Italic)) {
        append("Blue italic text. ")
    }
    appendInlineContent("star", "[star]")
    append("\n")
    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
        append("Bold large text. ")
    }
    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
        append("Underlined text. ")
    }
    appendInlineContent("heart", "[heart]")
    append(" ")
    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color(0xFF8E24AA))) {
        append("Purple strikethrough text. ")
    }
    append("\n\n")
    append("Select any text across different styles ")
    appendInlineContent("star", "[star]")
    append(" inline images ")
    appendInlineContent("diamond", "[diamond]")
    append(" and your OS handles selection natively ")
    appendInlineContent("heart", "[heart]")
    append(".")
}
