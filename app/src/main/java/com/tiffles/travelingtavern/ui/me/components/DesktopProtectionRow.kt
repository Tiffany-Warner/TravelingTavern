package com.tiffles.travelingtavern.ui.me.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.DesktopProtection
import com.tiffles.travelingtavern.domain.model.ExtensionState
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * The Desktop protection row (A8): the extension's state from `leaseInfo`, an untested-version
 * line when it applies, and one action: Install, Update, or Get the update (the app's releases).
 * [protection] null: not known yet ("Checking SillyTavern…", no action).
 */
@Composable
fun DesktopProtectionRow(
    protection: DesktopProtection?,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onGetAppUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.protection_title), style = MaterialTheme.typography.titleMedium)
            Text(statusText(protection), style = MaterialTheme.typography.bodyLarge)
            detailText(protection)?.let { SecondaryText(it) }
            untestedText(protection)?.let { SecondaryText(it) }
            val action = action(protection, onInstall, onUpdate, onGetAppUpdate)
            if (action != null) {
                Button(
                    onClick = action.second,
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp).heightIn(min = 48.dp)
                ) {
                    Text(action.first)
                }
            }
        }
    }
}

@Composable
private fun SecondaryText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun statusText(protection: DesktopProtection?): String = stringResource(
    when (protection?.extension) {
        null -> R.string.protection_checking
        ExtensionState.NotInstalled -> R.string.protection_not_installed
        ExtensionState.UpdateAvailable -> R.string.protection_update_available
        ExtensionState.AppUpdateNeeded -> R.string.protection_update_app
        ExtensionState.Installed -> R.string.protection_installed
    }
)

@Composable
private fun detailText(protection: DesktopProtection?): String? = when (protection?.extension) {
    ExtensionState.NotInstalled -> stringResource(R.string.protection_not_installed_detail)
    ExtensionState.UpdateAvailable -> stringResource(R.string.protection_update_available_detail)
    ExtensionState.AppUpdateNeeded -> stringResource(R.string.protection_update_app_detail)
    ExtensionState.Installed, null -> null
}

@Composable
private fun untestedText(protection: DesktopProtection?): String? = when {
    protection == null || !protection.untestedServer -> null
    protection.stVersion != null -> stringResource(R.string.protection_untested, protection.stVersion)
    else -> stringResource(R.string.protection_untested_unknown)
}

@Composable
private fun action(
    protection: DesktopProtection?,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onGetAppUpdate: () -> Unit
): Pair<String, () -> Unit>? = when (protection?.extension) {
    ExtensionState.NotInstalled -> stringResource(R.string.protection_install) to onInstall
    ExtensionState.UpdateAvailable -> stringResource(R.string.protection_update) to onUpdate
    ExtensionState.AppUpdateNeeded -> stringResource(R.string.protection_get_app_update) to onGetAppUpdate
    ExtensionState.Installed, null -> null
}

@Preview(name = "Not installed", showBackground = true)
@Composable
private fun NotInstalledPreview() {
    TravelingTavernTheme {
        DesktopProtectionRow(DesktopProtection(ExtensionState.NotInstalled, untestedServer = false, stVersion = "1.19.1"), {}, {}, {})
    }
}

@Preview(name = "Installed, untested version", showBackground = true)
@Composable
private fun InstalledUntestedPreview() {
    TravelingTavernTheme {
        DesktopProtectionRow(DesktopProtection(ExtensionState.Installed, untestedServer = true, stVersion = "1.20.0"), {}, {}, {})
    }
}

@Preview(name = "Checking", showBackground = true)
@Composable
private fun CheckingPreview() {
    TravelingTavernTheme { DesktopProtectionRow(null, {}, {}, {}) }
}
