package com.kori.korichat

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kori.korichat.R
import com.kori.korichat.html.HtmlContent
import com.kori.korichat.ui.theme.button
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Keeps the last item pinned to the bottom as its content grows taller than the
 * viewport. `scrollToItem` only snaps the item's top edge into view, so once
 * that edge stops moving it goes silent even while the item keeps growing
 * downward off-screen — scroll by the overflow instead.
 */
private suspend fun scrollToBottomOf(listState: LazyListState) {
    val info = listState.layoutInfo
    val lastIndex = info.totalItemsCount - 1
    val lastItem = info.visibleItemsInfo.lastOrNull { it.index == lastIndex }
    if (lastItem == null) {
        listState.scrollToItem(lastIndex)
    } else {
        val overflow = (lastItem.offset + lastItem.size) - info.viewportEndOffset
        if (overflow > 0) listState.scrollBy(overflow.toFloat())
    }
}

private class ChatMessage(text: String, val isFromUser: Boolean, isStreaming: Boolean = false) {
    var text by mutableStateOf(text)
    var isStreaming by mutableStateOf(isStreaming)
    // how much of `text` has been typewriter-revealed so far; lives on the message
    // (not composable `remember`) so scrolling a finished bubble off/on screen doesn't replay it
    var revealedLength by mutableStateOf(if (isStreaming) 0 else text.length)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(chatViewModel: ChatViewModel = viewModel()) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        chatViewModel.startSession(
            onReady = { messages.add(ChatMessage(it, isFromUser = false)) },
            onError = { messages.add(ChatMessage(it, isFromUser = false)) }
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .imePadding(),
        containerColor = Color.White,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.ai_assistant),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White),
                scrollBehavior = scrollBehavior
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(id = R.string.chat_message_hint)) },
                    shape = RoundedCornerShape(24.dp)
                )
                IconButton(onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        messages.add(ChatMessage(text, isFromUser = true))
                        draft = ""
                        val replyBubble = ChatMessage("", isFromUser = false, isStreaming = true)
                        messages.add(replyBubble)
                        chatViewModel.sendMessageStreaming(
                            text = text,
                            onChunk = { replyBubble.text = it },
                            onDone = { replyBubble.isStreaming = false },
                            onError = {
                                replyBubble.text = it
                                replyBubble.isStreaming = false
                            }
                        )
                    }
                }) {
                    Icon(Icons.Default.Send, contentDescription = stringResource(id = R.string.chat_message_hint), tint = button)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(messages) { index, message ->
                ChatBubble(message, listState = listState, isLast = index == messages.lastIndex)
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, listState: LazyListState, isLast: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.animateContentSize(),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromUser) button else Color(0xFFF4F5F7)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (message.isFromUser) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(12.dp),
                    color = Color.White
                )
            } else {
                LaunchedEffect(message) {
                    while (isActive) {
                        val target = message.text
                        when {
                            message.revealedLength < target.length -> {
                                // reveal a few chars at a time so it visibly types out
                                // even though the whole reply already arrived
                                message.revealedLength = minOf(message.revealedLength + 2, target.length)
                                if (isLast) scrollToBottomOf(listState)
                                delay(15)
                            }
                            message.revealedLength > target.length -> message.revealedLength = target.length
                            !message.isStreaming -> break
                            else -> delay(15)
                        }
                    }
                }
                val displayedText = message.text.substring(0, minOf(message.revealedLength, message.text.length))
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Box {
                        HtmlContent(html = remember(displayedText) { markdownToHtml(displayedText) })
                    }
                    if (message.isStreaming || displayedText.length < message.text.length) {
                        StreamingCursor()
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingCursor() {
    val alpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )
    Box(
        modifier = Modifier
            .padding(start = 4.dp, bottom = 2.dp)
            .width(8.dp)
            .height(16.dp)
            .background(Color(0xFF666666).copy(alpha = alpha))
    )
}

private val markdownParser = Parser.builder().build()
private val htmlRenderer = HtmlRenderer.builder().build()

private fun markdownToHtml(markdown: String): String =
    htmlRenderer.render(markdownParser.parse(markdown))

@Preview
@Composable
private fun ChatScreenPreview() {
    ChatScreen()
}
