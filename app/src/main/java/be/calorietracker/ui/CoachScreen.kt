package be.calorietracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
  var showChats by remember { mutableStateOf(false) }
  var rename by remember { mutableStateOf(false) }
  var chatTitle by remember { mutableStateOf("") }
  val clipboard = LocalClipboardManager.current
  val context = LocalContext.current
  val conversationId = s.activeConversationId
  val activeTitle = s.conversations.firstOrNull { it.id == conversationId }?.title ?: "Your coach"
  val visibleMessages =
    s.messages.filter {
      it.conversationId == conversationId &&
        it.kind == "message" &&
        (query.isBlank() || it.text.contains(query, true))
    }
  val visibleProposals =
    s.proposals
      .filter {
        it.conversationId == conversationId && it.status in listOf("pending", "applied")
      }
      .distinctBy { it.id }
  val lastAssistantIndexByRequest =
    visibleMessages
      .mapIndexedNotNull { index, message ->
        message.requestId?.takeIf { message.role == "assistant" }?.let { it to index }
      }
      .toMap()
  val listState = rememberLazyListState()
  var cameraFile by remember { mutableStateOf<java.io.File?>(null) }
  var cameraUri by remember { mutableStateOf<Uri?>(null) }
  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
      if (uri != null) vm.run { attachments += vm.store.importPhoto(uri, "chat").id }
    }
  val camera =
    rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
      val uri = cameraUri
      val file = cameraFile
      if (saved && uri != null)
        vm.run {
          try {
            attachments += vm.store.importPhoto(uri, "chat").id
          } finally {
            file?.delete()
          }
        }
      else file?.delete()
      cameraFile = null
      cameraUri = null
    }
  fun openCamera() {
    val file = java.io.File.createTempFile("coach-photo-", ".jpg", context.cacheDir)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    cameraFile = file
    cameraUri = uri
    camera.launch(uri)
  }
  val cameraPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) openCamera()
    }
  LaunchedEffect(conversationId, visibleMessages.size, visibleProposals.size, busy, streaming.length) {
    if (query.isBlank()) {
      withFrameNanos { }
      val last = listState.layoutInfo.totalItemsCount - 1
      if (last >= 0) listState.scrollToItem(last)
    }
  }
  Column(Modifier.fillMaxSize()) {
    Column(Modifier.padding(horizontal = 20.dp)) {
      PageTitle(if (activeTitle == "Your coach") "New conversation" else activeTitle, "Coach") {
        IconButton(onClick = { showSearch = !showSearch }) {
          Icon(Icons.Rounded.Search, "Search conversation")
        }
        IconButton(onClick = { showChats = true }) { Icon(Icons.Rounded.History, "Chat history") }
        IconButton(onClick = { vm.run { vm.store.newConversation() } }, enabled = !busy) { Icon(Icons.Rounded.AddComment, "New chat") }
      }
    }
    LazyColumn(
      Modifier.weight(1f).testTag("coach-list"),
      state = listState,
      contentPadding = PaddingValues(20.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (showSearch) item { Field("Search saved messages", query, { query = it }) }
      if (visibleMessages.isEmpty() && query.isBlank())
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
            modifier = if (m.role == "user") Modifier.fillMaxWidth(.9f).align(Alignment.End) else Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(if (m.role == "user") 20.dp else 12.dp),
            color = if (m.role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,
          ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
              Text(if (m.role == "user") "You" else "Coach", style = MaterialTheme.typography.labelSmall)
              m.photoIds.forEach { LocalPhoto(vm, it, Modifier.fillMaxWidth().height(180.dp)) }
              if (m.role == "assistant") MarkdownText(m.text) else Text(m.text)
              Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(messageTime(m), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                IconButton({ clipboard.setText(AnnotatedString(m.text)) }, Modifier.size(48.dp)) {
                  Icon(Icons.Rounded.ContentCopy, "Copy message", Modifier.size(18.dp))
                }
                IconButton(onClick = { delete = m }, modifier = Modifier.size(48.dp)) {
                  Icon(Icons.Rounded.DeleteOutline, "Delete message", Modifier.size(18.dp))
                }
              }
            }
          }
          if (m.role == "assistant" && m.contextKind == "weekly_check_in") {
            Panel(tint = MaterialTheme.colorScheme.surfaceContainerLow) {
              Text("Weekly insight", style = MaterialTheme.typography.titleMedium)
              Text("Based on your saved weekly check-in.", style = MaterialTheme.typography.bodyMedium)
            }
          }
          if (
            m.requestId != null &&
              lastAssistantIndexByRequest[m.requestId] == index
          ) {
            val linked = visibleProposals.filter { it.requestId == m.requestId }
            if (linked.size > 1 && linked.all { it.type == "entry" })
              EntryProposalBatchCard(vm, linked) { selected = it }
            else linked.forEach { p -> ProposalCard(vm, p) { selected = p } }
          }
        }
      }
      items(visibleProposals.filter { it.requestId == null }, key = { it.id }) { p ->
        ProposalCard(vm, p) { selected = p }
      }
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
      val lastUser =
        s.messages.lastOrNull {
          it.conversationId == conversationId && it.role == "user" && it.kind == "message"
        }
      if (
        !busy &&
          lastUser != null &&
          s.messages.none {
            it.conversationId == conversationId &&
              it.role == "assistant" &&
              it.kind == "message" &&
              it.requestId == lastUser.requestId
          }
      )
        item {
          Panel {
            Text(
              s.messages
                .lastOrNull {
                  it.conversationId == conversationId &&
                    it.kind == "error" &&
                    it.requestId == lastUser.requestId
                }
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
        Icon(Icons.Rounded.AddPhotoAlternate, "Choose photo")
      }
      IconButton(
        onClick = {
          if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
              PackageManager.PERMISSION_GRANTED
          )
            openCamera()
          else cameraPermission.launch(Manifest.permission.CAMERA)
        },
        enabled = !busy,
      ) {
        Icon(Icons.Rounded.PhotoCamera, "Take photo")
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
  if (showChats) {
    val chats =
      buildList {
        if (s.activeConversationId == "default" || s.messages.any { it.conversationId == "default" })
          add(Conversation(id = "default", title = "First chat", created = s.messages.firstOrNull { it.conversationId == "default" }?.timestamp ?: now()))
        addAll(s.conversations)
      }.distinctBy { it.id }.sortedByDescending { it.created }
    Modal("Your chats", { showChats = false }) {
      Button(onClick = {
        vm.run { vm.store.newConversation() }
        showChats = false
      }, modifier = Modifier.fillMaxWidth()) { Text("New chat") }
      TextButton(onClick = { chatTitle = activeTitle; rename = true; showChats = false }) { Text("Rename current chat") }
      TextButton(onClick = { showInfo = true; showChats = false }) { Text("Conversation information") }
      chats.forEach { chat ->
        val first =
          s.messages.firstOrNull {
            it.conversationId == chat.id && it.role == "user" && it.kind == "message"
          }
        val title = first?.text?.trim()?.take(48)?.ifBlank { null } ?: chat.title
        Surface(
          onClick = {
            vm.run { vm.store.selectConversation(chat.id) }
            showChats = false
          },
          color =
            if (chat.id == conversationId) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
          shape = MaterialTheme.shapes.large,
        ) {
          Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(
              "${s.messages.count { it.conversationId == chat.id && it.kind == "message" }} messages",
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }
    }
  }
  if (showInfo)
    Modal("Conversation information", { showInfo = false }) {
      Text("${visibleMessages.size} saved messages in this chat")
      Text("${s.summaries.count { it.conversationId == conversationId }} encrypted context summaries; original messages remain searchable.")
      Text(if (usage.isBlank()) "No token usage available for this session." else "Last request usage: $usage")
      Text("DeepSeek Flash uses high-effort thinking. Private reasoning is never shown in the transcript.")
    }
  if (rename)
    Modal("Name this chat", { rename = false }) {
      Field("Chat title", chatTitle, { chatTitle = it })
      Button(onClick = {
        vm.run { vm.store.renameConversation(conversationId, chatTitle.trim()) }
        rename = false
      }, enabled = chatTitle.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save title") }
    }
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
private fun EntryProposalBatchCard(
  vm: TrackerViewModel,
  proposals: List<Proposal>,
  onEdit: (Proposal) -> Unit,
) {
  val pending = proposals.filter { it.status == "pending" }
  Panel(
    tint =
      if (pending.isEmpty()) MaterialTheme.colorScheme.secondaryContainer
      else MaterialTheme.colorScheme.primaryContainer
  ) {
    Text(
      if (pending.isEmpty()) "Meal added" else "Meal ready for your review",
      style = MaterialTheme.typography.titleMedium,
    )
    proposals.forEach { proposal ->
      val entry = runCatching { codec.decodeFromString<Entry>(proposal.payload) }.getOrNull()
      if (entry != null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(entry.food.name, style = MaterialTheme.typography.titleSmall)
            Text(
              "${entry.amount.fmt(1)} ${entry.unit} · ${entry.nutrients.kcal.fmt()} kcal",
              style = MaterialTheme.typography.bodySmall,
            )
          }
          if (proposal.status == "pending")
            TextButton(onClick = { onEdit(proposal) }) { Text("Edit") }
        }
      }
    }
    if (pending.isNotEmpty()) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.run { pending.forEach { vm.store.applyProposal(it.id) } } }) {
          Text("Add ${pending.size} items")
        }
        TextButton(
          onClick = {
            vm.run {
              vm.store.update { state ->
                state.copy(
                  proposals =
                    state.proposals.map {
                      if (it.id in pending.map { proposal -> proposal.id })
                        it.copy(status = "dismissed")
                      else it
                    }
                )
              }
            }
          }
        ) { Text("Dismiss") }
      }
    } else {
      TextButton(onClick = { vm.run { proposals.forEach { vm.store.undoProposal(it.id) } } }) {
        Text("Undo all")
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
