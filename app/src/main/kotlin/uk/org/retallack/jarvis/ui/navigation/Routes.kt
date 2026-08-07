package uk.org.retallack.jarvis.ui.navigation

/**
 * Navigation routes for the app.
 */
object Routes {
    // Setup wizard
    const val SETUP_WIZARD = "setup"
    const val SETUP_WELCOME = "setup/welcome"
    const val SETUP_CONNECTION = "setup/connection"
    const val SETUP_MODEL_DOWNLOAD = "setup/model_download"
    const val SETUP_WAKE_WORD = "setup/wake_word"
    const val SETUP_QUIET_HOURS = "setup/quiet_hours"
    const val SETUP_DONE = "setup/done"

    // Main app tabs
    const val MAIN = "main"
    const val VOICE_TAB = "main/voice"
    const val ENTITIES_TAB = "main/entities"
    const val SETTINGS_TAB = "main/settings"

    // Entity detail
    const val ENTITY_DETAIL = "entity/{entityId}"
    fun entityDetail(entityId: String) = "entity/$entityId"
}
