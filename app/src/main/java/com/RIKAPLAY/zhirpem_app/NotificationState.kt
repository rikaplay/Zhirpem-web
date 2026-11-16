package com.RIKAPLAY.zhirpem_app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Показывает, открыто ли приложение прямо сейчас. Если открыто, фоновые уведомления
 * не нужны: входящий звонок и сообщения видны внутри приложения.
 */
object AppVisibility {
    @Volatile
    private var startedActivities = 0

    val isForeground: Boolean
        get() = startedActivities > 0

    fun register(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

/**
 * Действие, которое пользователь выбрал в уведомлении (ответить на звонок, открыть чат).
 * MainActivity читает его из Intent, а интерфейс реагирует на изменение состояния.
 */
object PendingNotificationAction {
    const val EXTRA_OPEN_CHAT = "notif_open_chat"
    const val EXTRA_ANSWER_CALL = "notif_answer_call"
    const val EXTRA_CALL_CHAT = "notif_call_chat"

    /** Нужно ответить на входящий звонок, как только он появится в приложении. */
    var answerCall by mutableStateOf(false)

    /** Нужно открыть этот чат. */
    var openChatId by mutableStateOf<String?>(null)

    fun consume(context: Context, intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra(EXTRA_ANSWER_CALL, false)) {
            answerCall = true
            intent.getStringExtra(EXTRA_CALL_CHAT)?.let { CallNotifications.cancelIncomingCall(context, it) }
            intent.removeExtra(EXTRA_ANSWER_CALL)
            intent.removeExtra(EXTRA_CALL_CHAT)
        }

        intent.getStringExtra(EXTRA_OPEN_CHAT)?.let {
            openChatId = it
            intent.removeExtra(EXTRA_OPEN_CHAT)
        }
    }
}
