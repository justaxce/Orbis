package com.floating.virtualwindow.updater

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkSize: String,
    val releaseNotes: String,
    val forceUpdate: Boolean = false
)
