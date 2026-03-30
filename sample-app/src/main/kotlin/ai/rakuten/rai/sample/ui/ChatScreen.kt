@file:OptIn(ExperimentalMaterial3Api::class)

package ai.rakuten.rai.sample.ui

import ai.rakuten.android.viewmodel.RaiAgentState
import ai.rakuten.rai.sample.ChatMessage
import ai.rakuten.rai.sample.ChatViewModel
import ai.rakuten.rai.sample.MessageRole
import ai.rakuten.rai.sample.ModelOption
import ai.rakuten.rai.sample.ToolOption
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val EaseInCubic  = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit,
) {
    val state          by viewModel.state.collectAsStateWithLifecycle()
    val messages       by viewModel.messages.collectAsStateWithLifecycle()
    val selectedModel  by viewModel.selectedModel.collectAsStateWithLifecycle()
    val enabledTools   by viewModel.enabledTools.collectAsStateWithLifecycle()

    val isRunning = state is RaiAgentState.Running || state is RaiAgentState.Streaming
    val listState = rememberLazyListState()
    var inputText       by remember { mutableStateOf("") }
    var isPanelExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.SmartToy,
                            contentDescription = null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("RAI Chat")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            // ── Messages ──────────────────────────────────────────────────────
            LazyColumn(
                modifier        = Modifier.weight(1f).fillMaxWidth(),
                state           = listState,
                contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isEmpty()) {
                    item { EmptyStateHint(Modifier.fillParentMaxSize()) }
                }
                itemsIndexed(messages, key = { i, _ -> i }) { _, message ->
                    ChatBubble(message)
                }
                if (state is RaiAgentState.Running) {
                    item { TypingIndicator() }
                }
            }

            // ── Model & tools panel ───────────────────────────────────────────
            AnimatedVisibility(
                visible = isPanelExpanded,
                enter   = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec  = tween(300, easing = EaseOutCubic),
                ) + fadeIn(tween(200)),
                exit    = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(250, easing = EaseInCubic),
                ) + fadeOut(tween(150)),
            ) {
                ModelToolsPanel(
                    models        = viewModel.availableModels,
                    tools         = viewModel.availableTools,
                    selectedModel = selectedModel,
                    enabledTools  = enabledTools,
                    onModelSelect = viewModel::selectModel,
                    onToolToggle  = viewModel::toggleTool,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Input bar ─────────────────────────────────────────────────────
            ChatInputBar(
                text             = inputText,
                onTextChange     = { inputText = it },
                isRunning        = isRunning,
                isPanelExpanded  = isPanelExpanded,
                selectedModelName = selectedModel.displayName,
                enabledToolCount = enabledTools.size,
                onPanelToggle    = { isPanelExpanded = !isPanelExpanded },
                onSend           = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText.trim())
                        inputText = ""
                        isPanelExpanded = false
                    }
                },
                onStop = viewModel::cancel,
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyStateHint(modifier: Modifier = Modifier) {
    Column(
        modifier                = modifier,
        verticalArrangement     = Arrangement.Center,
        horizontalAlignment     = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.SmartToy,
            contentDescription = null,
            tint     = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Start a conversation",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Pick a model and tools below, then type a message.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

// ── Chat bubble ───────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(message: ChatMessage, modifier: Modifier = Modifier) {
    val isUser = message.role == MessageRole.User
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp,  bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp,  topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    Row(
        modifier            = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment   = Alignment.Bottom,
    ) {
        if (!isUser) {
            Box(
                modifier        = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Surface(
            shape    = bubbleShape,
            color    = if (isUser) MaterialTheme.colorScheme.primaryContainer
                       else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            if (message.text.isEmpty() && !isUser) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    BlinkingCursor()
                }
            } else {
                Text(
                    text     = message.text,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        if (isUser) Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun BlinkingCursor() {
    val alpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label        = "cursor_alpha",
    )
    Box(
        modifier = Modifier
            .size(width = 2.dp, height = 16.dp)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)),
    )
}

// ── Typing indicator ──────────────────────────────────────────────────────────

@Composable
private fun TypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val scale by transition.animateFloat(
                initialValue  = 0.5f,
                targetValue   = 1f,
                animationSpec = infiniteRepeatable(
                    animation   = tween(400, delayMillis = index * 130),
                    repeatMode  = RepeatMode.Reverse,
                ),
                label = "dot_$index",
            )
            Box(
                modifier = Modifier
                    .size((9 * scale).dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        CircleShape,
                    ),
            )
        }
    }
}

// ── Model & tools panel ───────────────────────────────────────────────────────

@Composable
private fun ModelToolsPanel(
    models: List<ModelOption>,
    tools: List<ToolOption>,
    selectedModel: ModelOption,
    enabledTools: Set<String>,
    onModelSelect: (ModelOption) -> Unit,
    onToolToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier       = modifier.fillMaxWidth(),
        color          = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier            = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Model",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(models) { model ->
                    FilterChip(
                        selected    = model == selectedModel,
                        onClick     = { onModelSelect(model) },
                        label       = { Text(model.displayName, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (model == selectedModel) ({
                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
                        }) else null,
                    )
                }
            }

            if (tools.isNotEmpty()) {
                Text(
                    "Tools",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tools) { tool ->
                        FilterChip(
                            selected    = tool.name in enabledTools,
                            onClick     = { onToolToggle(tool.name) },
                            label       = { Text(tool.displayName, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (tool.name in enabledTools) ({
                                Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
                            }) else null,
                        )
                    }
                }
            }
        }
    }
}

// ── Input bar ─────────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isRunning: Boolean,
    isPanelExpanded: Boolean,
    selectedModelName: String,
    enabledToolCount: Int,
    onPanelToggle: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // ── Model / tool toggle chips ─────────────────────────────────────────
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SuggestionChip(
                onClick = onPanelToggle,
                label   = {
                    Text(
                        selectedModelName,
                        style    = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
                icon = {
                    Icon(
                        if (isPanelExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            AnimatedVisibility(
                visible = enabledToolCount > 0,
                enter   = fadeIn() + scaleIn(spring(Spring.DampingRatioMediumBouncy)),
                exit    = fadeOut() + scaleOut(),
            ) {
                SuggestionChip(
                    onClick = onPanelToggle,
                    label   = {
                        Text(
                            "$enabledToolCount tool${if (enabledToolCount > 1) "s" else ""} on",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    icon = {
                        Icon(Icons.Filled.Tune, null, Modifier.size(16.dp))
                    },
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // ── Text field + send/stop button ─────────────────────────────────────
        Row(
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value       = text,
                onValueChange = onTextChange,
                modifier    = Modifier.weight(1f),
                placeholder = { Text("Message…") },
                maxLines    = 5,
                shape       = RoundedCornerShape(24.dp),
                colors      = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )

            AnimatedContent(
                targetState  = isRunning,
                transitionSpec = {
                    (scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) togetherWith
                            (scaleOut() + fadeOut())
                },
                label = "send_stop",
            ) { running ->
                if (running) {
                    FilledIconButton(
                        onClick = onStop,
                        colors  = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor   = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop")
                    }
                } else {
                    FilledIconButton(
                        onClick  = onSend,
                        enabled  = text.isNotBlank(),
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}
