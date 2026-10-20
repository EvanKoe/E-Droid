package app.edroid.ui.search

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import app.edroid.ui.theme.groupedShapeAt

/**
 * Search tab: page heading, a search field, then either the browse view (recent searches +
 * categories) or, once the user types, the matching apps. With several repos enabled, a row of
 * chips under the field narrows the results to one of them.
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
    val focusManager = LocalFocusManager.current
    val fieldFocus = remember { FocusRequester() }

    LaunchedEffect(focusRequest) {
        if (focusRequest > 0) {
            fieldFocus.requestFocus()
            keyboard?.show()
        }
    }

    // While a query is entered, back clears it (returning to the browse view) instead of leaving.
    BackHandler(enabled = state.query.isNotEmpty()) {
        viewModel.clearQuery()
        focusManager.clearFocus()
    }

    // The heading and field stay put; only the list below them scrolls, so the lists get the
    // bottom clearance while the column takes the top and side insets.
    val listPadding = PaddingValues(top = 12.dp, bottom = contentPadding.calculateBottomPadding())
    // Hoisted so the field scrim can follow whichever list is showing.
    val browseListState = rememberLazyListState()
    val resultsListState = rememberLazyListState()
    val activeListState = if (state.showResults) resultsListState else browseListState
    val scrimHeightPx = with(LocalDensity.current) { FieldScrimHeight.toPx() }

    // A different repo is a different list; start it from the top.
    LaunchedEffect(state.selectedRepoId) { resultsListState.scrollToItem(0) }

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
        if (state.showResults && state.repos.size > 1) {
            RepoFilterRow(
                repos = state.repos,
                selectedId = state.selectedRepoId,
                onSelected = viewModel::onRepoSelected,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

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
internal fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes hint: Int = R.string.search_hint,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(hint)) },
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

/** "All" followed by one chip per enabled repo; at most one is selected. */
@Composable
private fun RepoFilterRow(
    repos: List<Pair<String, String>>,
    selectedId: String?,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") {
            RepoChip(stringResource(R.string.search_all_repos), selected = selectedId == null, onClick = { onSelected(null) })
        }
        items(repos, key = { it.first }) { (id, name) ->
            RepoChip(name, selected = id == selectedId, onClick = { onSelected(id) })
        }
    }
}

