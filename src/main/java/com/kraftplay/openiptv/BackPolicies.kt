package com.kraftplay.openiptv

internal enum class PlayerBackAction { HIDE_CONTROLS, EXIT }

internal fun playerBackAction(showInfoPanel: Boolean): PlayerBackAction =
    if (showInfoPanel) PlayerBackAction.HIDE_CONTROLS else PlayerBackAction.EXIT

internal object BackExitPolicy {
    const val DEFAULT_BEHAVIOR = "Double press to exit"

    enum class Action { IGNORE, PROMPT, EXIT }
    data class State(val firstPressAt: Long? = null, val lastPressAt: Long? = null)
    data class Result(val action: Action, val state: State)

    fun onBack(behavior: String, now: Long, state: State = State()): Result = when (behavior) {
        "Exit" -> Result(Action.EXIT, State())
        "Confirm", "Double press to exit" -> {
            val firstPressAt = state.firstPressAt
            val lastPressAt = state.lastPressAt
            if (lastPressAt != null && now - lastPressAt < 400) {
                Result(Action.IGNORE, state.copy(lastPressAt = now))
            } else if (firstPressAt != null && now - firstPressAt <= 1_500) {
                Result(Action.EXIT, State())
            } else {
                Result(Action.PROMPT, State(firstPressAt = now, lastPressAt = now))
            }
        }
        else -> Result(Action.IGNORE, state)
    }
}
