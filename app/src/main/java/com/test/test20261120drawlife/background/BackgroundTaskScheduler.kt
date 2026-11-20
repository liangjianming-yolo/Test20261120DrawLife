package com.test.test20261120drawlife.background

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackgroundTaskScheduler {
    const val LOG_TAG = "DrawLife"
    private const val JOB_ID = 100
    private const val JOB_DELAY_MILLIS = 30_000L
    private const val WORK_NAME = "WorkerServiceUnique"

    fun scheduleJob(context: Context, keepExisting: Boolean = true) {
        try {
            val scheduler = requireNotNull(context.getSystemService(JobScheduler::class.java))

            // 后台 Job 唤起新进程时，保留系统准备交付的任务。
            if (keepExisting && scheduler.getPendingJob(JOB_ID) != null) {
                Log.i(LOG_TAG, "Job $JOB_ID already scheduled; keeping existing job")
                return
            }

            val job = JobInfo.Builder(
                JOB_ID,
                ComponentName(context, DrawLifeJobService::class.java)
            )
                .setMinimumLatency(JOB_DELAY_MILLIS)
                .build()

            // 同一个 ID 会替换已有任务。30 秒是最小等待时间，实际执行由系统决定。
            val result = scheduler.schedule(job)
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.i(LOG_TAG, "Job $JOB_ID scheduled, minimum latency=${JOB_DELAY_MILLIS}ms")
            } else {
                Log.e(LOG_TAG, "Job $JOB_ID scheduling rejected, result=$result")
            }
        } catch (exception: Exception) {
            Log.e(LOG_TAG, "Unable to schedule Job $JOB_ID", exception)
        }
    }

    fun schedulePeriodicWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<DrawLifeWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

            // 进程重建时保留已有周期工作，包括当前正在执行的工作。
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(LOG_TAG, "Periodic work requested: $WORK_NAME, 15 minutes, CONNECTED, KEEP")
        } catch (exception: Exception) {
            Log.e(LOG_TAG, "Unable to enqueue $WORK_NAME", exception)
        }
    }
}
