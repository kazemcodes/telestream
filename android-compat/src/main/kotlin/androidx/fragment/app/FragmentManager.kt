package androidx.fragment.app

open class FragmentManager {
    open fun getFragments(): List<Fragment> = emptyList()
    open fun isDestroyed(): Boolean = false
    open fun isStateSaved(): Boolean = false
    open fun findFragmentByTag(tag: String): Fragment? = null
    open fun findFragmentById(id: Int): Fragment? = null
    open fun beginTransaction(): FragmentTransaction = FragmentTransaction()
}
