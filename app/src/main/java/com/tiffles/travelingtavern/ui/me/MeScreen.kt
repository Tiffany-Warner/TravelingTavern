package com.tiffles.travelingtavern.ui.me

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.DesktopProtection
import com.tiffles.travelingtavern.domain.model.ExtensionState
import com.tiffles.travelingtavern.domain.model.ProjectLinks
import com.tiffles.travelingtavern.ui.me.components.DesktopProtectionRow
import com.tiffles.travelingtavern.ui.protection.components.ExtensionDialogs
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/** The Me tab. Until 1.8 builds Me, it holds only the Desktop protection section (A8). */
@Composable
fun MeScreen(modifier: Modifier = Modifier, viewModel: MeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    MeContent(
        uiState = uiState,
        onInstall = viewModel::install,
        onUpdate = viewModel::update,
        onGetAppUpdate = { context.openAppReleases() },
        onConfirm = viewModel::confirm,
        onUpdateInstead = viewModel::updateInstead,
        onDismissDialog = viewModel::dismissDialog,
        modifier = modifier
    )
}

/** Stateless: the Desktop protection row and, while one runs, the install / update dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeContent(
    uiState: MeUiState,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onGetAppUpdate: () -> Unit,
    onConfirm: () -> Unit,
    onUpdateInstead: () -> Unit,
    onDismissDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_me)) }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            DesktopProtectionRow(
                protection = uiState.protection,
                onInstall = onInstall,
                onUpdate = onUpdate,
                onGetAppUpdate = onGetAppUpdate
            )
        }
    }
    uiState.dialog?.let {
        ExtensionDialogs(dialog = it, onConfirm = onConfirm, onUpdateInstead = onUpdateInstead, onDismiss = onDismissDialog)
    }
}

/** The app's GitHub Releases page (none before 1.0.0) in the browser. */
private fun Context.openAppReleases() {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, ProjectLinks.APP_RELEASES_URL.toUri()))
    } catch (e: ActivityNotFoundException) {
        Log.w("MeScreen", "No app to open the releases page", e)
    }
}

@Preview(showBackground = true)
@Composable
private fun MeContentPreview() {
    TravelingTavernTheme {
        MeContent(
            uiState = MeUiState(protection = DesktopProtection(ExtensionState.UpdateAvailable, untestedServer = false, stVersion = "1.19.1")),
            onInstall = {},
            onUpdate = {},
            onGetAppUpdate = {},
            onConfirm = {},
            onUpdateInstead = {},
            onDismissDialog = {}
        )
    }
}
