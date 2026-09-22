package be.calorietracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import be.calorietracker.data.*
import be.calorietracker.domain.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.encodeToString

@Composable
fun CoachScreen(vm: TrackerViewModel, s: AppState) {
  var text by rememberSaveable { mutableStateOf("") }
  val attachments = remember { mutableStateListOf<String>() }
  val busy by vm.coach.busy.collectAsStateWithLifecycle()
  val streaming by vm.coach.streaming.collectAsStateWithLifecycle()
  val usage by vm.coach.usage.collectAsStateWithLifecycle()
  val phase by vm.coach.phase.collectAsStateWithLifecycle()
  var selected by remember { mutableStateOf<Proposal?>(null) }
  var delete by remember { mutableStateOf<Message?>(null) }
  var query by remember { mutableStateOf("") }
  var showSearch by remember { mutableStateOf(false) }
  var showInfo by remember { mutableStateOf(false) }
  val clipboard = LocalClipboardManager.current
  val visibleMessages =
    s.messages.filter { it.kind == "message" && (query.isBlank() || it.text.contains(query, true)) }
  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri != null) vm.run { attachments += vm.store.importPhoto(uri, "chat").id }
    }
  Column(Modifier.fillMaxSize()) {
    LazyColumn(
      Modifier.weight(1f).testTag("coach-list"),
      contentPadding = PaddingValues(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        PageTitle("One conversation, your whole journey", "Your coach") {
          IconButton(onClick = { showInfo = true }) { Icon(Icons.Rounded.Info, "Conversation information") }
          IconButton(onClick = { showSearch = !showSearch }) {
            Icon(Icons.Rounded.Search, "Search conversation")
          }
        }
      }
      if (showSearch) item { Field("Search saved messages", query, { query = it }) }
      if (s.messages.none { it.kind == "message" })
        item {
          EmptyState(
            "Let's find what works for you",
            "Ask about your food diary, explore your weight trend, or share a meal photo. Plan changes always need your approval.",
          )
          Spacer(Modifier.height(12.dp))
          listOf(
              if (s.weeklyCheckIns.any { it.status == "completed" }) "Explain my latest weekly check-in" else "How did I eat this week?",
              if (s.health.any { it.workouts.isNotEmpty() }) "How does my recent activity fit my goal?" else "Help me understand my weight trend",
              "What is one practical focus for today?",
            )
            .forEach { prompt ->
              OutlinedButton(onClick = { text = prompt }, modifier = Modifier.fillMaxWidth()) {
                Text(prompt)
              }
            }
        }
      itemsIndexed(
        visibleMessages,
        key = { _, item -> item.id },
      ) { index, m ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          val day = messageDay(m)
          if (index == 0 || messageDay(visibleMessages[index - 1]) != day)
            Text(day, Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Surface(
            shape = RoundedCornerShape(24.dp),
            color = if (m.role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
          ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text(if (m.role == "user") "You" else "Coach", style = MaterialTheme.typography.labelSmall)
              m.photoIds.forEach { LocalPhoto(vm, it, Modifier.fillMaxWidth().height(180.dp)) }
              if (m.role == "assistant") MarkdownText(m.text) else Text(m.text)
              Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(messageTime(m), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                IconButton({ clipboard.setText(AnnotatedString(m.text)) }, Modifier.size(32.dp)) {
                  Icon(Icons.Rounded.ContentCopy, "Copy message", Modifier.size(18.dp))
                }
                IconButton(onClick = { delete = m }, modifier = Modifier.size(32.dp)) {
                  Icon(Icons.Rounded.DeleteOutline, "Delete message", Modifier.size(18.dp))
                }
              }
            }
          }
          if (m.role == "assistant" && m.contextKind == "weekly_check_in") {
            Panel(tint = MaterialTheme.colorScheme.tertiaryContainer) {
              Text("Weekly insight", style = MaterialTheme.typography.titleMedium)
              Text("This explanation uses the saved weekly calculation. Any target change still needs your approval on Today.")
            }
          }
          if (m.requestId != null)
            s.proposals
              .filter {
                it.requestId == m.requestId && it.status in listOf("pending", "applied")
              }
              .forEach { p -> ProposalCard(vm, p) { selected = p } }
        }
      }
      items(s.proposals.filter { it.requestId == null && it.status in listOf("pending", "applied") }) { p -> ProposalCard(vm, p) { selected = p } }
      if (busy)
        item {
          Panel {
            Text(phase.ifBlank { "Thinking" }, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
            if (streaming.isNotBlank()) MarkdownText(streaming)
            else {
              Text("Your coach is reviewing the relevant context before replying.")
            }
          }
        }
      val lastUser = s.messages.lastOrNull { it.role == "user" && it.kind == "message" }
      if (
        !busy &&
          lastUser != null &&
          s.messages.none {
            it.role == "assistant" && it.kind == "message" && it.requestId == lastUser.requestId
          }
      )
        item {
          Panel {
            Text(
              s.messages
                .lastOrNull { it.kind == "error" && it.requestId == lastUser.requestId }
                ?.text ?: "This request has no completed response."
            )
            Button(onClick = { vm.retryCoach() }) { Text("Retry response") }
          }
        }
    }
    if (attachments.isNotEmpty())
      Row(Modifier.padding(horizontal = 20.dp)) {
        Text("${attachments.size} photo(s) attached", Modifier.weight(1f))
        TextButton(onClick = { attachments.clear() }) { Text("Remove") }
      }
    Row(
      Modifier.padding(12.dp).imePadding(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      IconButton(onClick = { picker.launch("image/*") }, enabled = !busy) {
        Icon(Icons.Rounded.AddPhotoAlternate, "Attach photo")
      }
      OutlinedTextField(
        text,
        { text = it },
        placeholder = { Text("Ask your coach…") },
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(24.dp),
        maxLines = 4,
      )
      if (busy)
        IconButton(onClick = { vm.cancelCoach() }) { Icon(Icons.Rounded.Stop, "Stop response") }
      else
        FilledIconButton(
          onClick = {
            vm.send(text, attachments.toList())
            text = ""
            attachments.clear()
          },
          enabled = text.isNotBlank() || attachments.isNotEmpty(),
        ) {
          Icon(Icons.Rounded.ArrowUpward, "Send message")
        }
    }
  }
  if (showInfo)
    AlertDialog(
      onDismissRequest = { showInfo = false },
      title = { Text("Conversation information") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("${s.messages.count { it.kind == "message" }} saved messages")
          Text("${s.summaries.size} encrypted context summaries; original messages remain searchable.")
          Text(if (usage.isBlank()) "No token usage available for this session." else "Last request usage: $usage")
          Text("DeepSeek Flash uses high-effort thinking. Private reasoning is never shown in the transcript.")
        }
      },
      confirmButton = { TextButton({ showInfo = false }) { Text("Done") } },
    )
  delete?.let { m ->
    AlertDialog(
      onDismissRequest = { delete = null },
      title = { Text("Delete this message?") },
      text = {
        Text(
          "It will be removed from local history and summaries. This cannot retract content previously sent to DeepSeek."
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            vm.run { vm.store.deleteMessage(m.id) }
            delete = null
          }
        ) {
          Text("Delete")
        }
      },
      dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } },
    )
  }
  selected?.let { p ->
    when (p.type) {
      "plan" ->
        PlanEditor(
          codec.decodeFromString(p.payload),
          profile = s.profile,
          onDismiss = { selected = null },
          onSave = { v ->
            vm.run {
              vm.store.update { s ->
                s.copy(
                  proposals =
                    s.proposals.map {
                      if (it.id == p.id) it.copy(payload = codec.encodeToString(v)) else it
                    }
                )
              }
            }
            selected = null
          },
        )
      "entry" -> {
        val e = codec.decodeFromString<Entry>(p.payload)
        PortionEditor(
          e.food,
          e.date,
          s.meals,
          e,
          { selected = null },
          { v ->
            vm.run {
              vm.store.update { st ->
                st.copy(
                  proposals =
                    st.proposals.map {
                      if (it.id == p.id) it.copy(payload = codec.encodeToString(v)) else it
                    }
                )
              }
            }
            selected = null
          },
        )
      }
      "recipe" ->
        RecipeEditor(vm, codec.decodeFromString(p.payload), { selected = null }) { v ->
          vm.run {
            vm.store.update { s ->
              s.copy(
                proposals =
                  s.proposals.map {
                    if (it.id == p.id) it.copy(payload = codec.encodeToString(v)) else it
                  }
              )
            }
          }
          selected = null
        }
    }
  }
}

