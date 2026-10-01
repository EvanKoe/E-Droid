package app.edroid.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.edroid.R
import app.edroid.ui.components.AppCard
import app.edroid.ui.components.SectionTitle
import app.edroid.ui.theme.GroupedItemSpacing
import app.edroid.ui.theme.Layering
import app.edroid.ui.theme.groupedShapeAt

/**
 * Search tab: a search field, then either the browse view (recent searches + categories) or,
 * once the user types or picks a category, the matching apps.
 */
@Composable
fun SearchScreen(
    bottomPadding: Dp = 0.dp,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        containerColor = Layering.page,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Layering.page,
                    scrolledContainerColor = Layering.page,
                ),
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            SearchField(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = {
                    viewModel.commitSearch()
                    keyboard?.hide()
                },
                onClear = viewModel::clearQuery,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))

            val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp + bottomPadding)
            if (state.showResults) {
                ResultsList(state, listPadding, onClearCategory = { viewModel.onCategorySelected(null) })
            } else {
                BrowseList(
                    state = state,
                    contentPadding = listPadding,
                    onRecentSelected = viewModel::onRecentSelected,
                    onRemoveRecent = viewModel::onRemoveRecent,
                    onCategorySelected = viewModel::onCategorySelected,
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = Layering.item,
            unfocusedContainerColor = Layering.item,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

/** Recent searches (up to three) followed by every repository category, as grouped lists. */
@Composable
private fun BrowseList(
    state: SearchUiState,
    contentPadding: PaddingValues,
    onRecentSelected: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(GroupedItemSpacing),
    ) {
        if (state.recentSearches.isNotEmpty()) {
            item(key = "recent-title") { SectionTitle(stringResource(R.string.recent_searches)) }
            itemsIndexed(state.recentSearches, key = { _, q -> "recent:$q" }) { index, query ->
                RecentSearchRow(
                    query = query,
                    shape = groupedShapeAt(index, state.recentSearches.size),
                    onClick = { onRecentSelected(query) },
                    onRemove = { onRemoveRecent(query) },
                )
            }
            item(key = "recent-gap") { Spacer(Modifier.height(8.dp)) }
        }
        if (state.categories.isNotEmpty()) {
            item(key = "categories-title") { SectionTitle(stringResource(R.string.categories)) }
            itemsIndexed(state.categories, key = { _, c -> "cat:${c.first}" }) { index, (id, name) ->
                CategoryRow(
                    name = name,
                    shape = groupedShapeAt(index, state.categories.size),
                    onClick = { onCategorySelected(id) },
                )
            }
        } else if (!state.indexReady) {
            item(key = "loading") {
                Text(
                    stringResource(R.string.search_index_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun ResultsList(
    state: SearchUiState,
    contentPadding: PaddingValues,
    onClearCategory: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        state.selectedCategoryName?.let { name ->
            FilterChip(
                selected = true,
                onClick = onClearCategory,
                label = { Text(name) },
                trailingIcon = {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.action_clear), modifier = Modifier.width(18.dp))
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (state.results.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding()), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(if (state.indexReady) R.string.empty_results else R.string.search_index_loading),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return
        }
        LazyColumn(
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(GroupedItemSpacing),
        ) {
            itemsIndexed(state.results, key = { _, app -> app.packageName }) { index, app ->
                AppCard(app, shape = groupedShapeAt(index, state.results.size))
            }
        }
    }
}

@Composable
private fun RecentSearchRow(query: String, shape: Shape, onClick: () -> Unit, onRemove: () -> Unit) {
    GroupedRow(shape = shape, onClick = onClick) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            query,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Rounded.Clear,
                contentDescription = stringResource(R.string.action_remove_recent),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategoryRow(name: String, shape: Shape, onClick: () -> Unit) {
    GroupedRow(shape = shape, onClick = onClick) {
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A single-line grouped list row on the light `background` slab. */
@Composable
private fun GroupedRow(shape: Shape, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Layering.item),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
