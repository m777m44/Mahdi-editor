package com.mahdi.settings

import com.mahdi.extension.api.DynamicRoute
import com.mahdi.extension.api.MahdiExtensionPoint
import com.mahdi.icons.Icon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SettingsCategory(
    val label: String,
    val description: String,
    val icon: Icon,
    val route: String,
)

object SettingsRegistry {

    private val _categories = MutableStateFlow<List<SettingsCategory>>(emptyList())
    val categories: StateFlow<List<SettingsCategory>> = _categories.asStateFlow()

    private val _routes = MutableStateFlow<List<DynamicRoute>>(emptyList())
    val routes: StateFlow<List<DynamicRoute>> = _routes.asStateFlow()

    @MahdiExtensionPoint
    fun registerCategory(category: SettingsCategory) {
        _categories.update { it + category }
    }

    @MahdiExtensionPoint
    fun unregisterCategory(category: SettingsCategory) {
        _categories.update { it - category }
    }

    @MahdiExtensionPoint
    fun registerRoute(route: DynamicRoute) {
        _routes.update { it + route }
    }

    @MahdiExtensionPoint
    fun unregisterRoute(route: DynamicRoute) {
        _routes.update { it - route }
    }
}
