package com.btcswing.simulator.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.btcswing.simulator.di.ServiceLocator

/**
 * Recuperação após reinicialização do aparelho (BOOT_COMPLETED) e após
 * atualização do app (MY_PACKAGE_REPLACED). Se o robô estava habilitado,
 * religa o serviço e reagenda os workers — sem inventar nenhuma operação
 * do período em que o aparelho esteve desligado.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = ServiceLocator.settings.current()
                if (settings.botEnabled) {
                    BotController.enable(context.applicationContext)
                } else {
                    BotController.scheduleWorkers(context.applicationContext, settings)
                }
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
