package com.venice.spy

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object Api {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val base get() = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key get() = BuildConfig.SUPABASE_KEY

    private fun req(url: String) = Request.Builder().url(url)
        .addHeader("apikey", key)
        .addHeader("Authorization", "Bearer $key")

    fun post(table: String, json: String, onConflict: String? = null): Boolean {
        val url = "$base/rest/v1/$table" + (onConflict?.let { "?on_conflict=$it" } ?: "")
        val r = req(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(json.toRequestBody(JSON)).build()
        return try {
            client.newCall(r).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    fun patch(pathAndQuery: String, json: String): Boolean {
        val r = req("$base/rest/v1/$pathAndQuery")
            .addHeader("Content-Type", "application/json")
            .addHeader("Prefer", "return=minimal")
            .patch(json.toRequestBody(JSON)).build()
        return try {
            client.newCall(r).execute().use { it.isSuccessful }
        } catch (e: Exception) { false }
    }

    fun get(pathAndQuery: String): String? {
        val r = req("$base/rest/v1/$pathAndQuery")
            .addHeader("Accept", "application/json").get().build()
        return try {
            client.newCall(r).execute().use { if (it.isSuccessful) it.body?.string() else null }
        } catch (e: Exception) { null }
    }

    fun upload(path: String, file: File, mime: String): String? {
        val r = req("$base/storage/v1/object/media/$path")
            .addHeader("Content-Type", mime)
            .addHeader("x-upsert", "true")
            .post(file.asRequestBody(mime.toMediaType())).build()
        return try {
            if (client.newCall(r).execute().use { it.isSuccessful })
                "$base/storage/v1/object/public/media/$path" else null
        } catch (e: Exception) { null }
    }
}