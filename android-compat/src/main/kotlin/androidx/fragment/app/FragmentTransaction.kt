package androidx.fragment.app

open class FragmentTransaction {
    open fun add(fragment: Fragment, tag: String? = null): FragmentTransaction = this
    open fun add(containerViewId: Int, fragment: Fragment, tag: String? = null): FragmentTransaction = this
    open fun remove(fragment: Fragment): FragmentTransaction = this
    open fun show(fragment: Fragment): FragmentTransaction = this
    open fun hide(fragment: Fragment): FragmentTransaction = this
    open fun replace(containerViewId: Int, fragment: Fragment, tag: String? = null): FragmentTransaction = this
    open fun addToBackStack(name: String? = null): FragmentTransaction = this
    open fun commit(): Int = 0
    open fun commitAllowingStateLoss(): Int = 0
    open fun commitNow(): Unit {}
    open fun commitNowAllowingStateLoss(): Unit {}
}
