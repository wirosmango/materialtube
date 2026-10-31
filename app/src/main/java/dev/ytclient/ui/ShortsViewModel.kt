package dev.ytclient.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ytclient.Graph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShortsUiState(
    val ids: List<String> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

class ShortsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ShortsUiState())
    val state: StateFlow<ShortsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = ShortsUiState(loading = true)
            try {
                _state.value = ShortsUiState(ids = Graph.innerTube.shorts())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = ShortsUiState(error = e.message ?: "Ошибка запроса")
            }
        }
    }

    /** Подгрузка: повторный запрос может вернуть новые ролики; уже известные отбрасываются. */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.ids.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val more = Graph.innerTube.shorts()
                _state.update { it.copy(loadingMore = false, ids = (it.ids + more).distinct()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }
}
