package com.mahdi.settings.extension

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahdi.components.compose.preferences.base.PreferenceLayout
import com.mahdi.extension.LocalExtension
import com.mahdi.extension.extensionManager
import com.mahdi.resources.strings

@Composable
fun ExtensionSettings(extension: LocalExtension?) {
    val loadedExtensions by extensionManager.loadedExtensions.collectAsStateWithLifecycle()
    val api = loadedExtensions[extension]?.api

    PreferenceLayout(label = extension?.name ?: stringResource(strings.ext_not_found)) {
        if (extension == null || api == null) {
            Text(stringResource(strings.ext_not_found_desc), modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            api.SettingsContent()
        }
    }
}
