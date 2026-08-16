package com.ytmusic.downloader.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val method = request.httpMethod()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .url(request.url())

        request.headers().forEach { (name, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(name, value)
            }
        }

        if (method.equals("POST", ignoreCase = true) && dataToSend != null) {
            requestBuilder.post(dataToSend.toRequestBody())
        } else if (method.equals("HEAD", ignoreCase = true)) {
            requestBuilder.head()
        }

        val httpResponse = client.newCall(requestBuilder.build()).execute()

        return Response(
            httpResponse.code,
            httpResponse.message,
            httpResponse.headers.toMultimap(),
            httpResponse.body?.string(),
            httpResponse.request.url.toString()
        )
    }
}
