package app.edroid.ui.options

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.edroid.R
import app.edroid.ui.theme.Layering

/** Placeholder for app settings; intentionally empty for now. */
@Composable
fun OptionsScreen(bottomPadding: Dp = 0.dp) {
    Scaffold(
        containerColor = Layering.page,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.options_title)) },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Layering.page,
                    scrolledContainerColor = Layering.page,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .padding(bottom = bottomPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.options_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
