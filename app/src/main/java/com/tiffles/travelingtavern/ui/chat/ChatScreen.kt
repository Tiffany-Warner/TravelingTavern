package com.tiffles.travelingtavern.ui.chat

import android.content.ClipData
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.MessageAction
import com.tiffles.travelingtavern.ui.chat.components.ChatInputBar
import com.tiffles.travelingtavern.ui.chat.components.MessageActionsSheet
import com.tiffles.travelingtavern.ui.components.CharacterAvatar
import com.tiffles.travelingtavern.ui.components.ConfirmDeleteSheet
import com.tiffles.travelingtavern.ui.components.WebViewBackHandler
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Height of the Chat screen's own top bar. The app shell moves the shared WebView down by
 * this much, so the page sits exactly below the bar.
 */
val ChatChromeHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight

/**
 * The input bar's usual height before it has been measured. The shell keeps the WebView's
 * bottom above the bar, so the page never sits under it.
 */
val DefaultChatInputBarHeight: Dp = 120.dp

/** The callbacks [ChatContent] needs, grouped so previews and tests stay readable. */
data class ChatActions(
    val onBack: () -> Unit = {},
    val onInputChange: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onWriteForMe: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onMessageAction: (MessageAction) -> Unit = {},
    val onSheetShown: (coveredDp: Int) -> Unit = {},
    val onSheetDismissed: () -> Unit = {},
    val onStartNewChat: () -> Unit = {},
    val onRestartChat: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onConfirmationDismissed: () -> Unit = {},
    val onInputBarHeightChange: (Dp) -> Unit = {}
)

/**
 * One character's chat: a top bar and the native input bar around the shared SillyTavern
 * WebView, which the app shell draws behind this screen (the middle is transparent on purpose).
 */
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onInputBarHeightChange: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    ChatEventsEffect(viewModel, snackbarHostState)
    // System Back leaves the same way as the top bar's arrow (to the Chats tab when the chat was
    // opened by link). Registered first so the page's own Back, while it has history, wins.
    BackHandler(onBack = onBack)
    WebViewBackHandler()
    ChatContent(
        uiState = uiState,
        input = viewModel.input,
        actions = ChatActions(
            onBack = onBack,
            onInputChange = viewModel::onInputChange,
            onSend = viewModel::onSend,
            onWriteForMe = viewModel::onWriteForMe,
            onStop = viewModel::onStop,
            onMessageAction = viewModel::onMessageAction,
            onSheetShown = viewModel::onSheetShown,
            onSheetDismissed = viewModel::onSheetDismissed,
            onStartNewChat = viewModel::onStartNewChat,
            onRestartChat = viewModel::onRestartChat,
            onConfirm = viewModel::onConfirm,
            onConfirmationDismissed = viewModel::onConfirmationDismissed,
            onInputBarHeightChange = onInputBarHeightChange
        ),
        snackbarHostState = snackbarHostState,
        modifier = modifier
    )
}

/** Copies text for the Copy action and shows short notices. */
@Composable
private fun ChatEventsEffect(viewModel: ChatViewModel, snackbarHostState: SnackbarHostState) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.CopyText ->
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, event.text)))
                ChatEvent.MessageChanged ->
                    snackbarHostState.showSnackbar(context.getString(R.string.chat_message_changed))
                ChatEvent.ActionFailed ->
                    snackbarHostState.showSnackbar(context.getString(R.string.chat_action_failed))
            }
        }
    }
}

@Composable
fun ChatContent(
    uiState: ChatUiState,
    input: String,
    actions: ChatActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val ready = uiState as? ChatUiState.Ready
    val character = when (uiState) {
        is ChatUiState.Loading -> uiState.character
        is ChatUiState.Ready -> uiState.character
        is ChatUiState.Error -> null
    }
    // The page's bottom edge is the input bar's top (the shell keeps the WebView above the bar).
    var inputBarTop by remember { mutableFloatStateOf(Float.NaN) }
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatTopBar(
                character = character,
                menuEnabled = ready?.menuEnabled == true,
                onBack = actions.onBack,
                onStartNewChat = actions.onStartNewChat,
                onRestartChat = actions.onRestartChat
            )
            // While SillyTavern swaps chats it briefly shows an empty or half-drawn list; cover it.
            // Errors are shown by the WebView layer's own overlay underneath.
            if (uiState is ChatUiState.Loading) ChatLoadingCover(Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            if (uiState !is ChatUiState.Error) {
                MeasuredInputBar(ready = ready, input = input, actions = actions, onTopChange = { inputBarTop = it })
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).imePadding())
    }
    ready?.sheet?.let { sheet ->
        val density = LocalDensity.current
        MessageActionsSheet(
            sheet = sheet,
            onAction = actions.onMessageAction,
            onDismiss = actions.onSheetDismissed,
            onShown = { sheetTop ->
                if (!inputBarTop.isNaN()) {
                    val coveredPx = (inputBarTop - sheetTop).coerceAtLeast(0f)
                    actions.onSheetShown(with(density) { coveredPx.toDp().value.roundToInt() })
                }
            }
        )
    }
    ready?.confirmation?.let { ChatConfirmationSheet(it, actions) }
}

