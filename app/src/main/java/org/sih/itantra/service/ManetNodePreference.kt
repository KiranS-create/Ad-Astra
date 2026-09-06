package org.sih.itantra.service

import android.content.Context
import android.content.SharedPreferences

/**
 * Lightweight SharedPreferences-backed persistence for MANET Node Mode state.
 *
 * Survives Activity/ViewModel destruction and device reboot.
 * Does NOT depend on Compose, ViewModel, or any UI lifecycle.
 */
object ManetNodePreference {

    private const val PREFS_NAME = "manet_node_prefs"
    private const val KEY_NODE_MODE_ENABLED = "node_mode_enabled"
    private const val KEY_LAST_NODE_ID = "last_node_id"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Whether the operator has enabled MANET Node Mode (persisted across reboots). */
    fun isNodeModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NODE_MODE_ENABLED, false)

    fun setNodeModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NODE_MODE_ENABLED, enabled).apply()
    }

    /** Last known local node ID (for diagnostics / notification display). */
    fun lastNodeId(context: Context): Int =
        prefs(context).getInt(KEY_LAST_NODE_ID, 0)

    fun setLastNodeId(context: Context, nodeId: Int) {
        prefs(context).edit().putInt(KEY_LAST_NODE_ID, nodeId).apply()
    }
}
