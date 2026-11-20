package com.tiffles.travelingtavern.ui.chats

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.RecentChat
import com.tiffles.travelingtavern.ui.components.CharacterAvatar
import com.tiffles.travelingtavern.ui.components.ConnectionBanner
import com.tiffles.travelingtavern.ui.components.EmptyState
import com.tiffles.travelingtavern.ui.components.label
import com.tiffles.travelingtavern.ui.components.relativeTime
import com.tiffles.travelingtavern.ui.navigation.OnTabReselected
import com.tiffles.travelingtavern.ui.navigation.TopLevelTab
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@Composable
fun ChatsScreen(
    onOpenChat: (characterId: String, chatFile: String?) -> Unit,
    onBrowseCharacters: () -> Unit,
    onOpenServerSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val now by rememberNow()

    OnTabReselected(TopLevelTab.CHATS) { listState.animateScrollToItem(0) }

    ChatsContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        now = now,
        onRefresh = viewModel::refresh,
        onChatClick = onOpenChat,
        onBrowseCharacters = onBrowseCharacters,
        onRetry = viewModel::retry,
        onOpenServerSettings = onOpenServerSettings,
        modifier = modifier,
        listState = listState
    )
}

/** The current time, updated every minute so "5m" becomes "6m" without a refresh. */
@Composable
private fun rememberNow() = produceState(initialValue = Instant.now()) {
    while (true) {
        delay(Duration.ofMinutes(1).toMillis())
        value = Instant.now()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsContent(
    uiState: ChatsUiState,
    isRefreshing: Boolean,
    now: Instant,
    onRefresh: () -> Unit,
    onChatClick: (characterId: String, chatFile: String?) -> Unit,
    onBrowseCharacters: () -> Unit,
    onRetry: () -> Unit,
    onOpenServerSettings: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    zone: ZoneId = ZoneId.systemDefault()
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_chats)) }) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (uiState) {
                ChatsUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is ChatsUiState.Error -> ConnectionBanner(
                    error = uiState.error,
                    onRetry = onRetry,
                    onOpenServerSettings = onOpenServerSettings,
                    modifier = Modifier.padding(16.dp)
                )
                ChatsUiState.Empty -> EmptyState(
                    message = stringResource(R.string.chats_empty),
                    actionLabel = stringResource(R.string.chats_browse_characters),
                    onAction = onBrowseCharacters
                )
                is ChatsUiState.Success -> PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh) {
                    ChatList(chats = uiState.chats, now = now, zone = zone, onChatClick = onChatClick, listState = listState)
                }
            }
        }
    }
}

@Composable
private fun ChatList(
    chats: List<RecentChat>,
    now: Instant,
    zone: ZoneId,
    onChatClick: (characterId: String, chatFile: String?) -> Unit,
    listState: LazyListState
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // A character can have several chats, so the key is the pair (the position if the file is unknown).
        itemsIndexed(chats, key = { index, chat -> chat.character.id + "/" + (chat.chatFile ?: "#$index") }) { _, chat ->
            ChatRow(chat = chat, now = now, zone = zone, onClick = { onChatClick(chat.character.id, chat.chatFile) })
        }
    }
}

@Composable
private fun ChatRow(chat: RecentChat, now: Instant, zone: ZoneId, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CharacterAvatar(imageUrl = chat.character.avatarUrl, name = chat.character.name)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.character.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val preview = remember(chat.lastMessage) { chat.lastMessage?.toPreviewLine() }
            if (!preview.isNullOrEmpty()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        chat.lastMessageAt?.let { at ->
            Text(
                text = relativeTime(at, now, zone).label(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One line of plain text: roleplay emphasis markers dropped, line breaks folded. */
private fun String.toPreviewLine(): String = replace("*", "").replace(WHITESPACE, " ").trim()

private val WHITESPACE = Regex("\\s+")

private val previewNow: Instant = Instant.parse("2026-11-17T18:00:00Z")

private val previewChats = listOf(
    RecentChat(
        character = CharacterSummary("Seraphina.png", "Seraphina", "", "", null),
        lastMessage = "*She smiles softly.* Welcome back. How was your day?",
        lastMessageAt = previewNow.minusSeconds(300)
    ),
    RecentChat(
        character = CharacterSummary("Bob.png", "Bob", "", "", null),
        lastMessage = "\"Test? Haha, did my messages not go through or something?\"",
        lastMessageAt = previewNow.minus(Duration.ofHours(26))
    ),
    RecentChat(
        character = CharacterSummary("Oliver.png", "Oliver", "", "", null),
        lastMessage = null,
        lastMessageAt = previewNow.minus(Duration.ofDays(20))
    )
)

@Preview(name = "Chats · light", showBackground = true)
@Preview(name = "Chats · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsContentSuccessPreview() {
    TravelingTavernTheme {
        ChatsContent(
            uiState = ChatsUiState.Success(previewChats), isRefreshing = false, now = previewNow,
            onRefresh = {}, onChatClick = { _, _ -> }, onBrowseCharacters = {}, onRetry = {}, onOpenServerSettings = {},
            zone = ZoneId.of("UTC")
        )
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun ChatsContentEmptyPreview() {
    TravelingTavernTheme {
        ChatsContent(
            uiState = ChatsUiState.Empty, isRefreshing = false, now = previewNow,
            onRefresh = {}, onChatClick = { _, _ -> }, onBrowseCharacters = {}, onRetry = {}, onOpenServerSettings = {}
        )
    }
}

@Preview(name = "Unreachable", showBackground = true)
@Composable
private fun ChatsContentErrorPreview() {
    TravelingTavernTheme {
        ChatsContent(
            uiState = ChatsUiState.Error(PageError.Unreachable), isRefreshing = false, now = previewNow,
            onRefresh = {}, onChatClick = { _, _ -> }, onBrowseCharacters = {}, onRetry = {}, onOpenServerSettings = {}
        )
    }
}

@Preview(name = "Loading", showBackground = true)
@Composable
private fun ChatsContentLoadingPreview() {
    TravelingTavernTheme {
        ChatsContent(
            uiState = ChatsUiState.Loading, isRefreshing = false, now = previewNow,
            onRefresh = {}, onChatClick = { _, _ -> }, onBrowseCharacters = {}, onRetry = {}, onOpenServerSettings = {}
        )
    }
}
