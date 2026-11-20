package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.MessageAction
import com.tiffles.travelingtavern.domain.model.TappedMessage
import javax.inject.Inject

/**
 * The actions the sheet offers for a tapped message: Regenerate on the latest message
 * (whoever sent it, except an untouched greeting), Continue on the latest character message,
 * and Copy, Edit, Branch, Hide or Show, Delete on every message.
 */
class GetMessageActionsUseCase @Inject constructor() {

    operator fun invoke(message: TappedMessage): List<MessageAction> = buildList {
        if (message.canRegenerate) add(MessageAction.Regenerate)
        if (message.canContinue) add(MessageAction.Continue)
        add(MessageAction.Copy)
        add(MessageAction.Edit)
        add(MessageAction.Branch)
        add(if (message.isHidden) MessageAction.Show else MessageAction.Hide)
        add(MessageAction.Delete)
    }

    /** While a reply is generating, only Copy can run. */
    fun isEnabled(action: MessageAction, generating: Boolean): Boolean =
        !generating || action == MessageAction.Copy
}
