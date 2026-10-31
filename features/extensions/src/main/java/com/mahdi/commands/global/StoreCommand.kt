package com.mahdi.commands.global

import android.content.Intent
import com.mahdi.activities.settings.SettingsActivity
import com.mahdi.activities.settings.SettingsRoutes
import com.mahdi.commands.ActionContext
import com.mahdi.commands.GlobalCommand
import com.mahdi.icons.Icon
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings

/**
 * «تصفح الإضافات» — يفتح متجر Mahdi-Editor مباشرةً على تبويب الإضافات،
 * من لوحة الأوامر أو من أي زرّ يربطه بأمر.
 */
class StoreCommand : GlobalCommand() {
    override val id: String = "global.store"

    override fun getLabel(): String = strings.browse_extensions.getString()

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.store)

    override fun execute(context: ActionContext) {
        val activity = context.currentActivity
        activity.startActivity(
            Intent(activity, SettingsActivity::class.java)
                .putExtra("route", SettingsRoutes.Extensions.route)
        )
    }
}
