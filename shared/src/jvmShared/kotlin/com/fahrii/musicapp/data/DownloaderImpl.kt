package com.fahrii.musicapp.data

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

class DownloaderImpl private constructor(builder: OkHttpClient.Builder) : Downloader() {

    private val client: OkHttpClient = builder
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = when {
            dataToSend != null -> dataToSend.toRequestBody()
            httpMethod == "POST" -> ByteArray(0).toRequestBody()
            else -> null
        }

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, requestBody)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        for ((headerName, headerValues) in headers) {
            if (headerValues.size > 1) {
                requestBuilder.removeHeader(headerName)
                headerValues.forEach { requestBuilder.addHeader(headerName, it) }
            } else if (headerValues.size == 1) {
                requestBuilder.header(headerName, headerValues[0])
            }
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }

            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString()
            )
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

        @Volatile
        private var instance: DownloaderImpl? = null

        fun getInstance(): DownloaderImpl =
            instance ?: synchronized(this) {
                instance ?: DownloaderImpl(OkHttpClient.Builder()).also { instance = it }
            }
    }
}