package dev.ytclient.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ytclient.Graph
import dev.ytclient.innertube.AccountInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Профиль вошедшего аккаунта; перезагружается при входе и сбрасывается при выходе. */
class ProfileViewModel : ViewModel() {
    private val _account = MutableStateFlow<AccountInfo?>(null)
    val account: StateFlow<AccountInfo?> = _account.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            Graph.auth.state.map { it.loggedIn }.distinctUntilChanged().collect { loggedIn ->
                if (loggedIn) load() else _account.value = null
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _account.value = Graph.innerTube.account()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Профиль необязателен: без него экран показывает «Вы вошли в аккаунт».
            }
            _loading.value = false
        }
    }
}
