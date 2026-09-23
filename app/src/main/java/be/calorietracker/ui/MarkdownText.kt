package be.calorietracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MarkdownText(markdown: String, modifier: Modifier = Modifier) {
  var pendingUrl by remember { mutableStateOf<String?>(null) }
  val safe = safeMarkdown(markdown)
  val blocks = remember(safe) { markdownBlocks(safe) }
  Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
    blocks.forEach { block ->
      when (block.kind) {
        "space" -> Spacer(Modifier.height(2.dp))
        "code" ->
          Text(
            block.text,
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp)).padding(12.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
          )
        "h1" -> MarkdownLine(block.text, MaterialTheme.typography.titleMedium, onLink = { pendingUrl = it })
        "h2" -> MarkdownLine(block.text, MaterialTheme.typography.titleMedium, onLink = { pendingUrl = it })
        "h3" -> MarkdownLine(block.text, MaterialTheme.typography.titleMedium, onLink = { pendingUrl = it })
        "quote" ->
          Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 0.dp)) {
            MarkdownLine(block.text, MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), Modifier.padding(12.dp), onLink = { pendingUrl = it })
          }
        "bullet" -> Row { Text("•  ", fontWeight = FontWeight.Bold); MarkdownLine(block.text, MaterialTheme.typography.bodyMedium, Modifier.weight(1f), onLink = { pendingUrl = it }) }
        "number" -> Row { Text("${block.marker}  ", fontWeight = FontWeight.Bold); MarkdownLine(block.text, MaterialTheme.typography.bodyMedium, Modifier.weight(1f), onLink = { pendingUrl = it }) }
        else -> MarkdownLine(block.text, MaterialTheme.typography.bodyMedium, onLink = { pendingUrl = it })
      }
    }
  }
  pendingUrl?.let { url ->
    val handler = LocalUriHandler.current
    AlertDialog(
      onDismissRequest = { pendingUrl = null },
      title = { Text("Open external link?") },
      text = { Text(url) },
      confirmButton = { TextButton({ handler.openUri(url); pendingUrl = null }) { Text("Open") } },
      dismissButton = { TextButton({ pendingUrl = null }) { Text("Cancel") } },
    )
  }
}

internal fun safeMarkdown(value: String): String =
  value
    .replace(Regex("!\\[[^]]*]\\([^)]*\\)"), "[Image omitted]")
    .replace(Regex("<[^>]+>"), "")

internal data class MarkdownBlock(val kind: String, val text: String, val marker: String = "")

internal fun markdownBlocks(value: String): List<MarkdownBlock> {
  val result = mutableListOf<MarkdownBlock>()
  val code = StringBuilder()
  var inCode = false
  value.lines().forEach { line ->
    if (line.trimStart().startsWith("```")) {
      if (inCode) { result += MarkdownBlock("code", code.toString().trimEnd()); code.clear() }
      inCode = !inCode
    } else if (inCode) code.appendLine(line)
    else {
      val trimmed = line.trim()
      when {
        trimmed.isEmpty() -> result += MarkdownBlock("space", "")
        trimmed.startsWith("### ") -> result += MarkdownBlock("h3", trimmed.removePrefix("### "))
        trimmed.startsWith("## ") -> result += MarkdownBlock("h2", trimmed.removePrefix("## "))
        trimmed.startsWith("# ") -> result += MarkdownBlock("h1", trimmed.removePrefix("# "))
        trimmed.startsWith("> ") -> result += MarkdownBlock("quote", trimmed.removePrefix("> "))
        trimmed.startsWith("- ") || trimmed.startsWith("* ") -> result += MarkdownBlock("bullet", trimmed.drop(2))
        Regex("^\\d+\\. .*$").matches(trimmed) -> {
          val marker = trimmed.substringBefore(' ') 
          result += MarkdownBlock("number", trimmed.substringAfter(' '), marker)
        }
        else -> result += MarkdownBlock("paragraph", trimmed)
      }
    }
  }
  if (code.isNotEmpty()) result += MarkdownBlock("code", code.toString().trimEnd())
  return result
}

@Composable
private fun MarkdownLine(
  value: String,
  style: TextStyle,
  modifier: Modifier = Modifier,
  onLink: (String) -> Unit,
) {
  val colour = MaterialTheme.colorScheme.onSurface
  val linkColour = MaterialTheme.colorScheme.primary
  val annotated = remember(value, colour, linkColour) { inlineMarkdown(value, colour, linkColour) }
  ClickableText(
    text = annotated,
    modifier = modifier,
    style = style.copy(color = colour),
    onClick = { position -> annotated.getStringAnnotations("URL", position, position).firstOrNull()?.let { onLink(it.item) } },
  )
}

private fun inlineMarkdown(value: String, colour: Color, linkColour: Color): AnnotatedString {
  val token = Regex("(\\*\\*[^*]+\\*\\*|_[^_]+_|`[^`]+`|\\[[^]]+]\\(https?://[^)]+\\))")
  return buildAnnotatedString {
    var cursor = 0
    token.findAll(value).forEach { match ->
      append(value.substring(cursor, match.range.first))
      val raw = match.value
      when {
        raw.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(raw.removeSurrounding("**")) }
        raw.startsWith("_") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(raw.removeSurrounding("_")) }
        raw.startsWith("`") -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colour.copy(alpha = .08f))) { append(raw.removeSurrounding("`")) }
        else -> {
          val label = raw.substringAfter('[').substringBefore(']')
          val url = raw.substringAfter("](").dropLast(1)
          pushStringAnnotation("URL", url)
          withStyle(SpanStyle(color = linkColour, fontWeight = FontWeight.SemiBold)) { append(label) }
          pop()
        }
      }
      cursor = match.range.last + 1
    }
    append(value.substring(cursor))
  }
}
