package com.odom.privatediary.download

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DownloadViewModel(app: Application) : AndroidViewModel(app) {
    private val wm = WorkManager.getInstance(app)

    /** 0..100 진행률. 완료되면 100 유지. */
    val progress: Flow<Int> = wm.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME).map { infos ->
        val info = infos.firstOrNull() ?: return@map 0
        if (info.state == WorkInfo.State.SUCCEEDED) 100
        else info.progress.getInt(ModelDownloadWorker.KEY_PROGRESS, 0)
    }

    fun start(wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .build()
        wm.enqueueUniqueWork(ModelDownloadWorker.WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
