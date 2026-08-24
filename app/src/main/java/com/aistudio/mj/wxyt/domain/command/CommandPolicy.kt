package com.aistudio.mj.wxyt.domain.command

class CommandPolicy {
    fun isAllowed(action: CommandAction): Boolean {
        return when (action) {
            CommandAction.OPEN_APP -> true
            CommandAction.OPEN_SETTINGS -> true
            CommandAction.SEARCH_WEB -> true
            CommandAction.SEARCH_APP -> true
            CommandAction.SEND_MESSAGE -> true
            CommandAction.CALL_CONTACT -> true
            CommandAction.PLAY_MEDIA -> true
            CommandAction.INSTALL_APP -> true
            CommandAction.OPEN_PLAY_STORE -> true
            CommandAction.TYPE_TEXT -> true
            CommandAction.CLICK_TEXT -> true
            CommandAction.GO_BACK -> true
            // PRD 1 §3.2: Media control actions
            CommandAction.PAUSE_MEDIA -> true
            CommandAction.NEXT_TRACK -> true
            CommandAction.PREVIOUS_TRACK -> true
            CommandAction.VOLUME_UP -> true
            CommandAction.VOLUME_DOWN -> true
            CommandAction.SET_VOLUME -> true
            CommandAction.PLAY_MEDIA_DEEP_LINK -> true
            else -> false
        }
    }
}
