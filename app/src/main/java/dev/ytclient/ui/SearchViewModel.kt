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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val items: List<VideoItem> = emptyList(),
    val continuation: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
)

class SearchViewModel : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun submit() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update {
                it.copy(loading = true, loadingMore = false, error = null, items = emptyList(), continuation = null)
            }
            try {
                val page = Graph.innerTube.search(query)
                _state.update {
                    it.copy(loading = false, searched = true, items = page.items, continuation = page.continuation)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, searched = true, error = e.message ?: "Ошибка запроса") }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val token = current.continuation ?: return
        if (current.loading || current.loadingMore) return

        searchJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val page = Graph.innerTube.searchMore(token)
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
                // Ошибку подгрузки не показываем на весь экран: список остаётся, токен сохраняется.
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }
}
