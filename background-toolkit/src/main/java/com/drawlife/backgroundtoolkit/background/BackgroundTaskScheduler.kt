package com.drawlife.backgroundtoolkit.background

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
import com.drawlife.backgroundtoolkit.BackgroundToolkit
import java.util.concurrent.TimeUnit

/** 工具库初始化时从这里分别向 JobScheduler 和 WorkManager 提交已启用的任务。 */
internal object BackgroundTaskScheduler {
    const val LOG_TAG = "DrawLife_Log"
    private const val JOB_DELAY_MILLIS = 30_000L

    /** 初始化时保留同一服务的待执行 Job。Job 回调传入 false，用相同 ID 安排下一轮。 */
    fun scheduleJob(context: Context, keepExisting: Boolean = true) {
        val config = BackgroundToolkit.configuration ?: return
        val jobId = config.jobId ?: return
        try {
            val scheduler = requireNotNull(context.getSystemService(JobScheduler::class.java))
            val service = ComponentName(context, DrawLifeJobService::class.java)
            val pendingJob = scheduler.getPendingJob(jobId)

            // 后台 Job 唤起新进程时，保留系统准备交付的任务。迁移旧组件时改用库的服务。
            if (keepExisting && pendingJob?.service == service) {
                Log.i(LOG_TAG, "Job $jobId already scheduled; keeping existing job")
                return
            }
            // 只有配置中指明的旧服务可以迁移，避免覆盖宿主占用相同 ID 的其他 Job。
            if (pendingJob != null && pendingJob.service != service &&
                (pendingJob.service.packageName != context.packageName ||
                    pendingJob.service.className != config.legacyJobServiceName)
            ) {
                Log.e(LOG_TAG, "Job $jobId is already used by ${pendingJob.service}; choose another ID")
                return
            }

            val job = JobInfo.Builder(jobId, service)
                .setMinimumLatency(JOB_DELAY_MILLIS)
                .build()

            // 同一个 ID 会替换已有任务。30 秒是最小等待时间，实际执行由系统决定。
            val result = scheduler.schedule(job)
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.i(LOG_TAG, "Job $jobId scheduled, minimum latency=${JOB_DELAY_MILLIS}ms")
            } else {
                Log.e(LOG_TAG, "Job $jobId scheduling rejected, result=$result")
            }
        } catch (exception: Exception) {
            Log.e(LOG_TAG, "Unable to schedule Job $jobId", exception)
        }
    }

    /** 按唯一名称注册需要联网的 15 分钟周期任务，具体运行时机由 WorkManager 调度。 */
    fun schedulePeriodicWork(context: Context) {
        val config = BackgroundToolkit.configuration ?: return
        if (!config.enablePeriodicWork) return
        val workName = config.periodicWorkName
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<DrawLifeWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .addTag(workName)
                .build()

            // 进程重建时保留已有周期工作，包括当前正在执行的工作。
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                workName,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(LOG_TAG, "Periodic work requested: $workName, 15 minutes, CONNECTED, KEEP")
        } catch (exception: Exception) {
            Log.e(LOG_TAG, "Unable to enqueue $workName", exception)
        }
    }
}
