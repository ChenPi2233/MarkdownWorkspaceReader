package com.example.mdworkspace.core.network

import android.util.Base64
import com.example.mdworkspace.domain.model.RepositoryConfig
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class GitHubApi(
    private val client: OkHttpClient
) {
    suspend fun getDirectory(config: RepositoryConfig, path: String): List<GitHubContentItem> {
        val responseBody = getContentsBody(config = config, path = path)
        val array = JSONArray(responseBody)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(item.toContentItem())
            }
        }
    }

    suspend fun getMarkdownFile(config: RepositoryConfig, path: String): GitHubMarkdownFile {
        val responseBody = getContentsBody(config = config, path = path)
        val json = JSONObject(responseBody)
        val encodedContent = json.optString("content").replace("\n", "")
        val decoded = Base64.decode(encodedContent, Base64.DEFAULT).toString(Charsets.UTF_8)
        return GitHubMarkdownFile(
            path = json.getString("path"),
            name = json.getString("name"),
            sha = json.optString("sha").takeUnless { it.isBlank() },
            downloadUrl = json.optString("download_url").takeUnless { it.isBlank() || it == "null" },
            content = decoded
        )
    }

    private suspend fun getContentsBody(config: RepositoryConfig, path: String): String {
        val encodedPath = encodePath(path)
        val url = buildString {
            append("https://api.github.com/repos/")
            append(encodePath(config.owner))
            append("/")
            append(encodePath(config.repo))
            append("/contents")
            if (encodedPath.isNotBlank()) {
                append("/")
                append(encodedPath)
            }
            append("?ref=")
            append(urlEncode(config.branch))
        }
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .apply {
                if (config.token.isNotBlank()) {
                    header("Authorization", "Bearer ${config.token}")
                }
            }
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("GitHub API ${response.code}: ${extractMessage(body)}")
                }
                body
            }
        }
    }

    private fun JSONObject.toContentItem(): GitHubContentItem {
        return GitHubContentItem(
            name = getString("name"),
            path = getString("path"),
            type = getString("type"),
            sha = optString("sha").takeUnless { it.isBlank() },
            downloadUrl = optString("download_url").takeUnless { it.isBlank() || it == "null" },
            htmlUrl = optString("html_url").takeUnless { it.isBlank() }
        )
    }

    private fun extractMessage(body: String): String {
        return runCatching {
            JSONObject(body).optString("message").ifBlank { body }
        }.getOrElse { body.ifBlank { "request failed" } }
    }

    private fun encodePath(path: String): String {
        return path.trim('/')
            .split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") { urlEncode(it) }
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
