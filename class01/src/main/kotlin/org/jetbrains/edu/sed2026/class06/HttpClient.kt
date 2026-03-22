package org.jetbrains.edu.sed2026.class06

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Flow
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * This is an implementation of the adapter design pattern.
 *
 * The idea is that we can use an interface of the standard HTTP Client API from JDK, but implement it using
 * a client that we need. This allows us to replace the HTTP Client easily.
 */
class OkHttpClientAdapter(private val okHttpClient: OkHttpClient) : HttpClient() {

    override fun cookieHandler(): Optional<CookieHandler> {
        TODO("Not implemented")
    }

    override fun connectTimeout(): Optional<Duration> {
        TODO("Not implemented")
    }

    override fun followRedirects(): Redirect {
        TODO("Not implemented")
    }

    override fun proxy(): Optional<ProxySelector> {
        TODO("Not implemented")
    }

    override fun sslContext(): SSLContext {
        TODO("Not implemented")
    }

    override fun sslParameters(): SSLParameters {
        TODO("Not implemented")
    }

    override fun authenticator(): Optional<Authenticator> {
        TODO("Not implemented")
    }

    override fun version(): Version {
        TODO("Not implemented")
    }

    override fun executor(): Optional<Executor> {
        TODO("Not implemented")
    }

    override fun <T : Any?> send(
        request: HttpRequest,
        responseBodyHandler: HttpResponse.BodyHandler<T>
    ): HttpResponse<T> {
        // Convert JDK HttpRequest to OkHttp Request
        val okRequestBuilder = Request.Builder()
            .url(request.uri().toURL())

        // Copy headers
        request.headers().map().forEach { (name, values) ->
            values.forEach { value ->
                okRequestBuilder.addHeader(name, value)
            }
        }

        // Handle request body
        val bodyPublisher = request.bodyPublisher()
        if (bodyPublisher.isPresent && request.method() == "POST") {
            val body = StringBuilder()
            bodyPublisher.get().subscribe(object : Flow.Subscriber<ByteBuffer> {
                override fun onSubscribe(subscription: Flow.Subscription) {
                    subscription.request(Long.MAX_VALUE)
                }
                override fun onNext(item: ByteBuffer) {
                    body.append(String(item.array()))
                }
                override fun onError(throwable: Throwable) {}
                override fun onComplete() {}
            })
            okRequestBuilder.post(
                RequestBody.create(
                    "application/json".toMediaType(),
                    body.toString()
                ))
        } else {
            okRequestBuilder.method(request.method(), null)
        }

        val okRequest = okRequestBuilder.build()
        val okResponse = okHttpClient.newCall(okRequest).execute()

        // Create a simplified HttpResponse wrapper
        @Suppress("UNCHECKED_CAST")
        return OkHttpResponseWrapper(okResponse, request) as HttpResponse<T>
    }

    override fun <T : Any?> sendAsync(
        request: HttpRequest,
        responseBodyHandler: HttpResponse.BodyHandler<T>
    ): CompletableFuture<HttpResponse<T>> {
        throw UnsupportedOperationException("Not implemented - ISP violation")
    }

    override fun <T : Any?> sendAsync(
        request: HttpRequest,
        responseBodyHandler: HttpResponse.BodyHandler<T>,
        pushPromiseHandler: HttpResponse.PushPromiseHandler<T>
    ): CompletableFuture<HttpResponse<T>> {
        throw UnsupportedOperationException("Not implemented - ISP violation")
    }
}

/**
 * Minimal HttpResponse implementation wrapping OkHttp Response
 */
class OkHttpResponseWrapper(
    private val okResponse: Response,
    private val originalRequest: HttpRequest
) : HttpResponse<String> {

    override fun statusCode(): Int = okResponse.code

    override fun request(): HttpRequest = originalRequest

    override fun previousResponse(): Optional<HttpResponse<String>> = Optional.empty()

    override fun headers(): HttpHeaders {
        val headerMap = mutableMapOf<String, List<String>>()
        okResponse.headers.names().forEach { name ->
            headerMap[name] = okResponse.headers.values(name)
        }
        return HttpHeaders.of(headerMap) { _, _ -> true }
    }

    override fun body(): String = okResponse.body?.string() ?: ""

    override fun sslSession(): Optional<SSLSession> = Optional.empty()

    override fun uri(): URI = originalRequest.uri()

    override fun version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
}
