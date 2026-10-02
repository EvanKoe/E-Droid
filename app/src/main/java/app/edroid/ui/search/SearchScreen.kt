package app.edroid.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.edroid.R
import app.edroid.data.fdroid.model.App
import app.edroid.ui.components.AppCard
import app.edroid.ui.components.PageTitle
import app.edroid.ui.components.SectionTitle
import app.edroid.ui.theme.GroupedItemSpacing
import app.edroid.ui.theme.Layering
import app.edroid.ui.theme.PageHorizontalPadding
import app.edroid.ui.theme.gridGroupedShapeAt
import app.edroid.ui.theme.groupedShapeAt

/**
 * Search tab: page heading, a search field, then either the browse view (recent searches +
 * categories) or, once the user types, the matching apps.
 *
 * @param contentPadding insets handed down by `MainScreen` so content clears both scrims.
 * @param focusRequest a counter `MainScreen` bumps when the Search tab is re-tapped while already
 *   selected; every change (other than the initial 0) focuses the search field and opens the
 *   keyboard.
 * @param onAppClick opens an app's detail page.
 * @param onCategoryClick opens a category's page, given its id and display name.
 */
@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    focusRequest: Int = 0,
    onAppClick: (App) -> Unit,
    onCategoryClick: (id: String, name: String) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldFocus = remember { FocusRequester() }

    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) {
            fieldFocus.requestFocus()
            keyboard?.show()
        }
    }

    // The heading and field stay put; only the list below them scrolls, so the lists get the
    // bottom clearance while the column takes the top and side insets.
    val listPadding = PaddingValues(top = 12.dp, bottom = contentPadding.calculateBottomPadding())
    // Hoisted so the field scrim can follow whichever list is showing.
    val browseListState = rememberLazyListState()
    val resultsListState = rememberLazyListState()
    val activeListState = if (state.showResults) resultsListState else browseListState
    val scrimHeightPx = with(LocalDensity.current) { FieldScrimHeight.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = PageHorizontalPadding,
                end = PageHorizontalPadding,
                top = contentPadding.calculateTopPadding(),
            ),
    ) {
        PageTitle(text = stringResource(R.string.search_title))
        Spacer(Modifier.height(8.dp))
        SearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onSearch = {
                viewModel.commitSearch()
                keyboard?.hide()
            },
            onClear = viewModel::clearQuery,
            modifier = Modifier.focusRequester(fieldFocus),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.showResults) {
                ResultsList(state, resultsListState, listPadding, onAppClick = onAppClick)
            } else {
                BrowseList(
                    state = state,
                    listState = browseListState,
                    contentPadding = listPadding,
                    onRecentSelected = viewModel::onRecentSelected,
                    onRemoveRecent = viewModel::onRemoveRecent,
                    onCategorySelected = onCategoryClick,
                )
            }

            // Like `MainScreen`'s top scrim, but under the field: scrolled rows fade out beneath
            // it instead of being cut off at its edge. It is taller than the list's resting gap,
            // so it fades in over the first stretch of scroll rather than veiling the top row at
            // rest. Alpha is read in the draw phase, so scrolling doesn't recompose.
            val surface = Layering.page
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(FieldScrimHeight)
                    .graphicsLayer {
                        alpha = if (activeListState.firstVisibleItemIndex > 0) {
                            1f
                        } else {
                            (activeListState.firstVisibleItemScrollOffset / scrimHeightPx).coerceIn(0f, 1f)
                        }
                    }
                    .background(
                        Brush.verticalGradient(
                            0f to surface,
                            0.3f to surface.copy(alpha = 0.7f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
        }
    }
}

/** Height of the fade under the search field; matches `MainScreen`'s top scrim extent. */
private val FieldScrimHeight: Dp = 40.dp

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
    listState: LazyListState,
    contentPadding: PaddingValues,
    onRecentSelected: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onCategorySelected: (id: String, name: String) -> Unit,
) {
    LazyColumn(
        state = listState,
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
            val count = state.categories.size
            itemsIndexed(state.categories.chunked(CategoryColumns), key = { _, row -> "cat:${row.first().first}" }) { rowIndex, row ->
                val rowStart = rowIndex * CategoryColumns
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(GroupedItemSpacing),
                ) {
                    row.forEachIndexed { i, (id, name) ->
                        CategoryTile(
                            id = id,
                            name = name,
                            shape = gridGroupedShapeAt(rowStart + i, count, CategoryColumns),
                            onClick = { onCategorySelected(id, name) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    repeat(CategoryColumns - row.size) { Spacer(Modifier.weight(1f)) }
                }
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
    listState: LazyListState,
    contentPadding: PaddingValues,
    onAppClick: (App) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
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
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(GroupedItemSpacing),
        ) {
            itemsIndexed(state.results, key = { _, app -> app.packageName }) { index, app ->
                AppCard(app, shape = groupedShapeAt(index, state.results.size), onClick = { onAppClick(app) })
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

private const val CategoryColumns = 3

/** A grid cell: colored icon badge above the category name, centered. */
@Composable
private fun CategoryTile(id: String, name: String, shape: Shape, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Layering.item),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(categoryColor(id), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(categoryIcon(id), contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
