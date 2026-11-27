package com.test.test20261120drawlife.background

import android.content.Context
import androidx.annotation.Keep
import androidx.work.WorkerParameters
import com.drawlife.backgroundtoolkit.background.work.DrawLifeWorker as ToolkitWorker

/** 旧版 App 创建的周期任务保存了这个类的完整名称。用 @Keep 保留入口，让旧任务也执行库中的实现。 */
@Keep
class DrawLifeWorker(context: Context, parameters: WorkerParameters) :
    ToolkitWorker(context, parameters)
