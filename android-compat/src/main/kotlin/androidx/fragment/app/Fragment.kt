package androidx.fragment.app

import android.content.Context
import android.os.Bundle
import android.view.View

open class Fragment {
    var activity: FragmentActivity? = null
    var context: Context? = null

    open fun onViewCreated(view: View, savedInstanceState: Bundle?) {}
    open fun onDestroyView() {}
}
