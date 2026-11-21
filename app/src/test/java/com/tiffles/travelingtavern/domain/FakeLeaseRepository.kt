package com.tiffles.travelingtavern.domain

import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.repository.LeaseRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeLeaseRepository : LeaseRepository {

    override val status = MutableStateFlow<LeaseStatus>(LeaseStatus.NotReported)
    override val unprotected = MutableStateFlow(false)
    override val commandWaiting = MutableStateFlow(false)
    override val stillLoading = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    var useHereCalls = 0
    var reloadCalls = 0
    var continueCalls = 0

    override suspend fun useHere() {
        useHereCalls++
    }

    override suspend fun reload() {
        reloadCalls++
    }

    override suspend fun continueWithoutProtection() {
        continueCalls++
    }
}
