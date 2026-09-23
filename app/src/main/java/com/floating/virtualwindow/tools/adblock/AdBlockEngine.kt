package com.floating.virtualwindow.tools.adblock

import android.net.Uri
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger
import android.webkit.WebResourceResponse

object AdBlockEngine {

    val blockedCount = AtomicInteger(0)

    // Precompiled high-priority ad, telemetry, and tracking root domains
    private val BLOCKED_DOMAINS = hashSetOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "adservice.google.co.in",
        "adservice.google.de",
        "adcolony.com",
        "applovin.com",
        "unityads.unity3d.com",
        "vungle.com",
        "ironsrc.com",
        "taboola.com",
        "outbrain.com",
        "scorecardresearch.com",
        "quantserve.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "exoclick.com",
        "adnxs.com",
        "moatads.com",
        "serving-sys.com",
        "criteo.com",
        "criteo.net",
        "rubiconproject.com",
        "pubmatic.com",
        "openx.net",
        "smartadserver.com",
        "casalemedia.com",
        "bidswitch.net",
        "inmobi.com",
        "flurry.com",
        "chartbeat.com",
        "hotjar.com",
        "clarity.ms",
        "branch.io",
        "appsflyer.com",
        "adjust.com",
        "kochava.com",
        "admob.com",
        "advertising.com",
        "adroll.com",
        "adform.net",
        "yandex.ru/ads",
        "an.yandex.ru",
        "adzerk.net",
        "trafficjunky.net",
        "adsterra.com",
        "zergnet.com",
        "mgid.com",
        "revcontent.com"
    )

    // Generic ad path keywords for aggressive popup/banner blocking
    private val AD_PATH_KEYWORDS = arrayOf(
        "/ads/", "/ad-banner/", "/banner-ads/", "google_ads", "ad_frame", "ad_type="
    )

    fun shouldBlock(url: String): Boolean {
        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false

            // Check against known domains
            for (blocked in BLOCKED_DOMAINS) {
                if (host == blocked || host.endsWith(".$blocked")) {
                    blockedCount.incrementAndGet()
                    return true
                }
            }

            // Check obvious ad path triggers for 3rd-party origins
            val path = uri.path?.lowercase() ?: ""
            if (path.isNotEmpty()) {
                for (kw in AD_PATH_KEYWORDS) {
                    if (path.contains(kw) && !host.contains("youtube.com") && !host.contains("google.com")) {
                        blockedCount.incrementAndGet()
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
        return false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }

    fun getCosmeticAdHidingScript(): String {
        return """
            (function() {
                try {
                    var css = '.ad, .ads, .advert, .advertisement, .ad-banner, .ad-container, .adsbygoogle, [id^="google_ads_"], [class*="ad-unit"], [class*="sponsored"], iframe[src*="doubleclick"], iframe[src*="googlesyndication"], div[data-ad-slot], .ytp-ad-overlay-container, .video-ads, .ytp-ad-module, .ad-showing { display: none !important; opacity: 0 !important; pointer-events: none !important; }';
                    var head = document.head || document.getElementsByTagName('head')[0];
                    if (head) {
                        var style = document.createElement('style');
                        style.type = 'text/css';
                        style.appendChild(document.createTextNode(css));
                        head.appendChild(style);
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }
}
