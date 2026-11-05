package com.mahdi.components.compose.appbars

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

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahdi.components.compose.icons.ClickableIcon

/**
 * إجراء «الرئيسية» (5.0.0).
 *
 * يُوفّره نشاط الإعدادات (`SettingsActivity`)، فتظهر أيقونة البيت في الشريط
 * العلوي **لكل شاشات الإعدادات** بلا تعديل أي شاشة على حدة. ضغطها يعيد المستخدم
 * إلى المحرّر (الرئيسية) بدل أن يضيع في أعماق الإعدادات.
 */
val LocalHomeAction = compositionLocalOf<(() -> Unit)?> { null }

/**
 * شعار التطبيق داخل الشريط العلوي (5.0.0) — يُوفَّر كـ slot حتى يبقى هذا
 * المكوّن مستقلًا عن وحدة الأيقونات (`core/main`).
 */
val LocalTopBarLogo = compositionLocalOf<(@Composable () -> Unit)?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    backArrowVisible: Boolean,
    label: String,
    isExpandedScreen: Boolean,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val onHome = LocalHomeAction.current
    val logo = LocalTopBarLogo.current

    val navigationIcon: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (backArrowVisible) {
                ClickableIcon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    onClick = { backDispatcher?.onBackPressed() },
                )
            }
            if (onHome != null) {
                IconButton(onClick = onHome) {
                    Icon(imageVector = Icons.Rounded.Home, contentDescription = null)
                }
            }
        }
    }

    val title: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (logo != null) {
                logo()
                Spacer(Modifier.width(10.dp))
            }
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    if (isExpandedScreen) {
        TopAppBar(
            modifier = modifier,
            title = title,
            actions = actions,
            navigationIcon = navigationIcon,
            scrollBehavior = scrollBehavior,
        )
    } else {
        LargeTopAppBar(
            modifier = modifier,
            title = title,
            actions = actions,
            navigationIcon = navigationIcon,
            scrollBehavior = scrollBehavior,
        )
    }
}
