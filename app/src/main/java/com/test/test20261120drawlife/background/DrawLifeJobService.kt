package com.test.test20261120drawlife.background

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Process
import android.util.Log

class DrawLifeJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Log.i(
            BackgroundTaskScheduler.LOG_TAG,
            "Job.onStartJob id=${params.jobId}, pid=${Process.myPid()}"
        )

        BackgroundTaskScheduler.scheduleJob(this, keepExisting = false)

        // 本次同步工作已完成，下一次任务已提交给系统。
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        Log.i(
            BackgroundTaskScheduler.LOG_TAG,
            "Job.onStopJob id=${params.jobId}, pid=${Process.myPid()}"
        )
        // onStartJob 已负责安排下一次，停止时无需再重复提交。
        return false
    }
}
