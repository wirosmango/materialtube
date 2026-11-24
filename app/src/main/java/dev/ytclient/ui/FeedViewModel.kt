package dev.ytclient.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ytclient.Graph
import dev.ytclient.innertube.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val items: List<VideoItem> = emptyList(),
    val continuation: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

/** Одна лента `browse`: главная, подписки или история. По одному экземпляру на каждый browseId. */
class FeedViewModel(
    private val browseId: String,
    private val requiresLogin: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Полки Shorts разбираем только на главной; показывать их или нет, решает экран по настройке. */
    private val includeShorts = browseId == "FEwhat_to_watch"

    init {
        // Лента перезагружается при входе и выходе из аккаунта (первый раз — сразу при создании).
        viewModelScope.launch {
            Graph.auth.state.map { it.loggedIn }.distinctUntilChanged().collect { load() }
        }
    }

    /** [keepItems] = true — обновление «потянуть вниз»: старый список остаётся на экране, пока грузится новый. */
    fun load(keepItems: Boolean = false) {
        job?.cancel()
        if (requiresLogin && !Graph.auth.current.loggedIn) {
            _state.value = FeedUiState(loaded = true)
            return
        }
        job = viewModelScope.launch {
            _state.update { if (keepItems) it.copy(loading = true, loadingMore = false, error = null) else FeedUiState(loading = true) }
            try {
                val page = Graph.innerTube.feed(browseId, includeShorts = includeShorts)
                _state.value = FeedUiState(items = page.items, continuation = page.continuation, loaded = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loaded = true, error = e.message ?: "Ошибка запроса") }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val token = current.continuation ?: return
        if (current.loading || current.loadingMore) return

        job = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = Graph.innerTube.feed(browseId, token, includeShorts)
                _state.update {
                    it.copy(
                        loadingMore = false,
                        items = (it.items + page.items).distinctBy { item -> item.id },
                        continuation = page.continuation,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }
}
