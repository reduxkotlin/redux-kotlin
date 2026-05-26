package com.github.jetbrains.rssreader.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.jetbrains.rssreader.Res
import com.github.jetbrains.rssreader.app.FeedStoreHolder
import com.github.jetbrains.rssreader.app.refresh
import com.github.jetbrains.rssreader.app_name
import com.github.jetbrains.rssreader.back_button
import com.github.jetbrains.rssreader.core.RssReader
import com.github.jetbrains.rssreader.feed_list
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

enum class Screen(val title: StringResource) {
    Main(Res.string.app_name), FeedList(Res.string.feed_list)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssFeedAppBar(
    currentScreen: Screen,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(stringResource(currentScreen.title)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.back_button)
                    )
                }
            }
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainScreen(
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val storeHolder: FeedStoreHolder = koinInject()
    val rssReader: RssReader = koinInject()
    val state by storeHolder.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(Unit) {
        storeHolder.store.dispatch(refresh(rssReader, forceLoad = false))
    }
    PullToRefreshBox(
        isRefreshing = state.progress,
        onRefresh = { storeHolder.store.dispatch(refresh(rssReader, forceLoad = true)) },
        modifier = modifier,
        content = {
            MainFeed(
                storeHolder = storeHolder,
                onPostClick = { post ->
                    post.link?.let { url ->
                        uriHandler.openUri(url)
                    }
                },
                onEditClick = onEditClick
            )
        }
    )
}

@Composable
fun FeedListScreen() {
    val storeHolder: FeedStoreHolder = koinInject()
    FeedList(storeHolder = storeHolder)
}
