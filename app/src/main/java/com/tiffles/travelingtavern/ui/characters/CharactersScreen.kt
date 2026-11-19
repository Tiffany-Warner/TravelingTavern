package com.tiffles.travelingtavern.ui.characters

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.ui.characters.components.AddCharacterSheet
import com.tiffles.travelingtavern.ui.components.AppBarSearchField
import com.tiffles.travelingtavern.ui.components.CharacterImage
import com.tiffles.travelingtavern.ui.components.ConnectionBanner
import com.tiffles.travelingtavern.ui.components.EmptyState
import com.tiffles.travelingtavern.ui.navigation.OnTabReselected
import com.tiffles.travelingtavern.ui.navigation.TopLevelTab
import com.tiffles.travelingtavern.ui.theme.OnImageScrim
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

@Composable
fun CharactersScreen(
    onOpenChat: (characterId: String) -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenServerSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CharactersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    OnTabReselected(TopLevelTab.CHARACTERS) { gridState.animateScrollToItem(0) }

    CharactersContent(
        uiState = uiState,
        query = query,
        onQueryChange = viewModel::setQuery,
        onCharacterClick = onOpenChat,
        onOpenAdvanced = onOpenAdvanced,
        onRetry = viewModel::retry,
        onOpenServerSettings = onOpenServerSettings,
        modifier = modifier,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        gridState = gridState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharactersContent(
    uiState: CharactersUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onCharacterClick: (characterId: String) -> Unit,
    onOpenAdvanced: () -> Unit,
    onRetry: () -> Unit,
    onOpenServerSettings: () -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    gridState: LazyGridState = rememberLazyGridState()
) {
    // Saveable so the open search survives tab switches and rotation, like the query does.
    var searchOpen by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    var addSheetOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CharactersTopBar(
                searchOpen = searchOpen,
                query = query,
                onQueryChange = onQueryChange,
                onOpenSearch = { searchOpen = true },
                onCloseSearch = {
                    searchOpen = false
                    onQueryChange("")
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { addSheetOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.characters_add))
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            CharactersBody(
                uiState = uiState,
                onCharacterClick = onCharacterClick,
                onOpenAdvanced = onOpenAdvanced,
                onRetry = onRetry,
                onOpenServerSettings = onOpenServerSettings,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                gridState = gridState
            )
        }
    }

    if (addSheetOpen) AddCharacterSheet(onDismiss = { addSheetOpen = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharactersTopBar(
    searchOpen: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit
) {
    TopAppBar(
        title = {
            if (searchOpen) {
                AppBarSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    placeholder = stringResource(R.string.characters_search)
                )
            } else {
                Text(stringResource(R.string.tab_characters))
            }
        },
        actions = {
            if (searchOpen) {
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.characters_close_search))
                }
            } else {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.characters_search))
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CharactersBody(
    uiState: CharactersUiState,
    onCharacterClick: (characterId: String) -> Unit,
    onOpenAdvanced: () -> Unit,
    onRetry: () -> Unit,
    onOpenServerSettings: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    gridState: LazyGridState
) {
    when (uiState) {
        CharactersUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is CharactersUiState.Error -> ConnectionBanner(
            error = uiState.error,
            onRetry = onRetry,
            onOpenServerSettings = onOpenServerSettings,
            modifier = Modifier.padding(16.dp)
        )
        CharactersUiState.Empty -> EmptyState(
            message = stringResource(R.string.characters_empty),
            actionLabel = stringResource(R.string.characters_open_full),
            onAction = onOpenAdvanced
        )
        is CharactersUiState.Success -> if (uiState.characters.isEmpty()) {
            Text(
                text = stringResource(R.string.characters_no_matches, uiState.query.trim()),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        } else {
            PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                CharacterGrid(characters = uiState.characters, onCharacterClick = onCharacterClick, gridState = gridState)
            }
        }
    }
}

@Composable
private fun CharacterGrid(
    characters: List<CharacterSummary>,
    onCharacterClick: (characterId: String) -> Unit,
    gridState: LazyGridState
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        // Bottom padding keeps the last row clear of the FAB.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(characters, key = { it.id }) { character ->
            CharacterCard(character = character, onClick = { onCharacterClick(character.id) })
        }
    }
}

/**
 * A 3:4 portrait card. The fixed aspect ratio keeps the grid from jumping while images
 * load; the initial shows until then (or for good, if the image fails).
 */
@Composable
private fun CharacterCard(character: CharacterSummary, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CharacterImage(
            imageUrl = character.portraitUrl,
            name = character.name,
            initialStyle = MaterialTheme.typography.displayMedium,
            modifier = Modifier.fillMaxSize()
        )
        Text(
            text = character.name,
            style = MaterialTheme.typography.titleMedium,
            color = OnImageScrim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

private const val SCRIM_ALPHA = 0.5f

private val previewCharacters = listOf("Bob", "Oliver", "Seraphina", "Tide Pods").map {
    CharacterSummary(id = "$it.png", name = it, avatarUrl = "", portraitUrl = "", lastChatAt = null)
}

@Preview(name = "Characters · light", showBackground = true)
@Preview(name = "Characters · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CharactersContentSuccessPreview() {
    TravelingTavernTheme {
        CharactersContent(
            uiState = CharactersUiState.Success(previewCharacters, query = ""), query = "",
            onQueryChange = {}, onCharacterClick = {}, onOpenAdvanced = {}, onRetry = {}, onOpenServerSettings = {}
        )
    }
}

@Preview(name = "Search, no matches", showBackground = true)
@Composable
private fun CharactersContentNoMatchesPreview() {
    TravelingTavernTheme {
        CharactersContent(
            uiState = CharactersUiState.Success(emptyList(), query = "zed"), query = "zed",
            onQueryChange = {}, onCharacterClick = {}, onOpenAdvanced = {}, onRetry = {}, onOpenServerSettings = {}
        )
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun CharactersContentEmptyPreview() {
    TravelingTavernTheme {
        CharactersContent(
            uiState = CharactersUiState.Empty, query = "",
            onQueryChange = {}, onCharacterClick = {}, onOpenAdvanced = {}, onRetry = {}, onOpenServerSettings = {}
        )
    }
}

@Preview(name = "Login failed", showBackground = true)
@Composable
private fun CharactersContentErrorPreview() {
    TravelingTavernTheme {
        CharactersContent(
            uiState = CharactersUiState.Error(PageError.AuthFailed), query = "",
            onQueryChange = {}, onCharacterClick = {}, onOpenAdvanced = {}, onRetry = {}, onOpenServerSettings = {}
        )
    }
}
