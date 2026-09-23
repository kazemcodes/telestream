package com.google.android.material.bottomsheet

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

open class BottomSheetDialogFragment : Fragment() {
    open fun show(manager: FragmentManager, tag: String?) {
        try {
            val dummyView = View(context)
            onViewCreated(dummyView, Bundle())
        } catch (_: Throwable) {}
    }
    open fun dismiss() {}
    open fun dismissAllowingStateLoss() {}
}
