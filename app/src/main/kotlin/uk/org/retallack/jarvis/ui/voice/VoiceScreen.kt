package uk.org.retallack.jarvis.ui.voice

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun VoiceScreen(
    modifier: Modifier = Modifier,
    onEntityClick: ((String) -> Unit)? = null,
    viewModel: VoiceViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val mode by viewModel.mode.collectAsState()

    // Permission launcher for RECORD_AUDIO (needed for mic tap)
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            viewModel.onMicTap()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val hasRecordPermission = {
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val partialText by viewModel.partialText.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            MicFab(
                mode = mode,
                onClick = {
                    if (hasRecordPermission()) {
                        viewModel.onMicTap()
                    } else {
                        permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Chat history
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Tap the mic or say \"Hey Jarvis\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(messages, key = { it.id }) { message ->
                    ChatBubble(
                        message = message,
                        onEntityClick = onEntityClick,
                    )
                }
            }

            // Partial STT text display
            if (mode == VoiceUiMode.LISTENING && partialText.isNotBlank()) {
                Text(
                    text = partialText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // Status indicator
            when (mode) {
                VoiceUiMode.LISTENING -> StatusBar("Listening...")
                VoiceUiMode.PROCESSING -> StatusBar("Processing...")
                VoiceUiMode.SPEAKING -> StatusBar("Speaking...")
                VoiceUiMode.ERROR -> StatusBar("Error", isError = true)
                else -> {}
            }
        }
    }
}

@Composable
private fun MicFab(
    mode: VoiceUiMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = when (mode) {
            VoiceUiMode.LISTENING -> MaterialTheme.colorScheme.error
            VoiceUiMode.PROCESSING -> MaterialTheme.colorScheme.tertiary
            VoiceUiMode.SPEAKING -> MaterialTheme.colorScheme.secondary
            VoiceUiMode.ERROR -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        label = "fab_color",
    )

    // Pulse animation when listening
    val scale = if (mode == VoiceUiMode.LISTENING) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val animatedScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse_scale",
        )
        animatedScale
    } else {
        1f
    }

    LargeFloatingActionButton(
        onClick = onClick,
        modifier = modifier.scale(scale),
        shape = CircleShape,
        containerColor = containerColor,
    ) {
        Icon(
            imageVector = if (mode == VoiceUiMode.LISTENING) Icons.Filled.MicOff else Icons.Filled.Mic,
            contentDescription = if (mode == VoiceUiMode.LISTENING) "Stop listening" else "Start listening",
            modifier = Modifier.size(36.dp),
        )
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onEntityClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val bubbleColor = when {
        message.isError -> MaterialTheme.colorScheme.errorContainer
        message.isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = when {
        message.isError -> MaterialTheme.colorScheme.onErrorContainer
        message.isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val linkColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 16.dp,
            ),
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            if (!message.isUser && message.entityIds.isNotEmpty() && onEntityClick != null) {
                // Render text with tappable entity names
                val annotatedString = buildAnnotatedString {
                    var currentPos = 0
                    val text = message.text

                    // Find entity name positions in the text
                    val entityMatches = message.entityNames.mapIndexedNotNull { index, name ->
                        val start = text.indexOf(name, currentPos, ignoreCase = true)
                        if (start >= 0 && index < message.entityIds.size) {
                            Triple(start, start + name.length, message.entityIds[index])
                        } else {
                            null
                        }
                    }.sortedBy { it.first }

                    for (match in entityMatches) {
                        // Append text before entity name
                        if (match.first > currentPos) {
                            withStyle(SpanStyle(color = textColor)) {
                                append(text.substring(currentPos, match.first))
                            }
                        }
                        // Append entity name as clickable
                        pushStringAnnotation(tag = "entity", annotation = match.third)
                        withStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        ) {
                            append(text.substring(match.first, match.second))
                        }
                        pop()
                        currentPos = match.second
                    }

                    // Append remaining text
                    if (currentPos < text.length) {
                        withStyle(SpanStyle(color = textColor)) {
                            append(text.substring(currentPos))
                        }
                    }
                }

                ClickableText(
                    text = annotatedString,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(12.dp),
                    onClick = { offset ->
                        annotatedString.getStringAnnotations(
                            tag = "entity",
                            start = offset,
                            end = offset,
                        ).firstOrNull()?.let { annotation ->
                            onEntityClick(annotation.item)
                        }
                    },
                )
            } else {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBar(
    text: String,
    isError: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
