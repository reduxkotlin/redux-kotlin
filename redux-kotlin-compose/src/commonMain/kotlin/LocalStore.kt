package org.reduxkotlin.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import org.reduxkotlin.Dispatcher
import org.reduxkotlin.Store

@PublishedApi
internal val LocalStore: ProvidableCompositionLocal<Store<*>?> = compositionLocalOf { null }

@Composable
@PublishedApi
@Suppress("UNCHECKED_CAST")
internal inline fun <reified TState> getStore(): Store<TState> =
  LocalStore.current.runCatching { (this as Store<TState>) }.getOrElse {
    error("Store<${TState::class.simpleName}> not found in current composition scope")
  }

@Composable
inline fun <T : Any> withStore(store: Store<T>, crossinline content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalStore provides store) { content() }
}

@Composable
fun rememberDispatcher(): Dispatcher = getStore<Any>().dispatch

@Composable
inline fun <reified TState, TSlice> selectState(
  crossinline selector: @DisallowComposableCalls TState.() -> TSlice
): State<TSlice> {
  return getStore<TState>().selectState(selector)
}

@Composable
inline fun <TState, TSlice> Store<TState>.selectState(
  crossinline selector: @DisallowComposableCalls TState.() -> TSlice
): State<TSlice> {
  val result = remember { mutableStateOf(state.selector()) }
  DisposableEffect(result) {
    val unsubscribe = subscribe { result.value = state.selector() }
    onDispose(unsubscribe)
  }
  return result
}
