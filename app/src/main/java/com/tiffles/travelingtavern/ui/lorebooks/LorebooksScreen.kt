package com.tiffles.travelingtavern.ui.lorebooks

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.LorebookCard
import com.tiffles.travelingtavern.domain.model.LorebookFilter
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.ui.components.AppBarSearchField
import com.tiffles.travelingtavern.ui.components.ConnectionBanner
import com.tiffles.travelingtavern.ui.components.EmptyState
import com.tiffles.travelingtavern.ui.lorebooks.components.CharacterFilterSheet
import com.tiffles.travelingtavern.ui.lorebooks.components.CreateLorebookSheet
import com.tiffles.travelingtavern.ui.lorebooks.components.LorebookFilterChips
import com.tiffles.travelingtavern.ui.lorebooks.components.LorebookGridCard
import com.tiffles.travelingtavern.ui.lorebooks.components.previewLorebook
import com.tiffles.travelingtavern.ui.navigation.OnTabReselected
import com.tiffles.travelingtavern.ui.navigation.TopLevelTab
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

@Composable
fun LorebooksScreen(
    onOpenLorebook: (name: String) -> Unit,
    onOpenServerSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LorebooksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val createState by viewModel.createState.collectAsStateWithLifecycle()
    var createSheetOpen by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    OnTabReselected(TopLevelTab.LOREBOOKS) { gridState.animateScrollToItem(0) }
    LaunchedEffect(viewModel) {
        viewModel.openLorebook.collect { name ->
            createSheetOpen = false
            onOpenLorebook(name)
        }
    }

    LorebooksContent(
        uiState = uiState,
        query = query,
        isRefreshing = isRefreshing,
        callbacks = LorebooksCallbacks(
            onQueryChange = viewModel::setQuery,
            onFilterChange = viewModel::setFilter,
            onClearFilters = viewModel::clearFilters,
            onLorebookClick = onOpenLorebook,
            onRefresh = viewModel::refresh,
            onRetry = viewModel::retry,
            onOpenServerSettings = onOpenServerSettings,
            onOpenCreate = {
                viewModel.startCreate()
                createSheetOpen = true
            }
        ),
        modifier = modifier,
        gridState = gridState
    )

    if (createSheetOpen) {
        CreateLorebookSheet(
            state = createState,
            onNameChange = viewModel::setCreateName,
            onCreate = viewModel::create,
            onDismiss = { createSheetOpen = false }
        )
    }
}

/** Everything the list can ask for, so [LorebooksContent] stays readable. */
data class LorebooksCallbacks(
    val onQueryChange: (String) -> Unit = {},
    val onFilterChange: (LorebookFilter) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onLorebookClick: (name: String) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenServerSettings: () -> Unit = {},
    val onOpenCreate: () -> Unit = {}
)

@Composable
fun LorebooksContent(
    uiState: LorebooksUiState,
    query: String,
    isRefreshing: Boolean,
    callbacks: LorebooksCallbacks,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState()
) {
    // Saveable so the open search survives tab switches and rotation, like the query does.
    var searchOpen by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    var characterSheetOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            LorebooksTopBar(
                searchOpen = searchOpen,
                query = query,
                onQueryChange = callbacks.onQueryChange,
                onOpenSearch = { searchOpen = true },
                onCloseSearch = {
                    searchOpen = false
                    callbacks.onQueryChange("")
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = callbacks.onOpenCreate) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.lorebooks_add))
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            LorebooksBody(
                uiState = uiState,
                isRefreshing = isRefreshing,
                callbacks = callbacks,
                onPickCharacter = { characterSheetOpen = true },
                gridState = gridState
            )
        }
    }

    val success = uiState as? LorebooksUiState.Success
    if (characterSheetOpen && success != null) {
        CharacterFilterSheet(
            characters = success.filterCharacters,
            onPick = {
                characterSheetOpen = false
                callbacks.onFilterChange(LorebookFilter.Character(it.id))
            },
            onDismiss = { characterSheetOpen = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LorebooksTopBar(
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
                    placeholder = stringResource(R.string.lorebooks_search_placeholder)
                )
            } else {
                Text(stringResource(R.string.tab_lorebooks))
            }
        },
        actions = {
            if (searchOpen) {
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.lorebooks_close_search))
                }
            } else {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.lorebooks_search))
                }
            }
        }
    )
}

