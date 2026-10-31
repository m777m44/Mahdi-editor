package com.mahdi.git.template

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mahdi.file.FileObject
import com.mahdi.icons.Error
import com.mahdi.icons.Icon
import com.mahdi.icons.MahdiIcons
import com.mahdi.resources.drawables
import com.mahdi.resources.getString
import com.mahdi.resources.strings
import org.json.JSONObject

object IconPackTemplate : GitTemplate(repoUrl = "https://github.com/m777m44/Icon-Template") {

    override val id = "mahdi_icon_pack"
    override val label = "Icon pack"
    override val description = "Create a new Mahdi-Editor icon pack"
    override val icon = Icon.ResourceIcon(drawables.widgets)
    override val size: Long = 18432

    override val validConfiguration by derivedStateOf {
        idError == null && nameError == null && minAppVersionError == null
    }
    override val projectName by derivedStateOf { configStates.name }

    private var idError by mutableStateOf<String?>(null)
    private var nameError by mutableStateOf<String?>(null)
    private var minAppVersionError by mutableStateOf<String?>(null)

    private fun validateId(value: String): String? = if (value.isBlank()) strings.value_empty_err.getString() else null

    private fun validateName(value: String): String? = if (value.isBlank()) strings.name_empty_err.getString() else null

    private fun validateMinAppVersion(value: String): String? =
        if (value.toIntOrNull() == null) strings.value_invalid.getString() else null

    private var configStates by mutableStateOf(ConfigStates())

    private class ConfigStates {
        var id by mutableStateOf("com.mahdi.demo")
        var name by mutableStateOf("My Icon Pack")
        var minAppVersion by mutableStateOf("87")
        var applyTint by mutableStateOf(false)
    }

    @Composable
    override fun Configuration() {
        LaunchedEffect(Unit) { configStates = ConfigStates() }

        OutlinedTextField(
            value = configStates.id,
            onValueChange = {
                configStates.id = it
                idError = validateId(it)
            },
            label = { Text(stringResource(strings.template_id)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = idError != null,
            supportingText =
                idError?.let {
                    {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
            trailingIcon =
                idError?.let {
                    { Icon(MahdiIcons.Error, stringResource(strings.error), tint = MaterialTheme.colorScheme.error) }
                },
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = configStates.name,
            onValueChange = {
                configStates.name = it
                nameError = validateName(it)
            },
            label = { Text(stringResource(strings.name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = nameError != null,
            supportingText =
                nameError?.let {
                    {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
            trailingIcon =
                nameError?.let {
                    { Icon(MahdiIcons.Error, stringResource(strings.error), tint = MaterialTheme.colorScheme.error) }
                },
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = configStates.minAppVersion,
            onValueChange = {
                configStates.minAppVersion = it
                minAppVersionError = validateMinAppVersion(it)
            },
            label = { Text(stringResource(strings.template_min_app_version)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = minAppVersionError != null,
            supportingText =
                minAppVersionError?.let {
                    {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
            trailingIcon =
                minAppVersionError?.let {
                    { Icon(MahdiIcons.Error, stringResource(strings.error), tint = MaterialTheme.colorScheme.error) }
                },
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { configStates.applyTint = !configStates.applyTint }
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = configStates.applyTint,
                onCheckedChange = null,
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(text = stringResource(strings.template_apply_tint), style = MaterialTheme.typography.bodyLarge)
        }
    }

    override suspend fun afterClone(projectDir: FileObject) {
        val manifestFile = projectDir.getChild("manifest.json")
        if (manifestFile?.exists() == true) {
            val content = manifestFile.readText()
            val json = JSONObject(content)

            json.put("id", configStates.id)
            json.put("name", configStates.name)
            json.put("minAppVersion", configStates.minAppVersion.toIntOrNull() ?: 87)
            json.put("applyTint", configStates.applyTint)

            manifestFile.writeText(json.toString(2))
        }
    }
}
