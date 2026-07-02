package org.liamjd.apiviaduct.sample

import com.amazonaws.services.lambda.runtime.ClientContext
import com.amazonaws.services.lambda.runtime.CognitoIdentity
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.exitProcess

/**
 * Entry point for the native Lambda binary.
 *
 * - Deployed to AWS Lambda (runtime `provided.al2023`), the AWS_LAMBDA_RUNTIME_API
 *   environment variable is set and this runs the custom runtime event loop.
 * - Run locally with no arguments, it executes a built-in self-test: sample API
 *   Gateway JSON payloads are deserialized, routed and the responses checked.
 */
fun main() {
    val runtimeApi = System.getenv("AWS_LAMBDA_RUNTIME_API")
    if (runtimeApi != null) {
        runtimeLoop(runtimeApi)
    } else {
        selfTest()
    }
}

private val handler = SampleRouter()

/**
 * The AWS Lambda custom runtime event loop: poll for the next invocation,
 * handle it, post the result. https://docs.aws.amazon.com/lambda/latest/dg/runtimes-api.html
 */
private fun runtimeLoop(runtimeApi: String): Nothing {
    val base = "http://$runtimeApi/2018-06-01/runtime"
    while (true) {
        val next = URL("$base/invocation/next").openConnection() as HttpURLConnection
        val requestId = next.getHeaderField("Lambda-Runtime-Aws-Request-Id")
        val eventBody = next.inputStream.bufferedReader().readText()

        val responseJson = try {
            val event = parseApiGatewayEvent(eventBody)
            serializeResponseEvent(handler.handleRequest(event, StubContext(requestId)))
        } catch (e: Exception) {
            serializeResponseEvent(
                com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent().apply {
                    statusCode = 500
                    body = "Internal error: ${e.message}"
                }
            )
        }

        val post = URL("$base/invocation/$requestId/response").openConnection() as HttpURLConnection
        post.requestMethod = "POST"
        post.doOutput = true
        post.outputStream.use { it.write(responseJson.toByteArray()) }
        post.inputStream.use { it.readBytes() } // drain to complete the request
    }
}

/**
 * Local harness: prove that raw API Gateway JSON payloads round-trip through
 * deserialization, routing and response serialization. Exits non-zero on failure
 * so it can gate CI.
 */
private fun selfTest() {
    val cases = listOf(
        Triple(
            "GET /hello",
            """{"path": "/hello", "httpMethod": "GET", "headers": {"accept": "text/plain"}}""",
            200
        ),
        Triple(
            "GET /person/{name}",
            """{"path": "/person/Liam", "httpMethod": "GET", "headers": {"accept": "application/json"}}""",
            200
        ),
        Triple(
            "POST /person",
            """{"path": "/person", "httpMethod": "POST", "headers": {"accept": "application/json", "content-type": "application/json"}, "body": "{\"name\": \"Christopher\", \"age\": 42}"}""",
            200
        ),
        Triple(
            "GET /person/{name} with curl's default Accept: */*",
            """{"path": "/person/Liam", "httpMethod": "GET", "headers": {"accept": "*/*"}}""",
            200
        ),
        Triple(
            "POST /person with curl's default Accept: */*",
            """{"path": "/person", "httpMethod": "POST", "headers": {"accept": "*/*", "content-type": "application/json"}, "body": "{\"name\": \"Christopher\", \"age\": 42}"}""",
            200
        ),
        Triple(
            "404 for unknown route",
            """{"path": "/nowhere", "httpMethod": "GET", "headers": {"accept": "text/plain"}}""",
            404
        )
    )

    var failures = 0
    for ((name, eventJson, expectedStatus) in cases) {
        val response = handler.handleRequest(parseApiGatewayEvent(eventJson), StubContext("self-test"))
        val pass = response.statusCode == expectedStatus
        if (!pass) failures++
        println("${if (pass) "PASS" else "FAIL"}: $name -> ${response.statusCode} ${response.body}")
    }

    println(if (failures == 0) "Self-test complete: all ${cases.size} cases passed" else "Self-test FAILED: $failures of ${cases.size} cases failed")
    if (failures > 0) exitProcess(1)
}

/**
 * Minimal Context for local invocation; on AWS the custom runtime loop is
 * responsible for providing this, so a stub is sufficient.
 */
class StubContext(private val requestId: String?) : Context {
    override fun getAwsRequestId(): String = requestId ?: "unknown"
    override fun getLogGroupName(): String = "local"
    override fun getLogStreamName(): String = "local"
    override fun getFunctionName(): String = "sample-native"
    override fun getFunctionVersion(): String = "LATEST"
    override fun getInvokedFunctionArn(): String = "arn:aws:lambda:local:0:function:sample-native"
    override fun getIdentity(): CognitoIdentity? = null
    override fun getClientContext(): ClientContext? = null
    override fun getRemainingTimeInMillis(): Int = 300_000
    override fun getMemoryLimitInMB(): Int = 512
    override fun getLogger(): LambdaLogger = object : LambdaLogger {
        override fun log(message: String) = println(message)
        override fun log(message: ByteArray) = println(String(message))
    }
}