@Composable
private fun ProposalCard(vm: TrackerViewModel, proposal: Proposal, onEdit: () -> Unit) {
  Panel(
    tint =
      if (proposal.status == "applied") MaterialTheme.colorScheme.secondaryContainer
      else MaterialTheme.colorScheme.primaryContainer
  ) {
    Text(
      if (proposal.status == "applied") "Applied change" else "Ready for your review",
      style = MaterialTheme.typography.titleMedium,
    )
    Text(proposalDescription(proposal))
    if (proposal.status == "pending") {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.run { vm.store.applyProposal(proposal.id) } }) { Text("Apply") }
        OutlinedButton(onClick = onEdit) { Text("Edit") }
        TextButton(
          onClick = {
            vm.run {
              vm.store.update { state ->
                state.copy(
                  proposals =
                    state.proposals.map {
                      if (it.id == proposal.id) it.copy(status = "dismissed") else it
                    }
                )
              }
            }
          }
        ) {
          Text("Dismiss")
        }
      }
    } else {
      TextButton(onClick = { vm.run { vm.store.undoProposal(proposal.id) } }) { Text("Undo") }
    }
  }
}

private fun messageDay(message: Message): String =
  runCatching {
      DateTimeFormatter.ofPattern("EEEE, d MMMM")
        .format(Instant.parse(message.timestamp).atZone(ZoneId.systemDefault()))
    }
    .getOrDefault("Saved conversation")

private fun messageTime(message: Message): String =
  runCatching {
      DateTimeFormatter.ofPattern("HH:mm")
        .format(Instant.parse(message.timestamp).atZone(ZoneId.systemDefault()))
    }
    .getOrDefault("")

private fun proposalDescription(p: Proposal): String =
  try {
    when (p.type) {
      "plan" ->
        codec.decodeFromString<Plan>(p.payload).let {
          "${it.kcal.fmt()} kcal · protein ${it.protein.fmt()} g · carbs ${it.carbs.fmt()} g · fat ${it.fat.fmt()} g\nEffective ${it.effective}"
        }
      "entry" ->
        codec.decodeFromString<Entry>(p.payload).let {
          "${it.food.name}\n${it.amount.fmt(1)} ${it.unit} · ${it.nutrients.kcal.fmt()} kcal\n${it.meal} · ${it.date}"
        }
      "recipe" ->
        codec.decodeFromString<Recipe>(p.payload).let {
          "${it.name} · ${it.ingredients.size} ingredients · ${it.batchGrams.fmt()} g batch"
        }
      else -> "Unsupported proposal"
    }
  } catch (_: Exception) {
    "Invalid proposal"
  }
