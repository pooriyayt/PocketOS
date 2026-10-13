package app.pocketos.notifications
import android.annotation.TargetApi
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.TileService
import app.pocketos.MainActivity

/**
 * Android Quick Settings Tile for instant Quick Add entry.
 * Allows one-tap access from the notification pull-down panel.
 */
@TargetApi(Build.VERSION_CODES.N)
class QuickAddTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("pocketos://add")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
