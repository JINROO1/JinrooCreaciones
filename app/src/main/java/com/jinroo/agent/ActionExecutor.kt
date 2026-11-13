package com.jinroo.agent

/** Executes one action at a time; stops the pipeline if root injection fails. */
class ActionExecutor {
    suspend fun execute(action: Action): Boolean = RootManager.tap(action.x, action.y)
}
