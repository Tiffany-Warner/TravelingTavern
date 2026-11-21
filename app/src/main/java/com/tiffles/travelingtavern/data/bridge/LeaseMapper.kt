package com.tiffles.travelingtavern.data.bridge

import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ParkReason

/**
 * Types the page's `leaseState`. Fails closed: any `state` other than `holder` counts as
 * parked, with an unknown state kept as [ParkReason.Unknown]. A holder's reason is ignored.
 */
internal fun BridgeMessage.LeaseState.toLeaseStatus(): LeaseStatus = when (state) {
    "holder" -> LeaseStatus.Holder(busy = busy, connected = connected)
    "parked" -> LeaseStatus.Parked(parkReason(reason))
    else -> LeaseStatus.Parked(ParkReason.Unknown(state))
}

private fun parkReason(reason: String?): ParkReason = when (reason) {
    "held" -> ParkReason.Held
    "lost" -> ParkReason.Lost
    "claiming" -> ParkReason.Claiming
    "waitingForReply" -> ParkReason.WaitingForReply
    "lostRace" -> ParkReason.LostRace
    "unreadable" -> ParkReason.Unreadable
    "unknownFormat" -> ParkReason.UnknownFormat
    "reloadPending" -> ParkReason.ReloadPending
    else -> ParkReason.Unknown(reason)
}
