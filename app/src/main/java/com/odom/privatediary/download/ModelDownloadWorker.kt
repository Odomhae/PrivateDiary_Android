package com.odom.privatediary.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File

/** 매니페스트를 받아 모델별로 이어받기 다운로드 + SHA-256 검증. 포그라운드 서비스로 진행. */
class ModelDownloadWorker(
    private val ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    private val downloader = ModelDownloader()

    override suspend fun doWork(): Result {
        setForeground(foregroundInfo(0))
        val manifest = runCatching { ManifestParser.parse(downloader.fetchText(R2Config.manifestUrl())) }
            .getOrElse { return Result.retry() }

        val modelsDir = File(ctx.filesDir, "models").apply { mkdirs() }
        for (model in manifest.models) {
            val target = File(modelsDir, model.name)
            if (target.exists() && Sha256.verify(target, model.sha256)) continue
            runCatching {
                downloader.download(R2Config.url(model.path), target, model.bytes) { done, total ->
                    val pct = if (total > 0) (done * 100 / total).toInt() else 0
                    setProgressAsync(workDataOf(KEY_PROGRESS to pct, KEY_MODEL to model.name))
                }
            }.getOrElse { return Result.retry() }
            if (!Sha256.verify(target, model.sha256)) {
                target.delete()
                return Result.retry()
            }
        }
        return Result.success()
    }

    private fun foregroundInfo(pct: Int): ForegroundInfo {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "모델 다운로드", NotificationManager.IMPORTANCE_LOW),
        )
        val notification: Notification = Notification.Builder(ctx, CHANNEL_ID)
            .setContentTitle("모델 다운로드 중")
            .setContentText("$pct%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val WORK_NAME = "model_download"
        const val KEY_PROGRESS = "progress"
        const val KEY_MODEL = "model"
        private const val CHANNEL_ID = "model_download"
        private const val NOTIF_ID = 1
    }
}
