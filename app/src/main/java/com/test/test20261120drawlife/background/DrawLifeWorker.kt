package com.test.test20261120drawlife.background

import android.content.Context
import android.os.Process
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class DrawLifeWorker(
    context: Context,
    workerParameters: WorkerParameters
) : Worker(context, workerParameters) {
    override fun doWork(): Result {
        Log.i(
            BackgroundTaskScheduler.LOG_TAG,
            "Worker.doWork id=$id, attempt=$runAttemptCount, pid=${Process.myPid()}"
        )
        return Result.success()
    }
}