@Composable
private fun RepoChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        shape = MaterialTheme.shapes.large,
        colors = FilterChipDefaults.filterChipColors(containerColor = Layering.item),
        border = null,
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
            items(categoryBlocks(state.categories), key = { "cat:${it.categories.first().first}" }) { block ->
                // The list spaces items for grouped rows; the bento grid wants a wider gutter.
                CategoryBlockRow(
                    block = block,
                    onCategorySelected = onCategorySelected,
                    modifier = Modifier.padding(top = CategoryGap - GroupedItemSpacing),
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

private val CategoryGap: Dp = 8.dp
private val CategoryTileHeight: Dp = 96.dp
private val CategoryTallTileHeight: Dp = CategoryTileHeight * 2 + CategoryGap

/**
 * One row of the bento category grid. Blocks cycle wide banner → tall tile on the start with two
 * small tiles beside it → the mirror of that, so seven categories make up one repeat.
 */
private sealed interface CategoryBlock {
    val categories: List<Pair<String, String>>

    data class Wide(val category: Pair<String, String>) : CategoryBlock {
        override val categories get() = listOf(category)
    }

    /** [categories] in row-by-row reading order, so the tall tile is first at the start, second at the end. */
    data class Split(override val categories: List<Pair<String, String>>, val tallAtStart: Boolean) : CategoryBlock

    /** Leftover pair at the end of the list, too few for a split block. */
    data class Duo(override val categories: List<Pair<String, String>>) : CategoryBlock
}

private fun categoryBlocks(categories: List<Pair<String, String>>): List<CategoryBlock> {
    val blocks = mutableListOf<CategoryBlock>()
    var i = 0
    while (i < categories.size) {
        val remaining = categories.size - i
        val block = when {
            blocks.size % 3 == 0 || remaining == 1 -> CategoryBlock.Wide(categories[i])
            remaining == 2 -> CategoryBlock.Duo(categories.subList(i, i + 2))
            else -> CategoryBlock.Split(categories.subList(i, i + 3), tallAtStart = blocks.size % 3 == 1)
        }
        blocks += block
        i += block.categories.size
    }
    return blocks
}

@Composable
private fun CategoryBlockRow(
    block: CategoryBlock,
    onCategorySelected: (id: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (block) {
        is CategoryBlock.Wide -> {
            val (id, name) = block.category
            CategoryBanner(id, name, onClick = { onCategorySelected(id, name) }, modifier = modifier)
        }
        is CategoryBlock.Duo -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(CategoryGap)) {
            block.categories.forEach { (id, name) ->
                CategorySmallTile(id, name, onClick = { onCategorySelected(id, name) }, modifier = Modifier.weight(1f))
            }
        }
        is CategoryBlock.Split -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(CategoryGap)) {
            val tall = if (block.tallAtStart) block.categories.first() else block.categories[1]
            val smalls = block.categories - tall
            if (block.tallAtStart) {
                CategoryTallTile(tall.first, tall.second, onClick = { onCategorySelected(tall.first, tall.second) }, modifier = Modifier.weight(1f))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CategoryGap)) {
                smalls.forEach { (id, name) ->
                    CategorySmallTile(id, name, onClick = { onCategorySelected(id, name) })
                }
            }
            if (!block.tallAtStart) {
                CategoryTallTile(tall.first, tall.second, onClick = { onCategorySelected(tall.first, tall.second) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Pastel container, saturated accent for the shape badge, and a readable tint of it for text. */
@Immutable
private class CategoryTileColors(val container: Color, val accent: Color, val content: Color)

@Composable
private fun categoryTileColors(id: String): CategoryTileColors {
    val accent = categoryColor(id)
    val page = Layering.page
    val dark = page.luminance() < 0.5f
    return CategoryTileColors(
        container = lerp(accent, page, if (dark) 0.72f else 0.8f),
        accent = accent,
        content = if (dark) lerp(accent, Color.White, 0.75f) else lerp(accent, Color.Black, 0.55f),
    )
}

@Composable
private fun CategoryCard(
    colors: CategoryTileColors,
    onClick: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(height),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.content),
    ) {
        Box(Modifier.fillMaxSize(), content = content)
    }
}

/** Icon on an expressive shape badge. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryBadge(id: String, color: Color, size: Dp, iconSize: Dp, modifier: Modifier = Modifier) {
    val shape = categoryIconShape(id).toShape()
    Box(modifier.size(size).background(color, shape), contentAlignment = Alignment.Center) {
        Icon(categoryIcon(id), contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

/** Full-width banner: name on the start, a large wavy accent shape bleeding off the end. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CategoryBanner(id: String, name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = categoryTileColors(id)
    val shape = categoryBannerShape(id).toShape()
    CategoryCard(colors, onClick, CategoryTileHeight, modifier) {
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 80.dp)
                .requiredSize(220.dp)
                .background(colors.accent, shape),
        )
        Icon(
            categoryIcon(id),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 50.dp).size(40.dp),
        )
        Text(
            name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 22.dp, end = 160.dp, top = 12.dp),
        )
    }
}

/** Double-height tile: big badge at the top, name anchored to the bottom. */
@Composable
private fun CategoryTallTile(id: String, name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = categoryTileColors(id)
    CategoryCard(colors, onClick, CategoryTallTileHeight, modifier) {
        CategoryBadge(id, colors.accent, size = 72.dp, iconSize = 34.dp, modifier = Modifier.padding(20.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
        )
    }
}

/** Single-height tile: small badge then the name, side by side. */
@Composable
private fun CategorySmallTile(id: String, name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = categoryTileColors(id)
    CategoryCard(colors, onClick, CategoryTileHeight, modifier) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(id, colors.accent, size = 48.dp, iconSize = 24.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
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
