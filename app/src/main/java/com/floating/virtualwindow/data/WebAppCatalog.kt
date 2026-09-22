package com.floating.virtualwindow.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.floating.virtualwindow.R

data class WebAppInfo(
    val title: String,
    val url: String,
    val asDesktop: Boolean = false,
    val iconRes: Int = R.drawable.ic_browser
)

data class CuratedWebApp(
    val id: String,
    val name: String,
    val url: String,
    val asDesktop: Boolean = false,
    val alternativePackages: List<String> = emptyList(),
    val category: String = "Popular",
    val iconRes: Int = R.drawable.ic_browser
)

object WebAppCatalog {

    /**
     * Canonical list of high-quality curated Web Apps with official bundled brand icons.
     * Each app has guaranteed web compatibility in Orbis's floating popup window.
     */
    val CURATED_APPS: List<CuratedWebApp> = listOf(
        // Social & Messaging
        CuratedWebApp(
            id = "com.whatsapp",
            name = "WhatsApp Web",
            url = "https://web.whatsapp.com",
            asDesktop = true,
            alternativePackages = listOf("com.whatsapp.w4b"),
            category = "Social",
            iconRes = R.drawable.ic_app_whatsapp
        ),
        CuratedWebApp(
            id = "com.instagram.android",
            name = "Instagram",
            url = "https://www.instagram.com",
            asDesktop = false,
            alternativePackages = listOf("com.instagram.lite"),
            category = "Social",
            iconRes = R.drawable.ic_app_instagram
        ),
        CuratedWebApp(
            id = "org.telegram.messenger",
            name = "Telegram",
            url = "https://web.telegram.org/a/",
            asDesktop = false,
            alternativePackages = listOf("org.telegram.messenger.web", "org.telegram.plus"),
            category = "Social",
            iconRes = R.drawable.ic_app_telegram
        ),
        CuratedWebApp(
            id = "com.twitter.android",
            name = "X",
            url = "https://x.com",
            asDesktop = false,
            alternativePackages = listOf("com.twitter.android.lite"),
            category = "Social",
            iconRes = R.drawable.ic_app_x
        ),
        CuratedWebApp(
            id = "com.discord",
            name = "Discord",
            url = "https://discord.com/login",
            asDesktop = false,
            alternativePackages = listOf("com.discord.canary", "com.discord.ptb"),
            category = "Social",
            iconRes = R.drawable.ic_app_discord
        ),
        CuratedWebApp(
            id = "com.reddit.frontpage",
            name = "Reddit",
            url = "https://www.reddit.com",
            asDesktop = false,
            category = "Social",
            iconRes = R.drawable.ic_app_reddit
        ),
        CuratedWebApp(
            id = "com.instagram.barcelona",
            name = "Threads",
            url = "https://www.threads.net",
            asDesktop = false,
            category = "Social",
            iconRes = R.drawable.ic_app_threads
        ),
        CuratedWebApp(
            id = "com.facebook.katana",
            name = "Facebook",
            url = "https://m.facebook.com",
            asDesktop = false,
            alternativePackages = listOf("com.facebook.lite"),
            category = "Social",
            iconRes = R.drawable.ic_app_facebook
        ),
        CuratedWebApp(
            id = "com.facebook.orca",
            name = "Messenger",
            url = "https://www.messenger.com",
            asDesktop = false,
            alternativePackages = listOf("com.facebook.mlite"),
            category = "Social",
            iconRes = R.drawable.ic_app_messenger
        ),
        CuratedWebApp(
            id = "com.linkedin.android",
            name = "LinkedIn",
            url = "https://www.linkedin.com",
            asDesktop = false,
            category = "Social",
            iconRes = R.drawable.ic_app_linkedin
        ),
        CuratedWebApp(
            id = "com.pinterest",
            name = "Pinterest",
            url = "https://www.pinterest.com",
            asDesktop = false,
            category = "Social",
            iconRes = R.drawable.ic_app_pinterest
        ),
        CuratedWebApp(
            id = "com.snapchat.android",
            name = "Snapchat",
            url = "https://web.snapchat.com",
            asDesktop = false,
            category = "Social",
            iconRes = R.drawable.ic_app_snapchat
        ),

        // AI & Search
        CuratedWebApp(
            id = "com.openai.chatgpt",
            name = "ChatGPT",
            url = "https://chatgpt.com",
            asDesktop = false,
            category = "AI",
            iconRes = R.drawable.ic_app_chatgpt
        ),
        CuratedWebApp(
            id = "com.google.android.googlequicksearchbox",
            name = "Google",
            url = "https://www.google.com",
            asDesktop = false,
            alternativePackages = listOf(
                "com.android.chrome",
                "com.chrome.beta",
                "com.chrome.dev",
                "com.chrome.canary",
                "org.chromium.chrome",
                "org.mozilla.firefox",
                "com.brave.browser",
                "com.sec.android.app.sbrowser",
                "com.opera.browser"
            ),
            category = "Search",
            iconRes = R.drawable.ic_app_google
        ),
        CuratedWebApp(
            id = "com.anthropic.claude",
            name = "Claude",
            url = "https://claude.ai",
            asDesktop = false,
            category = "AI",
            iconRes = R.drawable.ic_app_claude
        ),
        CuratedWebApp(
            id = "ai.perplexity.app.android",
            name = "Perplexity",
            url = "https://www.perplexity.ai",
            asDesktop = false,
            category = "AI",
            iconRes = R.drawable.ic_app_perplexity
        ),
        CuratedWebApp(
            id = "com.microsoft.emmx",
            name = "Bing",
            url = "https://www.bing.com",
            asDesktop = false,
            category = "Search",
            iconRes = R.drawable.ic_app_bing
        ),

        // Media & Entertainment
        CuratedWebApp(
            id = "com.google.android.youtube",
            name = "YouTube",
            url = "https://m.youtube.com",
            asDesktop = false,
            alternativePackages = listOf("com.google.android.youtube.tv"),
            category = "Media",
            iconRes = R.drawable.ic_app_youtube
        ),
        CuratedWebApp(
            id = "com.google.android.apps.youtube.music",
            name = "YouTube Music",
            url = "https://music.youtube.com",
            asDesktop = false,
            category = "Media",
            iconRes = R.drawable.ic_app_ytmusic
        ),
        CuratedWebApp(
            id = "com.spotify.music",
            name = "Spotify",
            url = "https://open.spotify.com",
            asDesktop = false,
            alternativePackages = listOf("com.spotify.lite"),
            category = "Media",
            iconRes = R.drawable.ic_app_spotify
        ),
        CuratedWebApp(
            id = "com.netflix.mediaclient",
            name = "Netflix",
            url = "https://www.netflix.com",
            asDesktop = false,
            category = "Media",
            iconRes = R.drawable.ic_app_netflix
        ),
        CuratedWebApp(
            id = "tv.twitch.android.app",
            name = "Twitch",
            url = "https://m.twitch.tv",
            asDesktop = false,
            category = "Media",
            iconRes = R.drawable.ic_app_twitch
        ),
        CuratedWebApp(
            id = "com.soundcloud.android",
            name = "SoundCloud",
            url = "https://m.soundcloud.com",
            asDesktop = false,
            category = "Media",
            iconRes = R.drawable.ic_app_soundcloud
        ),
        CuratedWebApp(
            id = "com.jio.media.ondemand",
            name = "JioCinema",
            url = "https://www.jiocinema.com",
            asDesktop = false,
            category = "Media",
            iconRes = R.drawable.ic_app_jiocinema
        ),
        CuratedWebApp(
            id = "in.startv.hotstar",
            name = "Hotstar",
            url = "https://www.hotstar.com",
            asDesktop = false,
            category = "Media",
            iconRes = R.drawable.ic_app_hotstar
        ),

        // Google Ecosystem & Productivity
        CuratedWebApp(
            id = "com.google.android.apps.maps",
            name = "Google Maps",
            url = "https://maps.google.com",
            asDesktop = false,
            alternativePackages = listOf("com.google.android.apps.mapslite"),
            category = "Productivity",
            iconRes = R.drawable.ic_app_maps
        ),
        CuratedWebApp(
            id = "com.google.android.gm",
            name = "Gmail",
            url = "https://mail.google.com",
            asDesktop = false,
            category = "Productivity",
            iconRes = R.drawable.ic_app_gmail
        ),
        CuratedWebApp(
            id = "com.google.android.keep",
            name = "Google Keep",
            url = "https://keep.google.com",
            asDesktop = false,
            category = "Productivity",
            iconRes = R.drawable.ic_app_keep
        ),
        CuratedWebApp(
            id = "com.google.android.apps.docs",
            name = "Google Drive",
            url = "https://drive.google.com",
            asDesktop = false,
            category = "Productivity",
            iconRes = R.drawable.ic_app_drive
        ),
        CuratedWebApp(
            id = "com.google.android.apps.docs.editors.docs",
            name = "Google Docs",
            url = "https://docs.google.com",
            asDesktop = false,
            category = "Productivity",
            iconRes = R.drawable.ic_app_docs
        ),
        CuratedWebApp(
            id = "com.google.android.apps.docs.editors.sheets",
            name = "Google Sheets",
            url = "https://sheets.google.com",
            asDesktop = false,
            category = "Productivity",
            iconRes = R.drawable.ic_app_sheets
        ),
        CuratedWebApp(
            id = "com.google.android.apps.photos",
            name = "Google Photos",
            url = "https://photos.google.com",
            asDesktop = false,
            category = "Productivity",
            iconRes = R.drawable.ic_app_photos
        ),
        CuratedWebApp(
            id = "com.google.android.calendar",
            name = "Google Calendar",
            url = "https://calendar.google.com",
            asDesktop = false,
            category = "Productivity",
            iconRes = R.drawable.ic_app_calendar
        ),
        CuratedWebApp(
            id = "com.google.android.apps.translate",
            name = "Google Translate",
            url = "https://translate.google.com",
            asDesktop = false,
            category = "Productivity",
            iconRes = R.drawable.ic_app_translate
        ),
        CuratedWebApp(
            id = "com.github.android",
            name = "GitHub",
            url = "https://github.com",
            asDesktop = false,
            category = "Productivity",
            iconRes = R.drawable.ic_app_github
        ),
        CuratedWebApp(
            id = "notion.id",
            name = "Notion",
            url = "https://www.notion.so",
            asDesktop = false,
            category = "Productivity",
            iconRes = R.drawable.ic_app_notion
        ),
        CuratedWebApp(
            id = "com.canva.editor",
            name = "Canva",
            url = "https://www.canva.com",
            asDesktop = false,
            category = "Productivity",
            iconRes = R.drawable.ic_app_canva
        ),
        CuratedWebApp(
            id = "com.Slack",
            name = "Slack",
            url = "https://app.slack.com",
            asDesktop = false,
            category = "Productivity",
            iconRes = R.drawable.ic_app_slack
        ),
        CuratedWebApp(
            id = "org.wikipedia",
            name = "Wikipedia",
            url = "https://m.wikipedia.org",
            asDesktop = false,
            alternativePackages = listOf("org.wikipedia.beta"),
            category = "Knowledge",
            iconRes = R.drawable.ic_app_wikipedia
        ),
        CuratedWebApp(
            id = "com.medium.reader",
            name = "Medium",
            url = "https://medium.com",
            asDesktop = false,
            category = "Knowledge",
            iconRes = R.drawable.ic_app_medium
        ),
        CuratedWebApp(
            id = "com.quora.android",
            name = "Quora",
            url = "https://www.quora.com",
            asDesktop = false,
            category = "Knowledge",
            iconRes = R.drawable.ic_app_quora
        ),
        CuratedWebApp(
            id = "com.duolingo",
            name = "Duolingo",
            url = "https://www.duolingo.com",
            asDesktop = false,
            category = "Knowledge",
            iconRes = R.drawable.ic_app_duolingo
        ),

        // Shopping & Lifestyle
        CuratedWebApp(
            id = "com.amazon.mShop.android.shopping",
            name = "Amazon",
            url = "https://www.amazon.com",
            asDesktop = false,
            alternativePackages = listOf("in.amazon.mShop.android.shopping"),
            category = "Shopping",
            iconRes = R.drawable.ic_app_amazon
        ),
        CuratedWebApp(
            id = "com.flipkart.android",
            name = "Flipkart",
            url = "https://www.flipkart.com",
            asDesktop = false,
            category = "Shopping",
            iconRes = R.drawable.ic_app_flipkart
        ),
        CuratedWebApp(
            id = "in.swiggy.android",
            name = "Swiggy",
            url = "https://www.swiggy.com",
            asDesktop = false,
            category = "Lifestyle",
            iconRes = R.drawable.ic_app_swiggy
        ),
        CuratedWebApp(
            id = "com.application.zomato",
            name = "Zomato",
            url = "https://www.zomato.com",
            asDesktop = false,
            category = "Lifestyle",
            iconRes = R.drawable.ic_app_zomato
        ),
        CuratedWebApp(
            id = "com.myntra.android",
            name = "Myntra",
            url = "https://www.myntra.com",
            asDesktop = false,
            category = "Shopping",
            iconRes = R.drawable.ic_app_myntra
        ),
        CuratedWebApp(
            id = "com.bt.bms",
            name = "BookMyShow",
            url = "https://in.bookmyshow.com",
            asDesktop = false,
            category = "Lifestyle",
            iconRes = R.drawable.ic_app_bookmyshow
        ),
        CuratedWebApp(
            id = "com.cricbuzz.android",
            name = "Cricbuzz",
            url = "https://m.cricbuzz.com",
            asDesktop = false,
            category = "Lifestyle",
            iconRes = R.drawable.ic_app_cricbuzz
        )
    )

