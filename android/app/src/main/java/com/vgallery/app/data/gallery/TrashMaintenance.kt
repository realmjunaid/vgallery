package com.vgallery.app.data.gallery

import android.content.Context
import android.net.Uri
import androidx.work.Configuration
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Unique name so a second schedule never stacks a second daily job. */
const val TRASH_MAINTENANCE_WORK = "trash_maintenance"

/**
 * The part of trash cleanup that can run with nobody watching: drop entries whose file is already
 * gone. Items past 30 days stay listed — deleting them needs the user's approval, which
 * [com.vgallery.app.ui.trash.TrashPurgePrompt] asks for the next time the app opens.
 */
@Singleton
class TrashMaintenance @Inject constructor(
    private val repository: DeviceGalleryRepository,
) {
    suspend fun run(nowMs: Long = System.currentTimeMillis()): TrashMaintenancePlan {
        val items = repository.trashedMedia.first()
        if (items.isEmpty()) return TrashMaintenancePlan(emptyList(), emptyList())
        val gone = repository.goneUris(items.map { Uri.parse(it.uri) }).map { it.toString() }.toSet()
        val plan = trashMaintenancePlan(items, gone, nowMs)
        if (plan.pruneUris.isNotEmpty()) repository.removeTrashedMedia(plan.pruneUris)
        return plan
    }
}

/**
 * Daily sweep. WorkManager builds this itself, so it reaches the repository through a Hilt entry
 * point rather than constructor injection.
 */
class TrashMaintenanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        entryPoint().trashMaintenance().run()
        Result.success()
    } catch (_: Exception) {
        // A permanent failure (a broken store, an unavailable entry point) must not be retried
        // forever, so the daily sweep gives up after a few tries and waits for the next period.
        if (runAttemptCount >= MAX_ATTEMPTS) Result.failure() else Result.retry()
    }

    private fun entryPoint(): TrashWorkerEntryPoint =
        EntryPointAccessors.fromApplication(applicationContext, TrashWorkerEntryPoint::class.java)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TrashWorkerEntryPoint {
    fun trashMaintenance(): TrashMaintenance
}

/** How many times the daily sweep retries a failing run before giving up until the next period. */
private const val MAX_ATTEMPTS = 3

/**
 * One daily job, kept if it is already scheduled, so opening the app never resets the clock.
 *
 * The manifest initializer normally runs first. A test process (and any process where that
 * initializer was stripped) has no WorkManager yet, so this creates one rather than crashing
 * the launch that was only trying to tidy the trash.
 */
fun scheduleTrashMaintenance(context: Context) {
    val workManager = try {
        WorkManager.getInstance(context)
    } catch (_: IllegalStateException) {
        WorkManager.initialize(context, Configuration.Builder().build())
        WorkManager.getInstance(context)
    }
    val request = PeriodicWorkRequestBuilder<TrashMaintenanceWorker>(1, TimeUnit.DAYS).build()
    workManager.enqueueUniquePeriodicWork(
        TRASH_MAINTENANCE_WORK,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )
}
