package com.mahdi.drawer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mahdi.icons.Icon
import java.io.Serializable

abstract class DrawerTab : Serializable {
    @Composable abstract fun Content(modifier: Modifier)

    abstract fun getName(): String

    abstract fun getIcon(): Icon

    open fun isSupported(): Boolean = true

    open fun isEnabled(): Boolean = true

    open fun onAdded() {}

    open fun onRemoved() {}
}
