package com.savoo.scclient.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.paging.CombinedLoadStates
import androidx.paging.ItemSnapshotList
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

private val IncompleteLoadState = LoadState.NotLoading(false)
private val InitialLoadStates = CombinedLoadStates(
    refresh = LoadState.Loading,
    prepend = IncompleteLoadState,
    append = IncompleteLoadState,
    source = LoadStates(LoadState.Loading, IncompleteLoadState, IncompleteLoadState),
)

class LazyPagingItems<T : Any> internal constructor(private val flow: Flow<PagingData<T>>) {
    private val presenter = object : PagingDataPresenter<T>(mainContext = Dispatchers.Main) {
        override suspend fun presentPagingDataEvent(event: PagingDataEvent<T>) {
            itemSnapshotList = snapshot()
        }
    }

    var itemSnapshotList: ItemSnapshotList<T> by mutableStateOf(presenter.snapshot())
        private set

    val itemCount: Int get() = itemSnapshotList.size

    var loadState: CombinedLoadStates by mutableStateOf(presenter.loadStateFlow.value ?: InitialLoadStates)
        private set

    operator fun get(index: Int): T? {
        presenter[index]
        return itemSnapshotList[index]
    }

    fun peek(index: Int): T? = itemSnapshotList[index]

    fun retry() = presenter.retry()

    fun refresh() = presenter.refresh()

    internal suspend fun collectLoadState() {
        presenter.loadStateFlow.filterNotNull().collect { loadState = it }
    }

    internal suspend fun collectPagingData() {
        flow.collectLatest { presenter.collectFrom(it) }
    }
}

@Composable
fun <T : Any> Flow<PagingData<T>>.collectAsLazyPagingItems(): LazyPagingItems<T> {
    val items = remember(this) { LazyPagingItems(this) }
    LaunchedEffect(items) { items.collectPagingData() }
    LaunchedEffect(items) { items.collectLoadState() }
    return items
}

private data class PagingPlaceholderKey(private val index: Int)

fun <T : Any> LazyPagingItems<T>.itemKey(key: ((item: T) -> Any)? = null): (index: Int) -> Any = { index ->
    val item = peek(index)
    if (key == null || item == null) PagingPlaceholderKey(index) else key(item)
}

fun <T : Any> LazyPagingItems<T>.itemContentType(contentType: ((item: T) -> Any?)? = null): (index: Int) -> Any? = { index ->
    val item = peek(index)
    if (contentType == null || item == null) null else contentType(item)
}

