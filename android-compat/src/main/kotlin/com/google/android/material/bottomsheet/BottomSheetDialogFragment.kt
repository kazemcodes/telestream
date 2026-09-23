package com.google.android.material.bottomsheet

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.FragmentManager

open class BottomSheetDialogFragment : AppCompatDialogFragment() {
    override fun show(manager: FragmentManager, tag: String?) {
        try {
            val dummyView = View(context)
            onViewCreated(dummyView, Bundle())
        } catch (_: Throwable) {}
    }
}
