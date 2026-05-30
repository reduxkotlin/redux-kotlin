package org.reduxkotlin.sample.taskflow.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.ktor.client.HttpClient
import org.reduxkotlin.sample.taskflow.platform.ktorEngineOrNull

/**
 * Installs the app-wide Coil [ImageLoader] singleton, wiring the platform Ktor engine
 * (via [ktorEngineOrNull]) into a [KtorNetworkFetcherFactory] so async images (avatars,
 * attachments, board covers) load over the network on every target. On wasmJs the engine
 * is `null` and Coil falls back to the service-loaded browser fetch engine.
 *
 * Call once at the root of the app's composition, before any composable reads an image —
 * Coil's [setSingletonImageLoaderFactory] is a read-only composable that must run ahead of
 * the first `SingletonImageLoader.get`.
 */
@Composable
@ReadOnlyComposable
public fun initCoil() {
    setSingletonImageLoaderFactory { ctx ->
        ImageLoader.Builder(ctx)
            .components {
                add(
                    KtorNetworkFetcherFactory(
                        httpClient = ktorEngineOrNull()?.let { HttpClient(it) } ?: HttpClient(),
                    ),
                )
            }
            .build()
    }
}

/**
 * A network-free [ImageLoader] for tests and previews. With no fetcher registered, every
 * remote request fails fast, so `AsyncImage` immediately surfaces its error/fallback path —
 * which in TaskFlow is the deterministic monogram drawn by `Avatar`. Pass the test's
 * [PlatformContext] (e.g. from `LocalPlatformContext.current`).
 */
public fun fakeNoNetworkImageLoader(ctx: PlatformContext): ImageLoader = ImageLoader.Builder(ctx).build()
