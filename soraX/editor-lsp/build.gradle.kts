  /*******************************************************************************
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2024  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 ******************************************************************************/
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.publish)
}

android {
    namespace = "io.github.rosemoe.sora.lsp"

    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

/**
 * 10.0.1 — تثبيت `gson` الذي يأتي من POM الخاصّ بـLSP4J.
 *
 * المشكلة: LSP4J يطلب `gson:[2.9.1,3.0)` — **مدى نسخ مفتوح**. المدى المفتوح
 * يُلزم Gradle بقراءة `maven-metadata.xml` من كل مستودع ليختار أعلى نسخة،
 * وليس بتثبيت نسخة واحدة. فإذا كان أي مستودع (هنا `repo.eclipse.org`) يعيد
 * ‏503، يسقط حلّ `gson` كلّه — رغم أن النسخة 2.14.0 موجودة سالمة في Maven
 * Central. أوقف هذا البناء مرّتين، ورسالته تُوهم بأن الحزمة مفقودة.
 *
 * الحلّ: نُسجّل سبب اختيار النسخة صراحةً، فيثبّت Gradle نسخة واحدة ولا يطلب
 * فهرس المدى إطلاقًا. لا نغيّر سلوكًا: 2.14.0 داخل المدى وهو ما كان يُختار.
 */
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.google.code.gson" && requested.name == "gson") {
            useVersion(libs.gson.get().version!!)
            because("تثبيت صريح يتفادى استعلام فهرس المدى المفتوح من مستودعات قد تعيد 503")
        }
    }
}

dependencies {
    compileOnly(projects.editor)
    implementation(libs.lsp4j)
    implementation(libs.kotlinx.coroutines)
    testImplementation(libs.junit)
    testImplementation(libs.tests.google.truth)
    testImplementation(libs.tests.robolectric)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
}
