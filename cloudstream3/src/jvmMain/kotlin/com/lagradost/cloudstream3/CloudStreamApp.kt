package com.lagradost.cloudstream3

import android.content.Context
import android.content.SimulatedContext

/**
 * CloudStreamApp compatibility stub for plugins that access application context
 * (such as phisher98's DonationManager or preference helpers).
 */
open class CloudStreamApp {
    companion object {
        @JvmField
        var context: Context? = SimulatedContext()

        @JvmStatic
        fun getContext(): Context? = context

        @JvmStatic
        fun setContext(ctx: Context?) {
            context = ctx
        }
    }
}
