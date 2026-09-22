package be.calorietracker.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import be.calorietracker.domain.*
import java.util.Locale

fun Double?.fmt(digits: Int = 0) =
  this?.let { String.format(Locale.getDefault(), "%.${digits}f", it) } ?: "—"

@Composable
fun PageTitle(eyebrow: String, title: String, action: (@Composable () -> Unit)? = null) {
  Column(Modifier.padding(top = 14.dp, bottom = 10.dp)) {
    Text(
      eyebrow.uppercase(),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.primary,
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
      action?.invoke()
    }
  }
}

@Composable
fun Panel(
  modifier: Modifier = Modifier,
  tint: Color = MaterialTheme.colorScheme.surfaceContainer,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = tint) {
    Column(
      Modifier.padding(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
      content = content,
    )
  }
}

@Composable
fun Section(title: String, action: String? = null, onAction: () -> Unit = {}) {
  Row(
    Modifier.fillMaxWidth().padding(top = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    if (action != null) TextButton(onClick = onAction) { Text(action) }
  }
}

@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
  Panel {
    Icon(
      Icons.Rounded.Spa,
      null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(32.dp),
    )
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (action != null) Button(onClick = onAction) { Text(action) }
  }
}

@Composable
fun Field(
  label: String,
  value: String,
  onValue: (String) -> Unit,
  numeric: Boolean = false,
  modifier: Modifier = Modifier,
) {
  OutlinedTextField(
    value,
    { onValue(if (numeric) it.replace(',', '.') else it) },
    label = { Text(label) },
    singleLine = true,
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    keyboardOptions =
      KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
  )
}

@Composable
fun Choice(label: String, options: List<String>, selected: String, onChoose: (String) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Row(
      Modifier.horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      options.forEach {
        FilterChip(selected = it == selected, onClick = { onChoose(it) }, label = { Text(it) })
      }
    }
  }
}

@Composable
fun Modal(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      Modifier.fillMaxWidth().padding(12.dp).heightIn(max = 780.dp),
      shape = RoundedCornerShape(28.dp),
    ) {
      Column(
        Modifier.padding(20.dp).verticalScroll(rememberScrollState()).imePadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
          IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close") }
        }
        content()
      }
    }
  }
}

@Composable
fun NutrientGrid(n: Nutrients) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    listOf(
        "Energy" to energy(n.kcal),
        "Protein" to "${n.protein.fmt(1)} g",
        "Carbohydrates" to "${n.carbs.fmt(1)} g",
        "Fat" to "${n.fat.fmt(1)} g",
        "Saturated fat" to "${n.saturated.fmt(1)} g",
        "Sugars" to "${n.sugars.fmt(1)} g",
        "Fibre" to "${n.fibre.fmt(1)} g",
        "Salt" to "${n.salt.fmt(2)} g",
      )
      .forEach { (label, value) ->
        Row(Modifier.fillMaxWidth()) {
          Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(value, fontWeight = FontWeight.Medium)
        }
      }
    n.extra.forEach { (k, v) -> Text("$k: ${v.fmt(1)}") }
  }
}

@Composable
fun ErrorText(error: String?) {
  if (error != null)
    Text(
      error,
      color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun FoodRow(food: Food, onClick: () -> Unit, onFavourite: (() -> Unit)? = null) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Row(
      Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
      ) {
        Icon(
          Icons.Rounded.Restaurant,
          null,
          Modifier.padding(12.dp),
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }
      Column(Modifier.weight(1f)) {
        Text(food.name, style = MaterialTheme.typography.titleSmall)
        Text(
          food.brand.ifBlank { food.source },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          "${energy(food.nutrients.kcal)} / 100 ${food.basis}",
          style = MaterialTheme.typography.labelMedium,
        )
      }
      if (onFavourite != null)
        IconButton(onClick = onFavourite) {
          Icon(
            if (food.favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            "Favourite",
          )
        }
    }
  }
}
