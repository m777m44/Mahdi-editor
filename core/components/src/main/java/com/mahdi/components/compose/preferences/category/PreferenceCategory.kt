package com.mahdi.components.compose.preferences.category

/*
 * Copyright 2021, Lawnchair.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mahdi.components.compose.preferences.base.PreferenceTemplate

@Composable
fun PreferenceCategory(
    modifier: Modifier = Modifier,
    label: String,
    @DrawableRes iconResource: Int? = null,
    onNavigate: () -> Unit,
    isSelected: Boolean = false,
    description: String? = null,
    endWidget: (@Composable () -> Unit)? = null,
    startWidget: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
) {
    PreferenceTemplate(
        modifier =
            modifier
                .padding(horizontal = 16.dp)
                .clip(MaterialTheme.shapes.large)
                .clickable { onNavigate() }
                .background(
                    if (isSelected) MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp) else Color.Transparent
                ),
        verticalPadding = 14.dp,
        title = {
            Text(
                text = label,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            )
        },
        description = {
            if (description != null) {
                Text(text = description)
            }
        },
        startWidget = {
            // 9.4.0: أيقونة القسم داخل مربّع ملوّن بحواف ناعمة بدل أيقونة عائمة —
            // يوحّد شكل كل شاشات الإعدادات ويمنحها عمقًا بصريًا.
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = if (isSelected) 0.22f else 0.12f)
                        ),
            ) {
                if (iconResource != null) {
                    Icon(
                        painter = painterResource(id = iconResource),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (startWidget != null) {
                    startWidget()
                }
            }
        },
        endWidget = { endWidget?.let { it() } },
        enabled = enabled,
    )
}