/**
 * The input bar above the keyboard, reporting its own height (without the keyboard) to the shell
 * and its top edge's screen Y to [onTopChange].
 */
@Composable
private fun MeasuredInputBar(
    ready: ChatUiState.Ready?,
    input: String,
    actions: ChatActions,
    onTopChange: (Float) -> Unit
) {
    val density = LocalDensity.current
    ChatInputBar(
        text = input,
        generating = ready?.generating == true,
        canSend = ready?.canSend(input) == true,
        onTextChange = actions.onInputChange,
        onSend = actions.onSend,
        onWriteForMe = actions.onWriteForMe,
        onStop = actions.onStop,
        modifier = Modifier
            .imePadding()
            .onSizeChanged { size -> actions.onInputBarHeightChange(with(density) { size.height.toDp() }) }
            .onGloballyPositioned { onTopChange(it.positionOnScreen().y) }
    )
}

@Composable
private fun ChatConfirmationSheet(confirmation: ChatConfirmation, actions: ChatActions) {
    when (confirmation) {
        ChatConfirmation.StartNewChat -> ConfirmDeleteSheet(
            title = stringResource(R.string.chat_start_new_title),
            message = stringResource(R.string.chat_start_new_message),
            confirmLabel = stringResource(R.string.chat_start_new_action),
            destructive = false,
            onConfirm = actions.onConfirm,
            onDismiss = actions.onConfirmationDismissed
        )
        ChatConfirmation.RestartChat -> ConfirmDeleteSheet(
            title = stringResource(R.string.chat_restart_title),
            message = stringResource(R.string.chat_restart_message),
            confirmLabel = stringResource(R.string.chat_restart_action),
            onConfirm = actions.onConfirm,
            onDismiss = actions.onConfirmationDismissed
        )
        is ChatConfirmation.DeleteMessage -> ConfirmDeleteSheet(
            title = stringResource(R.string.chat_delete_message_title),
            message = stringResource(R.string.chat_delete_message_text),
            onConfirm = actions.onConfirm,
            onDismiss = actions.onConfirmationDismissed
        )
    }
}

/** Opaque until the chat is drawn; the spinner only appears if that takes a while. */
@Composable
private fun ChatLoadingCover(modifier: Modifier = Modifier) {
    var showSpinner by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPINNER_DELAY_MS)
        showSpinner = true
    }
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            if (showSpinner) CircularProgressIndicator()
        }
    }
}

private const val SPINNER_DELAY_MS = 800L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    character: CharacterSummary?,
    menuEnabled: Boolean,
    onBack: () -> Unit,
    onStartNewChat: () -> Unit,
    onRestartChat: () -> Unit
) {
    TopAppBar(
        expandedHeight = ChatChromeHeight,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
            }
        },
        title = { if (character != null) ChatTitle(character) },
        actions = { ChatMenu(enabled = menuEnabled, onStartNewChat = onStartNewChat, onRestartChat = onRestartChat) }
    )
}

@Composable
private fun ChatTitle(character: CharacterSummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CharacterAvatar(
            imageUrl = character.avatarUrl,
            name = character.name,
            size = 32.dp,
            initialStyle = MaterialTheme.typography.labelLarge
        )
        Text(text = character.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** ⋮ with SillyTavern's Start new chat and Restart chat; both wait while a reply generates. */
@Composable
private fun ChatMenu(enabled: Boolean, onStartNewChat: () -> Unit, onRestartChat: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.chat_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_start_new)) },
                enabled = enabled,
                onClick = {
                    expanded = false
                    onStartNewChat()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.chat_restart)) },
                enabled = enabled,
                onClick = {
                    expanded = false
                    onRestartChat()
                }
            )
        }
    }
}

@Preview(name = "Chat · light", showBackground = true)
@Preview(name = "Chat · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatContentPreview() {
    TravelingTavernTheme {
        ChatContent(
            uiState = ChatUiState.Ready(CharacterSummary("Seraphina.png", "Seraphina", "", "", null)),
            input = "Hello",
            actions = ChatActions()
        )
    }
}

@Preview(name = "Chat · loading", showBackground = true)
@Composable
private fun ChatLoadingPreview() {
    TravelingTavernTheme {
        Box(modifier = Modifier.padding(0.dp)) {
            ChatContent(uiState = ChatUiState.Loading(), input = "", actions = ChatActions())
        }
    }
}
