// Modified by Rafail Andreou, 2026-10-26, for FocusRelay. Original notices retained.
// SPDX-License-Identifier: GPL-2.0-only
package com.mindful.android.services.tracking

/** Owns a WindowManager registration, not a View's later attachment callback.
 * All calls come from the UI looper. A failed removal retains ownership for retry.
 */
internal class RegisteredOverlay<V : Any, T : Any>(
    private val add: (V) -> Unit,
    private val remove: (V) -> Unit,
) {
    var view: V? = null
        private set
    var target: T? = null
        private set
    var closed = false
        private set

    fun show(target: T, allowed: () -> Boolean = { true }, create: () -> V): V? {
        if (closed || !allowed()) return null
        if (view != null && this.target == target) return null
        clear()
        val next = create()
        // Creating/inflating a view can overlap a revoke on the sync thread.
        if (closed || !allowed()) return null
        add(next)
        view = next
        this.target = target
        return next
    }

    fun clear() {
        view?.let { remove(it) } // Must run even before onAttachedToWindow.
        view = null
        target = null
    }

    fun clearIf(predicate: (T) -> Boolean) {
        target?.let { if (predicate(it)) clear() }
    }

    fun close() {
        closed = true
        clear()
    }
}