    private val CATALOG: Map<String, WebAppInfo> = buildMap {
        for (app in CURATED_APPS) {
            val info = WebAppInfo(app.name, app.url, app.asDesktop, app.iconRes)
            put(app.id, info)
            for (alt in app.alternativePackages) {
                put(alt, info)
            }
        }
    }

    fun findCuratedApp(packageName: String): CuratedWebApp? {
        return CURATED_APPS.firstOrNull {
            it.id == packageName || it.alternativePackages.contains(packageName)
        }
    }

    /**
     * Resolves an app's web version:
     * 1. Check curated catalog.
     * 2. If not found, inspect Android OS App Links / Intent Filters for registered web domains.
     */
    fun resolveWebApp(context: Context, packageName: String): WebAppInfo? {
        val catalogItem = CATALOG[packageName]
        if (catalogItem != null) {
            return catalogItem
        }
        return discoverDynamicWebDomain(context, packageName)
    }

    /**
     * Checks if an app has a known or discoverable web version.
     */
    fun hasWebVersion(context: Context, packageName: String): Boolean {
        if (CATALOG.containsKey(packageName)) return true
        return discoverDynamicWebDomain(context, packageName) != null
    }

    fun discoverDynamicWebDomain(context: Context, packageName: String): WebAppInfo? {
        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://")).apply {
                `package` = packageName
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            val matches = pm.queryIntentActivities(intent, PackageManager.GET_RESOLVED_FILTER)
            for (match in matches) {
                val filter = match.filter ?: continue
                val count = filter.countDataAuthorities()
                for (i in 0 until count) {
                    val auth = filter.getDataAuthority(i)
                    val host = auth?.host
                    if (!host.isNullOrEmpty() && !host.contains("localhost") && host.contains(".")) {
                        val cleanHost = host.removePrefix("*.")
                        val label = match.loadLabel(pm)?.toString() ?: cleanHost
                        return WebAppInfo(label, "https://$cleanHost", asDesktop = false, iconRes = R.drawable.ic_browser)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private val KNOWN_LANDSCAPE_APPS: Set<String> = setOf(
        "com.dts.freefireth",
        "com.dts.freefiremax",
        "com.pubg.imobile",
        "com.pubg.krmobile",
        "com.tencent.ig",
        "com.vng.pubgmobile",
        "com.activision.callofduty.shooter",
        "com.garena.game.codm",
        "com.epicgames.fortnite",
        "com.ea.gp.apexmobile",
        "com.miHoYo.GenshinImpact",
        "com.HoYoverse.hkrpgoversea",
        "com.supercell.clashofclans",
        "com.supercell.brawlstars",
        "com.supercell.clashroyale",
        "com.gameloft.android.ANMP.GloftA9HM",
        "com.gameloft.android.ANMP.GloftA8HM",
        "com.roblox.client",
        "com.mojang.minecraftpe",
        "com.ea.game.nfs14_row",
        "com.crafting.and.building",
        "com.netease.lztgglobal",
        "com.carxtech.sr"
    )

    /**
     * Checks if an app is a landscape-oriented game or widescreen application.
     */
    fun isLandscapeApp(context: Context, packageName: String): Boolean {
        if (KNOWN_LANDSCAPE_APPS.contains(packageName)) return true

        return try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(packageName) ?: return false
            val component = launchIntent.component ?: return false
            val activityInfo = pm.getActivityInfo(component, 0)
            val orientation = activityInfo.screenOrientation
            orientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
            orientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
            orientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE ||
            orientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        } catch (e: Exception) {
            false
        }
    }
}
