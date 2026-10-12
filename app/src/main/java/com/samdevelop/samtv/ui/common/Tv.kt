package com.samdevelop.samtv.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** TV Box / Android TV: sem tela de toque ou com leanback. Usado para foco inicial e atalhos do controle. */
fun Context.isTvDevice(): Boolean {
    val pm = packageManager
    return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}

@Composable
fun rememberIsTv(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isTvDevice() }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
