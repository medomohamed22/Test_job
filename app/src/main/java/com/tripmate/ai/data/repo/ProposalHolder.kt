package com.tripmate.ai.data.repo

import com.tripmate.ai.domain.model.ReplanProposal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Holds the pending (not yet applied) re-plan proposal shown on the Before/After screen. */
@Singleton
class ProposalHolder @Inject constructor() {
    private val _pending = MutableStateFlow<ReplanProposal?>(null)
    val pending: StateFlow<ReplanProposal?> = _pending.asStateFlow()
    fun set(p: ReplanProposal) { _pending.value = p }
    fun clear() { _pending.value = null }
}
