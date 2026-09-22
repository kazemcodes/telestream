package androidx.preference

import android.content.Context
import android.content.SharedPreferences

object PreferenceManager {
    @JvmStatic
    fun getDefaultSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences("default_preferences", Context.MODE_PRIVATE)
    }
}
