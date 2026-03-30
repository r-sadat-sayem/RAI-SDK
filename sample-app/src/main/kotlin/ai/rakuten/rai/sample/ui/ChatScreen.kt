@file:OptIn(ExperimentalMaterial3Api::class)

package ai.rakuten.rai.sample.ui

import ai.rakuten.android.viewmodel.RaiAgentState
import ai.rakuten.rai.sample.AspectRatio
import ai.rakuten.rai.sample.ChatMessage
import ai.rakuten.rai.sample.ChatViewModel
import ai.rakuten.rai.sample.MessageRole
import ai.rakuten.rai.sample.ModelOption
import ai.rakuten.rai.sample.ToolOption
import ai.rakuten.rai.sample.ui.format.MarkdownText
import ai.rakuten.rai.sample.ui.format.MessageContent
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val EaseInCubic  = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToSettings: () -> Unit,
) {
    val state              by viewModel.state.collectAsStateWithLifecycle()
    val messages           by viewModel.messages.collectAsStateWithLifecycle()
    val selectedModel      by viewModel.selectedModel.collectAsStateWithLifecycle()
    val enabledTools       by viewModel.enabledTools.collectAsStateWithLifecycle()
    val isImageMode        by viewModel.isImageMode.collectAsStateWithLifecycle()
    val selectedAspectRatio by viewModel.selectedAspectRatio.collectAsStateWithLifecycle()

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
                    item { EmptyStateHint(isImageMode, Modifier.fillParentMaxSize()) }
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
                    models               = viewModel.availableModels,
                    tools                = viewModel.availableTools,
                    selectedModel        = selectedModel,
                    enabledTools         = enabledTools,
                    isImageMode          = isImageMode,
                    selectedAspectRatio  = selectedAspectRatio,
                    onModelSelect        = viewModel::selectModel,
                    onToolToggle         = viewModel::toggleTool,
                    onImageModeToggle    = viewModel::toggleImageMode,
                    onAspectRatioSelect  = viewModel::selectAspectRatio,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Input bar ─────────────────────────────────────────────────────
            ChatInputBar(
                text              = inputText,
                onTextChange      = { inputText = it },
                isRunning         = isRunning,
                isPanelExpanded   = isPanelExpanded,
                isImageMode       = isImageMode,
                selectedModelName = selectedModel.displayName,
                enabledToolCount  = enabledTools.size,
                onPanelToggle     = { isPanelExpanded = !isPanelExpanded },
                onSend            = {
                    if (inputText.isNotBlank()) {
                        viewModel.send(inputText.trim())
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
private fun EmptyStateHint(isImageMode: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier                = modifier,
        verticalArrangement     = Arrangement.Center,
        horizontalAlignment     = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (isImageMode) Icons.Filled.AutoAwesome else Icons.Filled.SmartToy,
            contentDescription = null,
            tint     = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (isImageMode) "Generate an image" else "Start a conversation",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (isImageMode)
                "Describe the image you'd like to create."
            else
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
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment     = Alignment.Bottom,
    ) {
        if (!isUser) {
            Box(
                modifier         = Modifier
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

        when (val content = message.content) {
            is MessageContent.GeneratedImage -> ImageBubble(content, bubbleShape)
            else -> TextBubble(message, bubbleShape, isUser)
        }

        if (isUser) Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun TextBubble(message: ChatMessage, shape: RoundedCornerShape, isUser: Boolean) {
    Surface(
        shape    = shape,
        color    = if (isUser) MaterialTheme.colorScheme.primaryContainer
                   else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.widthIn(max = 300.dp),
    ) {
        when (val content = message.content) {
            is MessageContent.PlainText -> {
                if (content.value.isEmpty() && !isUser) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { BlinkingCursor() }
                } else {
                    Text(
                        text     = content.value,
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            is MessageContent.Markdown -> {
                if (content.value.isEmpty() && !isUser) {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { BlinkingCursor() }
                } else {
                    MarkdownText(
                        text     = content.value,
                        color    = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            is MessageContent.Code -> {
                CodeBubbleContent(content)
            }
            is MessageContent.GeneratedImage -> { /* handled by ImageBubble above */ }
        }
    }
}

@Composable
private fun CodeBubbleContent(content: MessageContent.Code) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (content.language.isNotBlank()) {
            Text(
                text  = content.language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text       = content.code,
            style      = MaterialTheme.typography.bodySmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImageBubble(content: MessageContent.GeneratedImage, shape: RoundedCornerShape) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    Column(horizontalAlignment = Alignment.End) {
        Surface(
            shape    = shape,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(content.bytes)
                    .crossfade(true)
                    .build(),
                contentDescription = "Generated image",
                contentScale       = ContentScale.Fit,
                modifier           = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(shape),
            )
        }

        Spacer(Modifier.height(4.dp))

        // Save to gallery button
        SuggestionChip(
            onClick = {
                scope.launch {
                    val saved = saveImageToGallery(context, content.bytes, content.mimeType)
                    val msg = if (saved) "Image saved to gallery" else "Failed to save image"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            label = { Text("Save", style = MaterialTheme.typography.labelSmall) },
            icon  = { Icon(Icons.Filled.Download, null, Modifier.size(14.dp)) },
        )
    }
}

@Composable
private fun BlinkingCursor() {
    val alpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue  = 1f,
        targetValue   = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label         = "cursor_alpha",
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
    isImageMode: Boolean,
    selectedAspectRatio: AspectRatio,
    onModelSelect: (ModelOption) -> Unit,
    onToolToggle: (String) -> Unit,
    onImageModeToggle: () -> Unit,
    onAspectRatioSelect: (AspectRatio) -> Unit,
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
            // ── Mode toggle (Chat / Image) ─────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected    = !isImageMode,
                    onClick     = { if (isImageMode) onImageModeToggle() },
                    label       = { Text("Chat", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (!isImageMode) ({
                        Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
                    }) else null,
                )
                FilterChip(
                    selected    = isImageMode,
                    onClick     = { if (!isImageMode) onImageModeToggle() },
                    label       = { Text("Image", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (isImageMode) ({
                        Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
                    }) else ({
                        Icon(Icons.Filled.Image, null, Modifier.size(16.dp))
                    }),
                )
            }

            if (isImageMode) {
                // ── Aspect ratio selector ─────────────────────────────────────
                Text(
                    "Aspect Ratio",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AspectRatio.entries) { ratio ->
                        FilterChip(
                            selected    = ratio == selectedAspectRatio,
                            onClick     = { onAspectRatioSelect(ratio) },
                            label       = { Text(ratio.label, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (ratio == selectedAspectRatio) ({
                                Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
                            }) else null,
                        )
                    }
                }
            } else {
                // ── Text model selector ───────────────────────────────────────
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

                // ── Tools selector ────────────────────────────────────────────
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
}

// ── Input bar ─────────────────────────────────────────────────────────────────

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isRunning: Boolean,
    isPanelExpanded: Boolean,
    isImageMode: Boolean,
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
        // ── Mode + model chips ────────────────────────────────────────────────
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SuggestionChip(
                onClick = onPanelToggle,
                label = {
                    Text(
                        if (isImageMode) "Image Mode" else selectedModelName,
                        style    = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                },
                icon = {
                    Icon(
                        if (isImageMode) Icons.Filled.AutoAwesome
                        else if (isPanelExpanded) Icons.Filled.ExpandLess
                        else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            AnimatedVisibility(
                visible = enabledToolCount > 0 && !isImageMode,
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
                    icon = { Icon(Icons.Filled.Tune, null, Modifier.size(16.dp)) },
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
                value         = text,
                onValueChange = onTextChange,
                modifier      = Modifier.weight(1f),
                placeholder   = {
                    Text(if (isImageMode) "Describe an image…" else "Message…")
                },
                maxLines = 5,
                shape    = RoundedCornerShape(24.dp),
                colors   = OutlinedTextFieldDefaults.colors(
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
                        onClick = onSend,
                        enabled = text.isNotBlank(),
                    ) {
                        Icon(
                            if (isImageMode) Icons.Filled.AutoAwesome else Icons.Filled.Send,
                            contentDescription = if (isImageMode) "Generate" else "Send",
                        )
                    }
                }
            }
        }
    }
}

// ── Image save helper ─────────────────────────────────────────────────────────

/**
 * Saves [imageBytes] to the device's Pictures gallery.
 *
 * Uses [MediaStore] (no `WRITE_EXTERNAL_STORAGE` permission required on API 29+).
 * On API 26–28 the permission is needed and should be requested before calling this.
 *
 * @return `true` if the image was saved successfully.
 */
private suspend fun saveImageToGallery(
    context: Context,
    imageBytes: ByteArray,
    mimeType: String,
): Boolean = withContext(Dispatchers.IO) {
    try {
        val extension = when {
            mimeType.contains("png",  ignoreCase = true) -> "png"
            mimeType.contains("webp", ignoreCase = true) -> "webp"
            else -> "jpg"
        }
        val filename = "rai_image_${System.currentTimeMillis()}.$extension"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE,    mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RAI")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false

        resolver.openOutputStream(uri)?.use { it.write(imageBytes) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } catch (_: Exception) {
        false
    }
}