@Composable
private fun LorebooksBody(
    uiState: LorebooksUiState,
    isRefreshing: Boolean,
    callbacks: LorebooksCallbacks,
    onPickCharacter: () -> Unit,
    gridState: LazyGridState
) {
    when (uiState) {
        LorebooksUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is LorebooksUiState.Error -> ConnectionBanner(
            error = uiState.error,
            onRetry = callbacks.onRetry,
            onOpenServerSettings = callbacks.onOpenServerSettings,
            modifier = Modifier.padding(16.dp)
        )
        LorebooksUiState.Empty -> EmptyState(
            message = stringResource(R.string.lorebooks_empty),
            actionLabel = stringResource(R.string.lorebooks_create),
            onAction = callbacks.onOpenCreate
        )
        is LorebooksUiState.Success -> Column(Modifier.fillMaxSize()) {
            LorebookFilterChips(
                filter = uiState.filter,
                selectedCharacter = uiState.selectedCharacter,
                showCharacterChip = uiState.filterCharacters.isNotEmpty(),
                onFilterChange = callbacks.onFilterChange,
                onPickCharacter = onPickCharacter
            )
            PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = callbacks.onRefresh, modifier = Modifier.fillMaxSize()) {
                if (uiState.cards.isEmpty()) {
                    NoMatches(onClearFilters = callbacks.onClearFilters)
                } else {
                    LorebookGrid(cards = uiState.cards, onLorebookClick = callbacks.onLorebookClick, gridState = gridState)
                }
            }
        }
    }
}

@Composable
private fun NoMatches(onClearFilters: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.lorebooks_no_matches),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = onClearFilters) { Text(stringResource(R.string.lorebooks_clear_filters)) }
    }
}

@Composable
private fun LorebookGrid(cards: List<LorebookCard>, onLorebookClick: (String) -> Unit, gridState: LazyGridState) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        // Bottom padding keeps the last row clear of the FAB.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(cards, key = { it.lorebook.name }) { card ->
            LorebookGridCard(card = card, onClick = { onLorebookClick(card.lorebook.name) })
        }
    }
}

private val previewCards = listOf(
    previewLorebook("Eldoria", characters = 1),
    previewLorebook("Kingdoms of the North", characters = 3),
    previewLorebook("House rules", global = true),
    previewLorebook("Me and my cat", persona = true),
    previewLorebook("Old notes")
)

@Preview(name = "Lorebooks · light", showBackground = true)
@Preview(name = "Lorebooks · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LorebooksContentSuccessPreview() {
    TravelingTavernTheme {
        LorebooksContent(
            uiState = LorebooksUiState.Success(
                cards = previewCards,
                filter = LorebookFilter.All,
                filterCharacters = previewCards[1].characters,
                selectedCharacter = null
            ),
            query = "",
            isRefreshing = false,
            callbacks = LorebooksCallbacks()
        )
    }
}

@Preview(name = "No matches", showBackground = true)
@Composable
private fun LorebooksContentNoMatchesPreview() {
    TravelingTavernTheme {
        LorebooksContent(
            uiState = LorebooksUiState.Success(emptyList(), LorebookFilter.Unused, emptyList(), null),
            query = "",
            isRefreshing = false,
            callbacks = LorebooksCallbacks()
        )
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun LorebooksContentEmptyPreview() {
    TravelingTavernTheme {
        LorebooksContent(uiState = LorebooksUiState.Empty, query = "", isRefreshing = false, callbacks = LorebooksCallbacks())
    }
}

@Preview(name = "Unreachable", showBackground = true)
@Composable
private fun LorebooksContentErrorPreview() {
    TravelingTavernTheme {
        LorebooksContent(
            uiState = LorebooksUiState.Error(PageError.Unreachable),
            query = "",
            isRefreshing = false,
            callbacks = LorebooksCallbacks()
        )
    }
}
