package dev.ytclient.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ytclient.Graph
import dev.ytclient.innertube.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Список каналов, на которые подписан пользователь (ряд аватарок на вкладке «Подписки»). */
class SubscriptionsViewModel : ViewModel() {
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    init {
        viewModelScope.launch {
            Graph.auth.state.map { it.loggedIn }.distinctUntilChanged().collect { loggedIn ->
                if (!loggedIn) {
                    _channels.value = emptyList()
                } else {
                    try {
                        _channels.value = Graph.innerTube.subscriptionChannels()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Ряд каналов необязателен: лента подписок работает и без него.
                    }
                }
            }
        }
    }
}
