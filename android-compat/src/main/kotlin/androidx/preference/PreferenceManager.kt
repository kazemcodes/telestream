package androidx.preference

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager {
    companion object {
        @JvmStatic
        fun getDefaultSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(
                context.packageName ?: "default_preferences",
                Context.MODE_PRIVATE
            )
        }
    }
}
