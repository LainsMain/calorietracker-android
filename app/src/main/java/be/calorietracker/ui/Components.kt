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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import be.calorietracker.domain.*
import java.util.Locale
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun Double?.fmt(digits: Int = 0) =
  this?.let { String.format(Locale.getDefault(), "%.${digits}f", it) } ?: "—"

@Composable
fun PageTitle(eyebrow: String, title: String, action: (@Composable () -> Unit)? = null) {
  Column(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
  Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = tint) {
    Column(
      Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
      content = content,
    )
  }
}

@Composable
fun Section(title: String, action: String? = null, onAction: () -> Unit = {}) {
  Row(
    Modifier.fillMaxWidth().padding(top = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
    if (action != null) TextButton(onClick = onAction) { Text(action) }
  }
}

@Composable
fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
  Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (action != null) TextButton(onClick = onAction) { Text(action) }
  }
}

@Composable
fun FlatRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
  Column {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
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
        FilterChip(selected = it == selected, onClick = { onChoose(it) }, label = { Text(it) }, modifier = Modifier.heightIn(min = 48.dp))
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
    Surface(Modifier.fillMaxSize()) {
      Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onDismiss) { Icon(Icons.Rounded.ArrowBack, "Close") }
          Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        }
        content()
      }
    }
  }
}

@Composable
fun ActionModal(
  title: String,
  onDismiss: () -> Unit,
  action: @Composable () -> Unit,
  fullScreen: Boolean = true,
  content: @Composable ColumnScope.() -> Unit,
) {
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().padding(12.dp).fillMaxHeight(.9f), shape = RoundedCornerShape(if (fullScreen) 0.dp else 28.dp)) {
      Column(Modifier.imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 2)
          IconButton(onClick = onDismiss) { Icon(Icons.Rounded.ArrowBack, "Close") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { action() }
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
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Row(
      Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      FoodThumbnail(food, Modifier.size(52.dp))
      Column(Modifier.weight(1f)) {
        Text(food.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
        Text(
          food.brand.ifBlank { "Generic food" },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          "${energy(food.nutrients.kcal)} / 100 ${food.basis} · ${food.source.substringBefore(" · ")}",
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

private val thumbnailCache = android.util.LruCache<String, android.graphics.Bitmap>(80)

@Composable
fun FoodThumbnail(food: Food, modifier: Modifier = Modifier) {
  val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = food.imageUrl) {
    val url = food.imageUrl ?: return@produceState
    value = thumbnailCache.get(url) ?: withContext(Dispatchers.IO) {
      runCatching {
        val uri = URI(url)
        require(uri.scheme == "https" && (uri.host == "openfoodfacts.org" || uri.host.endsWith(".openfoodfacts.org")))
        val connection = uri.toURL().openConnection().apply {
          connectTimeout = 5000
          readTimeout = 5000
        }
        val bytes = connection.getInputStream().use { input ->
          val buffer = ByteArray(2_000_001)
          var count = 0
          while (count < buffer.size) {
            val n = input.read(buffer, count, buffer.size - count)
            if (n < 0) break
            count += n
          }
          require(count <= 2_000_000)
          buffer.copyOf(count)
        }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      }.getOrNull()
    }?.also { thumbnailCache.put(url, it) }
  }
  Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
    if (bitmap != null)
      Image(bitmap!!.asImageBitmap(), food.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Restaurant, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
      }
  }
}
