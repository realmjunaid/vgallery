package com.vgallery.app.data.gallery

/**
 * Known/flagged folder names behind the home "new folder" green dot. [known] is every folder
 * ever seen, [flagged] the ones detected as new but not opened yet.
 */
data class FolderMarkerState(
    val known: Set<String> = emptySet(),
    val flagged: Set<String> = emptySet()
)

/**
 * Reconciles the folder names currently on the device ([names]) with the persisted [state].
 *
 * The very first scan is a baseline: everything present becomes "known" and nothing is flagged,
 * so a fresh install (or a first upgrade that adds this feature) never dots every folder at once.
 * Later scans flag only folders that were not known before, and drop flags for folders that have
 * since gone away. Opening a folder clears its own flag through [FolderMarkerState.cleared].
 */
fun reconcileFolderMarkers(names: Set<String>, state: FolderMarkerState): FolderMarkerState {
    if (state.known.isEmpty()) return FolderMarkerState(known = names, flagged = emptySet())
    val added = names - state.known
    return FolderMarkerState(
        known = state.known + names,
        flagged = (state.flagged + added) intersect names
    )
}

/** The state with [name]'s flag cleared (the user opened it); the name stays "known". */
fun FolderMarkerState.cleared(name: String): FolderMarkerState = copy(flagged = flagged - name)
