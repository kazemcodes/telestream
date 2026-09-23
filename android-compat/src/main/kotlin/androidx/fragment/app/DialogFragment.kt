package androidx.fragment.app

open class DialogFragment : Fragment() {
    open fun dismiss() {}
    open fun dismissAllowingStateLoss() {}
    open fun show(manager: FragmentManager, tag: String?) {}
    open fun show(transaction: FragmentTransaction, tag: String?): Int = 0
    open fun showNow(manager: FragmentManager, tag: String?) {}
}
