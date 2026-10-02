package app.edroid.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.edroid.EDroidApp
import app.edroid.R
import app.edroid.data.fdroid.model.App
import app.edroid.ui.components.AppCard
import app.edroid.ui.components.PageTitle
import app.edroid.ui.theme.GroupedItemSpacing
import app.edroid.ui.theme.Layering
import app.edroid.ui.theme.PageHorizontalPadding
import app.edroid.ui.theme.groupedShapeAt

/**
 * Every app in one repository category, opened from the Search tab's category grid. Like the
 * detail page it is drawn over the tab by `MainScreen`, which swaps the nav bar for a back bar.
 *
 * @param name shown as the heading until the index is loaded (and as a fallback if the repository
 *   doesn't name [categoryId]).
 * @param contentPadding insets handed down by `MainScreen` so content clears both scrims.
 */
@Composable
fun CategoryScreen(
    categoryId: String,
    name: String,
    contentPadding: PaddingValues,
    onAppClick: (App) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { (context.applicationContext as EDroidApp).container.fdroidRepository }
    val snapshot by repository.snapshot.collectAsStateWithLifecycle()
    val apps = remember(snapshot, categoryId) {
        snapshot?.apps.orEmpty().filter { categoryId in it.categories }
    }
    val title = snapshot?.repo?.categories?.get(categoryId) ?: name

    if (apps.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Layering.page)
                .padding(
                    start = PageHorizontalPadding,
                    end = PageHorizontalPadding,
                    top = contentPadding.calculateTopPadding(),
                ),
        ) {
            PageTitle(text = title)
            Box(
                Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(if (snapshot != null) R.string.empty_results else R.string.search_index_loading),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        return
    }

    // The top inset goes on the list rather than around it, so the heading and apps scroll up
    // under `MainScreen`'s top scrim and fade out there, like every tab, instead of being clipped.
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Layering.page)
            .padding(horizontal = PageHorizontalPadding),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(GroupedItemSpacing),
    ) {
        item(key = "title") {
            Column {
                PageTitle(text = title)
                Spacer(Modifier.height(8.dp))
            }
        }
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            AppCard(app, shape = groupedShapeAt(index, apps.size), onClick = { onAppClick(app) })
        }
    }
}
