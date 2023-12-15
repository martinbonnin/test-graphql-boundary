#!/usr/bin/env kotlin

@file:DependsOn("com.squareup.okhttp3:mockwebserver:4.11.0")
@file:DependsOn("com.apollographql.apollo3:apollo-api-jvm:4.0.0-beta.3")
@file:DependsOn("com.squareup.okhttp3:okhttp:4.11.0")

import com.apollographql.apollo3.api.json.buildJsonString
import com.apollographql.apollo3.api.json.writeAny
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.BufferedSink
import java.io.File

if (File("router").exists().not()) {
    error("Please download a 'router' binary in the current directory first:\ncurl -sSL https://router.apollo.dev/download/nix/latest | sh")
}
val mockServer = MockWebServer()

mockServer.start(8080)

fun Any.toJsonString(): String {
    return buildJsonString {
        writeAny(this@toJsonString)
    }
}
fun response(value: String): String {
    return mapOf(
        "data" to mapOf(
            "foo" to value
        )
    ).toJsonString()
}

repeat(100) {
    mockServer.enqueue(MockResponse().apply {
        setResponseCode(200)
        setBody(response("\r\n--graphql"))
        addHeader("content-type", "application/json")
    })
}

println("MockServer listening at ${mockServer.url("/")}")

println("Starting router...")
val process = ProcessBuilder()
    .inheritIO()
    .command(File(".").resolve("router").absolutePath, "--dev", "--supergraph", "supergraph-schema.graphqls")
    .start()

Thread.sleep(2000)

println("Sending query to router...")

val response = Request.Builder()
    .post(object : RequestBody() {
        override fun contentType(): MediaType {
            return "application/json".toMediaType()
        }

        override fun writeTo(sink: BufferedSink) {
            mapOf(
                "query" to """
                    query ExampleQuery {
                      ...queryDetails @defer
                    }

                    fragment queryDetails on Query {
                      foo
                    }
                """.trimIndent()
            ).toJsonString()
                .let {
                    sink.writeUtf8(it)
                }
        }

    })
    .url("http://127.0.0.1:4000")
    .addHeader("accept", "multipart/mixed;boundary=\"toto\";deferSpec=20220824")
    .build()
    .let {
        OkHttpClient().newCall(it).execute()
    }

println(response.headers)
println(response.body!!.string())

process.waitFor()


