package eu.kanade.domain.ui

import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Scope set when the app is opened from a generated companion launcher.
 *
 * A companion is a launcher APK for a single extension: the host must behave as if that
 * source were the only one installed, so library and history are filtered by [sourceId].
 */
object CompanionScope {

    const val KIND_ANIME = "anime"
    const val KIND_MANGA = "manga"

    private val preferences: UiPreferences
        get() = Injekt.get()

    val sourceId: Long
        get() = preferences.companionSourceId().get()

    val kind: String
        get() = preferences.companionKind().get()

    val isActive: Boolean
        get() = sourceId >= 0 && kind.isNotEmpty()

    fun save(sourceId: Long, kind: String) {
        preferences.companionSourceId().set(sourceId)
        preferences.companionKind().set(kind)
    }

    fun appliesTo(kind: String): Boolean = isActive && this.kind == kind

    /** Source id to filter by for the given content kind, or -1 when unscoped. */
    fun sourceIdFor(kind: String): Long = if (appliesTo(kind)) sourceId else -1L
}
