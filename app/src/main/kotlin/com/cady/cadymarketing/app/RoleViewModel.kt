package com.cady.cadymarketing.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.team.RoleRepository
import com.cady.cadymarketing.data.team.RoleState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Finds out, once per sign-in, who the signed-in account is for this app (see [RoleRepository]). */
@HiltViewModel
class RoleViewModel @Inject constructor(
    private val roles: RoleRepository,
) : ViewModel() {
    val state: StateFlow<RoleState> = roles.state

    fun load(uid: String) {
        viewModelScope.launch { roles.load(uid) }
    }

    fun retry(uid: String) {
        viewModelScope.launch { roles.load(uid, force = true) }
    }
}
