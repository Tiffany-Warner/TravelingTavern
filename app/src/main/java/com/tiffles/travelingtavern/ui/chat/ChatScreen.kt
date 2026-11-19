package com.tiffles.travelingtavern.ui.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.ui.components.CharacterAvatar
import com.tiffles.travelingtavern.ui.components.WebViewBackHandler
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme
import kotlinx.coroutines.delay

/**
 * Height of the Chat screen's own bar. The app shell moves the shared WebView down by this
 * much while Chat is showing, so the page sits exactly below the bar.
 */
val ChatChromeHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight

/**
 * One character's chat: a top bar over the shared SillyTavern WebView, which the app shell
 * draws behind this screen (the rest of this screen is transparent on purpose).
 */
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val voiceMessage by viewModel.voiceMessage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestMicPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onMicPermissionResult(it)
    }
    LaunchedEffect(viewModel) {
        viewModel.permissionRequests.collect { requestMicPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }
    // Never keep the mic open in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopListening() }
    WebViewBackHandler()
    ChatContent(
        uiState = uiState,
        voiceMessage = voiceMessage,
        onBack = onBack,
        onOpenAppSettings = { context.startActivity(appDetailsSettings(context)) },
        onVoiceMessageShown = viewModel::onVoiceMessageShown,
        modifier = modifier
    )
}

@Composable
fun ChatContent(
    uiState: ChatUiState,
    voiceMessage: VoiceMessage?,
    onBack: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onVoiceMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    val character = when (uiState) {
        is ChatUiState.Loading -> uiState.character
        is ChatUiState.Ready -> uiState.character
        is ChatUiState.Error -> null
    }
    val snackbarHostState = remember { SnackbarHostState() }
    VoiceMessageEffect(voiceMessage, snackbarHostState, onOpenAppSettings, onVoiceMessageShown)
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatTopBar(character = character, onBack = onBack)
            // While SillyTavern swaps chats it briefly shows an empty or half-drawn list; cover it.
            // Errors are shown by the WebView layer's own overlay underneath.
            if (uiState is ChatUiState.Loading) ChatLoadingCover()
        }
        // Above the keyboard when it's open; otherwise over SillyTavern's input row, briefly.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).imePadding()
        )
    }
}

/** Shows [message] once as a snackbar; PermissionDenied gets a Settings action. */
@Composable
private fun VoiceMessageEffect(
    message: VoiceMessage?,
    hostState: SnackbarHostState,
    onOpenAppSettings: () -> Unit,
    onShown: () -> Unit
) {
    if (message == null) return
    val text = stringResource(message.textRes())
    val settings = stringResource(R.string.chat_voice_settings)
    LaunchedEffect(message) {
        val result = hostState.showSnackbar(
            message = text,
            actionLabel = if (message == VoiceMessage.PermissionDenied) settings else null,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) onOpenAppSettings()
        onShown()
    }
}

private fun VoiceMessage.textRes(): Int = when (this) {
    VoiceMessage.PermissionDenied -> R.string.chat_voice_permission_denied
    VoiceMessage.LanguagePackMissing -> R.string.chat_voice_language_pack
    VoiceMessage.NoMatch -> R.string.chat_voice_no_match
    VoiceMessage.Failed -> R.string.chat_voice_failed
}

/** The system's page for this app, where the microphone permission can be turned back on. */
private fun appDetailsSettings(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

/** Opaque until the chat is drawn; the spinner only appears if that takes a while. */
@Composable
private fun ChatLoadingCover() {
    var showSpinner by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPINNER_DELAY_MS)
        showSpinner = true
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            if (showSpinner) CircularProgressIndicator()
        }
    }
}

private const val SPINNER_DELAY_MS = 800L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(character: CharacterSummary?, onBack: () -> Unit) {
    TopAppBar(
        expandedHeight = ChatChromeHeight,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
            }
        },
        title = {
            if (character != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CharacterAvatar(
                        imageUrl = character.avatarUrl,
                        name = character.name,
                        size = 32.dp,
                        initialStyle = MaterialTheme.typography.labelLarge
                    )
                    Text(text = character.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    )
}

@Preview(name = "Chat · light", showBackground = true)
@Preview(name = "Chat · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatContentPreview() {
    TravelingTavernTheme {
        ChatContent(
            uiState = ChatUiState.Ready(CharacterSummary("Seraphina.png", "Seraphina", "", "", null)),
            voiceMessage = null,
            onBack = {},
            onOpenAppSettings = {},
            onVoiceMessageShown = {}
        )
    }
}
