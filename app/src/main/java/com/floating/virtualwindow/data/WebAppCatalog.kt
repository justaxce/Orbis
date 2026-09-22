package com.floating.virtualwindow.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

data class WebAppInfo(
    val title: String,
    val url: String,
    val asDesktop: Boolean = false
)

object WebAppCatalog {

    /**
     * Tier 1: Curated High-Performance Catalog for 50+ popular services.
     * Maps Android package names to verified mobile web endpoints.
     */
    private val CATALOG: Map<String, WebAppInfo> = mapOf(
        // Social & Messaging
        "com.instagram.android" to WebAppInfo("Instagram", "https://www.instagram.com", asDesktop = false),
        "com.instagram.lite" to WebAppInfo("Instagram", "https://www.instagram.com", asDesktop = false),
        "com.discord" to WebAppInfo("Discord", "https://discord.com/login", asDesktop = false),
        "com.discord.canary" to WebAppInfo("Discord", "https://discord.com/login", asDesktop = false),
        "com.discord.ptb" to WebAppInfo("Discord", "https://discord.com/login", asDesktop = false),
        // WhatsApp Web requires desktop user agent to initiate companion pairing
        "com.whatsapp" to WebAppInfo("WhatsApp Web", "https://web.whatsapp.com", asDesktop = true),
        "com.whatsapp.w4b" to WebAppInfo("WhatsApp Web", "https://web.whatsapp.com", asDesktop = true),
        "org.telegram.messenger" to WebAppInfo("Telegram", "https://web.telegram.org/a/", asDesktop = false),
        "org.telegram.messenger.web" to WebAppInfo("Telegram", "https://web.telegram.org/a/", asDesktop = false),
        "org.telegram.plus" to WebAppInfo("Telegram", "https://web.telegram.org/a/", asDesktop = false),
        "com.twitter.android" to WebAppInfo("X", "https://x.com", asDesktop = false),
        "com.twitter.android.lite" to WebAppInfo("X", "https://x.com", asDesktop = false),
        "com.reddit.frontpage" to WebAppInfo("Reddit", "https://www.reddit.com", asDesktop = false),
        "com.instagram.barcelona" to WebAppInfo("Threads", "https://www.threads.net", asDesktop = false),
        "com.facebook.katana" to WebAppInfo("Facebook", "https://m.facebook.com", asDesktop = false),
        "com.facebook.lite" to WebAppInfo("Facebook", "https://m.facebook.com", asDesktop = false),
        "com.facebook.orca" to WebAppInfo("Messenger", "https://www.messenger.com", asDesktop = false),
        "com.facebook.mlite" to WebAppInfo("Messenger", "https://www.messenger.com", asDesktop = false),
        "com.linkedin.android" to WebAppInfo("LinkedIn", "https://www.linkedin.com", asDesktop = false),
        "com.pinterest" to WebAppInfo("Pinterest", "https://www.pinterest.com", asDesktop = false),
        "com.snapchat.android" to WebAppInfo("Snapchat", "https://web.snapchat.com", asDesktop = false),

        // Media, Video & Audio
        "com.google.android.youtube" to WebAppInfo("YouTube", "https://m.youtube.com", asDesktop = false),
        "com.google.android.youtube.tv" to WebAppInfo("YouTube", "https://m.youtube.com", asDesktop = false),
        "com.google.android.apps.youtube.music" to WebAppInfo("YouTube Music", "https://music.youtube.com", asDesktop = false),
        "com.spotify.music" to WebAppInfo("Spotify", "https://open.spotify.com", asDesktop = false),
        "com.spotify.lite" to WebAppInfo("Spotify", "https://open.spotify.com", asDesktop = false),
        "tv.twitch.android.app" to WebAppInfo("Twitch", "https://m.twitch.tv", asDesktop = false),
        "com.soundcloud.android" to WebAppInfo("SoundCloud", "https://m.soundcloud.com", asDesktop = false),
        "com.netflix.mediaclient" to WebAppInfo("Netflix", "https://www.netflix.com", asDesktop = false),
        "com.jio.media.ondemand" to WebAppInfo("JioCinema", "https://www.jiocinema.com", asDesktop = false),
        "in.startv.hotstar" to WebAppInfo("Hotstar", "https://www.hotstar.com", asDesktop = false),

        // AI & Search
        "com.openai.chatgpt" to WebAppInfo("ChatGPT", "https://chatgpt.com", asDesktop = false),
        "com.anthropic.claude" to WebAppInfo("Claude", "https://claude.ai", asDesktop = false),
        "ai.perplexity.app.android" to WebAppInfo("Perplexity", "https://www.perplexity.ai", asDesktop = false),
        "com.google.android.googlequicksearchbox" to WebAppInfo("Google", "https://www.google.com", asDesktop = false),
        "com.android.chrome" to WebAppInfo("Google", "https://www.google.com", asDesktop = false),
        "com.chrome.beta" to WebAppInfo("Google", "https://www.google.com", asDesktop = false),
        "com.chrome.dev" to WebAppInfo("Google", "https://www.google.com", asDesktop = false),
        "com.chrome.canary" to WebAppInfo("Google", "https://www.google.com", asDesktop = false),
        "org.chromium.chrome" to WebAppInfo("Google", "https://www.google.com", asDesktop = false),
        "org.mozilla.firefox" to WebAppInfo("Browser", "https://www.google.com", asDesktop = false),
        "com.brave.browser" to WebAppInfo("Browser", "https://www.google.com", asDesktop = false),
        "com.microsoft.emmx" to WebAppInfo("Bing", "https://www.bing.com", asDesktop = false),
        "com.sec.android.app.sbrowser" to WebAppInfo("Browser", "https://www.google.com", asDesktop = false),
        "com.opera.browser" to WebAppInfo("Browser", "https://www.google.com", asDesktop = false),

        // Google Ecosystem & Productivity
        "com.google.android.apps.maps" to WebAppInfo("Google Maps", "https://maps.google.com", asDesktop = false),
        "com.google.android.apps.mapslite" to WebAppInfo("Google Maps", "https://maps.google.com", asDesktop = false),
        "com.google.android.keep" to WebAppInfo("Google Keep", "https://keep.google.com", asDesktop = false),
        "com.google.android.apps.docs" to WebAppInfo("Google Drive", "https://drive.google.com", asDesktop = false),
        "com.google.android.apps.docs.editors.docs" to WebAppInfo("Google Docs", "https://docs.google.com", asDesktop = false),
        "com.google.android.apps.docs.editors.sheets" to WebAppInfo("Google Sheets", "https://sheets.google.com", asDesktop = false),
        "com.google.android.apps.docs.editors.slides" to WebAppInfo("Google Slides", "https://slides.google.com", asDesktop = false),
        "com.google.android.apps.photos" to WebAppInfo("Google Photos", "https://photos.google.com", asDesktop = false),
        "com.google.android.apps.translate" to WebAppInfo("Google Translate", "https://translate.google.com", asDesktop = false),
        "com.google.android.calendar" to WebAppInfo("Google Calendar", "https://calendar.google.com", asDesktop = false),
        "com.google.android.gm" to WebAppInfo("Gmail", "https://mail.google.com", asDesktop = false),
        "com.github.android" to WebAppInfo("GitHub", "https://github.com", asDesktop = false),
        "notion.id" to WebAppInfo("Notion", "https://www.notion.so", asDesktop = false),
        "com.canva.editor" to WebAppInfo("Canva", "https://www.canva.com", asDesktop = false),
        "com.Slack" to WebAppInfo("Slack", "https://app.slack.com", asDesktop = false),
        "org.wikipedia" to WebAppInfo("Wikipedia", "https://m.wikipedia.org", asDesktop = false),
        "org.wikipedia.beta" to WebAppInfo("Wikipedia", "https://m.wikipedia.org", asDesktop = false),
        "com.medium.reader" to WebAppInfo("Medium", "https://medium.com", asDesktop = false),
        "com.quora.android" to WebAppInfo("Quora", "https://www.quora.com", asDesktop = false),
        "com.duolingo" to WebAppInfo("Duolingo", "https://www.duolingo.com", asDesktop = false),

        // Shopping & Lifestyle
        "in.amazon.mShop.android.shopping" to WebAppInfo("Amazon", "https://www.amazon.com", asDesktop = false),
        "com.amazon.mShop.android.shopping" to WebAppInfo("Amazon", "https://www.amazon.com", asDesktop = false),
        "com.flipkart.android" to WebAppInfo("Flipkart", "https://www.flipkart.com", asDesktop = false),
        "in.swiggy.android" to WebAppInfo("Swiggy", "https://www.swiggy.com", asDesktop = false),
        "com.application.zomato" to WebAppInfo("Zomato", "https://www.zomato.com", asDesktop = false),
        "com.myntra.android" to WebAppInfo("Myntra", "https://www.myntra.com", asDesktop = false),
        "com.bt.bms" to WebAppInfo("BookMyShow", "https://in.bookmyshow.com", asDesktop = false),
        "com.cricbuzz.android" to WebAppInfo("Cricbuzz", "https://m.cricbuzz.com", asDesktop = false)
    )

    /**
     * Resolves an app's web version:
     * 1. Check curated catalog.
     * 2. If not found, inspect Android OS App Links / Intent Filters for registered web domains.
     */
    fun resolveWebApp(context: Context, packageName: String): WebAppInfo? {
        // Tier 1: Static curated catalog
        val catalogItem = CATALOG[packageName]
        if (catalogItem != null) {
            return catalogItem
        }

        // Tier 2: Dynamic Intent-Filter discovery via Android PackageManager
        return discoverDynamicWebDomain(context, packageName)
    }

    /**
     * Checks if an app has a known or discoverable web version.
     */
    fun hasWebVersion(context: Context, packageName: String): Boolean {
        if (CATALOG.containsKey(packageName)) return true
        return discoverDynamicWebDomain(context, packageName) != null
    }

    private fun discoverDynamicWebDomain(context: Context, packageName: String): WebAppInfo? {
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
                        return WebAppInfo(label, "https://$cleanHost", asDesktop = false)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
