// SPDX-FileCopyrightText: 2026 James Sesler
// SPDX-License-Identifier: GPL-3.0-or-later

package com.theamericanmaker.tickbox

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TickboxApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        reclaimOrphanedImages()
    }

    /**
     * Sweeps image files that no note references any more.
     *
     * Notes deleted before the orphan fix landed left their JPEGs behind, so a
     * long-standing install can have a fair amount to reclaim on first launch.
     */
    private fun reclaimOrphanedImages() {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val referenced = container.noteRepository.getAllImageFilePaths().toSet()
                container.imageStore.deleteOrphans(referenced)
            }
        }
    }
}
