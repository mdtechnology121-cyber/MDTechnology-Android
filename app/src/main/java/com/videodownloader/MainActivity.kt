package com.videodownloader

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.videodownloader.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var downloadJob: Job? = null
    private lateinit var prefs: android.content.SharedPreferences
    private val LICENSE_URL = "YOUR_APPS_SCRIPT_URL_HERE"
    private val DEVICE_ID: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            prefs = getSharedPreferences("license", Context.MODE_PRIVATE)
            val savedKey = prefs.getString("license_key", "")
            if (savedKey.isNullOrEmpty()) {
                showLicenseDialog()
            } else {
                verifyLicense(savedKey)
            }

            binding.btnDownload.setOnClickListener {
                val url = binding.etUrl.text.toString().trim()
                if (url.isNotEmpty()) startExtract(url)
                else showStatus("Enter a video URL")
            }

            binding.ivAbout.setOnClickListener { showAboutDialog() }
            binding.tvLegal.setOnClickListener { showLegalDialog() }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            throw e
        }
    }

    private fun showAboutDialog() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (_: PackageManager.NameNotFoundException) { "1.0" }

        MaterialAlertDialogBuilder(this)
            .setIcon(R.mipmap.ic_launcher)
            .setTitle("MD TECHNOLOGY")
            .setMessage("Version $version\nVISION BEYOND LIMITS\n\nDownload Instagram reels and videos directly to your device.\nNo login required.\n\nContact: mdtechnology121@gmail.com")
            .setPositiveButton("Share") { _, _ ->
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "MD TECHNOLOGY - VISION BEYOND LIMITS | Download Instagram videos easily!")
                }
                startActivity(Intent.createChooser(intent, "Share via"))
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showLegalDialog() {
        val items = arrayOf("Privacy Policy", "Terms of Service", "Third-Party Licenses")
        MaterialAlertDialogBuilder(this)
            .setTitle("Legal")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showHtmlPage("Privacy Policy", "privacy_policy.html")
                    1 -> showHtmlPage("Terms of Service", "terms_of_service.html")
                    2 -> showHtmlPage("Licenses", "licenses.html")
                }
            }
            .setPositiveButton("Contact: mdtechnology121@gmail.com", null)
            .show()
    }

    private fun showHtmlPage(title: String, assetFile: String) {
        try {
            val html = assets.open(assetFile).bufferedReader().use { it.readText() }
            val webView = WebView(this)
            webView.settings.javaScriptEnabled = false
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(webView)
                .setPositiveButton("Close", null)
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not load $title", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startExtract(url: String) {
        when {
            url.contains("instagram.com") -> {
                val shortcode = extractShortcode(url)
                if (shortcode != null) {
                    showStatus("Extracting video...")
                    binding.progressContainer.visibility = android.view.View.VISIBLE
                    binding.btnDownload.isEnabled = false
                    downloadJob?.cancel()
                    downloadJob = CoroutineScope(Dispatchers.IO).launch {
                        extractInstagramVideo(shortcode)
                    }
                } else showError("Invalid Instagram URL")
            }
            url.contains("facebook.com") || url.contains("fb.watch") -> {
                showStatus("Facebook not yet supported via API")
            }
            else -> {
                binding.progressContainer.visibility = android.view.View.VISIBLE
                binding.btnDownload.isEnabled = false
                CoroutineScope(Dispatchers.IO).launch {
                    extractDirectVideo(url)
                }
            }
        }
    }

    private fun extractShortcode(url: String): String? {
        val regex = Regex("instagram\\.com/(?:p|reel|reels|tv)/([a-zA-Z0-9_-]+)")
        return regex.find(url)?.groupValues?.get(1)
    }

    private suspend fun extractInstagramVideo(shortcode: String) {
        try {
                val variables = JSONObject().apply {
                    put("shortcode", shortcode)
                    put("fetch_comment_count", JSONObject.NULL)
                    put("fetch_related_profile_media_count", JSONObject.NULL)
                    put("parent_comment_count", JSONObject.NULL)
                    put("child_comment_count", JSONObject.NULL)
                    put("fetch_like_count", JSONObject.NULL)
                    put("fetch_tagged_user_count", JSONObject.NULL)
                    put("fetch_preview_comment_count", JSONObject.NULL)
                    put("has_threaded_comments", false)
                    put("hoisted_comment_id", JSONObject.NULL)
                    put("hoisted_reply_id", JSONObject.NULL)
                }

                val formBody = FormBody.Builder()
                    .add("av", "0")
                    .add("__d", "www")
                    .add("__user", "0")
                    .add("__a", "1")
                    .add("__req", "3")
                    .add("__hs", "19624.HYP:instagram_web_pkg.2.1..0.0")
                    .add("dpr", "3")
                    .add("__ccg", "UNKNOWN")
                    .add("__rev", "1008824440")
                    .add("__s", "xf44ne:zhh75g:xr51e7")
                    .add("__hsi", "7282217488877343271")
                    .add("__dyn", "7xeUmwlEnwn8K2WnFw9-2i5U4e0yoW3q32360CEbo1nEhw2nVE4W0om78b87C0yE5ufz81s8hwGwQwoEcE7O2l0Fwqo31w9a9x-0z8-U2zxe2GewGwso88cobEaU2eUlwhEe87q7-0iK2S3qazo7u1xwIw8O321LwTwKG1pg661pwr86C1mwraCg")
                    .add("__csr", "gZ3yFmJkillQvV6ybimnG8AmhqujGbLADgjyEOWz49z9XDlAXBJpC7Wy-vQTSvUGWGh5u8KibG44dBiigrgjDxGjU0150Q0848azk48N09C02IR0go4SaR70r8owyg9pU0V23hwiA0LQczA48S0f-x-27o05NG0fkw")
                    .add("__comet_req", "7")
                    .add("lsd", "AVqbxe3J_YA")
                    .add("jazoest", "2957")
                    .add("__spin_r", "1008824440")
                    .add("__spin_b", "trunk")
                    .add("__spin_t", "1695523385")
                    .add("fb_api_caller_class", "RelayModern")
                    .add("fb_api_req_friendly_name", "PolarisPostActionLoadPostQueryQuery")
                    .add("variables", variables.toString())
                    .add("server_timestamps", "true")
                    .add("doc_id", "10015901848480474")
                    .build()

                val request = Request.Builder()
                    .url("https://www.instagram.com/api/graphql")
                    .post(formBody)
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("X-FB-Friendly-Name", "PolarisPostActionLoadPostQueryQuery")
                    .header("X-CSRFToken", "RVDUooU5MYsBbS1CNN3CzVAuEP8oHB52")
                    .header("X-IG-App-ID", "1217981644879628")
                    .header("X-FB-LSD", "AVqbxe3J_YA")
                    .header("X-ASBD-ID", "129477")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 11; SAMSUNG SM-G973U) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/14.2 Chrome/87.0.4280.141 Mobile Safari/537.36")
                    .header("Origin", "https://www.instagram.com")
                    .header("Referer", "https://www.instagram.com/")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw Exception("API returned ${response.code}")
                }

                val body = response.body?.string() ?: throw Exception("Empty response")
                val json = JSONObject(body)

                val media = json.getJSONObject("data").getJSONObject("xdt_shortcode_media")
                val isVideo = media.optBoolean("is_video", false)
                if (!isVideo) throw Exception("This post is not a video")

                val videoUrl = media.getString("video_url")
                val dimensions = media.optJSONObject("dimensions")
                val width = dimensions?.optInt("width", 0) ?: 0
                val height = dimensions?.optInt("height", 0) ?: 0

                withContext(Dispatchers.Main) {
                    showStatus("Found video: ${width}x$height")
                }

                downloadVideo(videoUrl)

            } catch (e: Exception) {
            withContext(Dispatchers.Main) { showError("Extraction failed: ${e.message}") }
        }
    }

    private suspend fun extractDirectVideo(url: String) {
        try {
            showStatus("Fetching page...")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            val patterns = listOf(
                Regex("""<meta\s+[^>]*property\s*=\s*["']og:video["'][^>]*content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                Regex("""<meta\s+[^>]*property\s*=\s*["']og:video:url["'][^>]*content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                Regex("""(?:video_url|contentUrl|download_url)\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE),
                Regex("""https?://[^"'\s<>]*\.(?:mp4|webm|m3u8)(?:\?[^"'\s<>]*)?""", RegexOption.IGNORE_CASE)
            )

            for (pattern in patterns) {
                val match = pattern.find(html)
                if (match != null) {
                    val videoUrl = match.groupValues.last()
                    if (videoUrl.startsWith("http")) {
                        withContext(Dispatchers.Main) { downloadVideo(videoUrl) }
                        return
                    }
                }
            }

            withContext(Dispatchers.Main) { showError("Could not find video in page") }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { showError(e.message ?: "Error fetching page") }
        }
    }

    private fun downloadVideo(videoUrl: String) {
        showStatus("Downloading...")
        downloadJob?.cancel()
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url(videoUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .header("Referer", "https://www.instagram.com/")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("Server returned ${response.code}")

                val body = response.body ?: throw Exception("Empty response")
                val ext = if (videoUrl.contains(".m3u8")) "ts" else "mp4"
                val fileName = "Video_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.$ext"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToMediaStore(fileName, body.byteStream(), body.contentLength())
                } else {
                    saveToLegacy(fileName, body.byteStream())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showError(e.message ?: "Download failed") }
            }
        }
    }

    private suspend fun saveToMediaStore(fileName: String, stream: InputStream, totalBytes: Long) {
        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VideoDownloader")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("Failed to create file in Movies/VideoDownloader")

        try {
            val outputStream = resolver.openOutputStream(uri)
                ?: throw Exception("Failed to open output stream")
            outputStream.use { os ->
                val buffer = ByteArray(8192)
                var totalRead = 0L
                var bytesRead: Int

                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    os.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) {
                        val pct = (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        withContext(Dispatchers.Main) { showProgress(pct) }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val update = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                resolver.update(uri, update, null, null)
            }

            withContext(Dispatchers.Main) { showComplete(fileName) }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.delete(uri, null, null)
            }
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToLegacy(fileName: String, stream: InputStream) {
        val folder = java.io.File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VideoDownloader")
        folder.mkdirs()
        val file = java.io.File(folder, fileName)
        file.outputStream().use { os -> stream.copyTo(os) }
        runOnUiThread { showComplete(fileName) }
    }

    private fun showProgress(percent: Float) {
        binding.progressContainer.visibility = android.view.View.VISIBLE
        binding.btnDownload.isEnabled = false
        val pct = (percent * 100).toInt().coerceIn(0, 100)
        binding.progressBar.progress = pct
        binding.tvPercentage.text = "$pct%"
        binding.tvStatus.text = "Downloading..."
    }

    private fun showComplete(fileName: String) {
        binding.progressContainer.visibility = android.view.View.GONE
        binding.btnDownload.isEnabled = true
        binding.tvStatus.text = "Saved: $fileName"
        Toast.makeText(this, "Saved to Movies/VideoDownloader", Toast.LENGTH_SHORT).show()
    }

    private fun showError(msg: String) {
        binding.progressContainer.visibility = android.view.View.GONE
        binding.btnDownload.isEnabled = true
        binding.tvStatus.text = msg
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun showStatus(msg: String) {
        binding.tvStatus.text = msg
    }

    private fun showLicenseDialog() {
        val input = EditText(this).apply {
            hint = "XXXX-XXXX-XXXX-XXXX"
            setText("")
            textSize = 18f
        }
        MaterialAlertDialogBuilder(this)
            .setIcon(R.mipmap.ic_launcher)
            .setTitle("Activate Pro")
            .setMessage("Enter your license key to activate MD TECHNOLOGY Pro")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Activate") { _, _ ->
                val key = input.text.toString().trim()
                if (key.length >= 16) {
                    verifyLicense(key)
                } else {
                    Toast.makeText(this, "Invalid license key", Toast.LENGTH_SHORT).show()
                    showLicenseDialog()
                }
            }
            .setNegativeButton("Get License") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://mdtechnology121-cyber.github.io"))
                startActivity(intent)
                showLicenseDialog()
            }
            .show()
    }

    private fun verifyLicense(key: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "$LICENSE_URL?action=verify&licenseKey=$key&deviceId=$DEVICE_ID"
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                val json = JSONObject(response.body?.string() ?: "{}")
                if (json.optBoolean("valid")) {
                    prefs.edit().putString("license_key", key).apply()
                    withContext(Dispatchers.Main) {
                        showStatus("Pro activated")
                    }
                } else {
                    prefs.edit().remove("license_key").apply()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Invalid license key", Toast.LENGTH_LONG).show()
                        showLicenseDialog()
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Network error. Check connection.", Toast.LENGTH_LONG).show()
                    showLicenseDialog()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
    }
}
