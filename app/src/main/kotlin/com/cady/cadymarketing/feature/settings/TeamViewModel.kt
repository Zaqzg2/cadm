package com.cady.cadymarketing.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.cloud.CloudErrors
import com.cady.cadymarketing.data.team.TeamException
import com.cady.cadymarketing.data.team.TeamRepository
import com.cady.cadymarketing.domain.model.TeamMember
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamUiState(
    val loading: Boolean = true,
    val members: List<TeamMember> = emptyList(),
    val error: String? = null,
    val adding: Boolean = false,
    val addError: String? = null,
    val busyUid: String? = null,
    /** The user name just added, so the screen can remind the manager to give it (with the password) to the marketer. */
    val justAdded: String? = null,
)

@HiltViewModel
class TeamViewModel @Inject constructor(
    private val team: TeamRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TeamUiState())
    val state: StateFlow<TeamUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val members = team.list()
                _state.update { it.copy(loading = false, members = members) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = CloudErrors.message(e)) }
            }
        }
    }

    /** Makes the account and lists it. [onDone] runs when it worked (the dialog closes). */
    fun add(name: String, username: String, password: String, onDone: () -> Unit) {
        if (_state.value.adding) return
        viewModelScope.launch {
            _state.update { it.copy(adding = true, addError = null) }
            try {
                val member = team.addMarketer(name, username, password)
                _state.update {
                    it.copy(
                        adding = false,
                        justAdded = member.username,
                        members = (it.members.filter { m -> m.uid != member.uid } + member).sortedBy { m -> m.name },
                    )
                }
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: TeamException) {
                _state.update { it.copy(adding = false, addError = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(adding = false, addError = CloudErrors.message(e)) }
            }
        }
    }

    fun setActive(member: TeamMember, active: Boolean) {
        if (_state.value.busyUid != null) return
        viewModelScope.launch {
            _state.update { it.copy(busyUid = member.uid, error = null) }
            try {
                team.setActive(member, active)
                _state.update { state ->
                    state.copy(
                        busyUid = null,
                        members = state.members.map { if (it.uid == member.uid) it.copy(active = active) else it },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busyUid = null, error = (e as? TeamException)?.message ?: CloudErrors.message(e)) }
            }
        }
    }

    fun dismissAdded() {
        _state.update { it.copy(justAdded = null) }
    }
}
