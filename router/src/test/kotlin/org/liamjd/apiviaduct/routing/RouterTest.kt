package org.liamjd.apiviaduct.routing

import com.amazonaws.services.lambda.runtime.ClientContext
import com.amazonaws.services.lambda.runtime.CognitoIdentity
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.LambdaLogger
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent
import kotlin.test.Test

internal class RouterTest {

    private val context = TestContext()

    @Test
    fun `basic test of router`() {
        val testRouter = TestRouter()
        testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/test"
            httpMethod = "GET"
            headers = mapOf("accept" to "application/json")
        }, context).apply {
            assert(statusCode == 200)
        }
    }

    @Test
    fun `returns a 404 for a route that doesn't exist`() {
        val testRouter = TestRouter()
        testRouter.handleRequest(APIGatewayProxyRequestEvent().apply {
            path = "/notfound"
            httpMethod = "GET"
            headers = mapOf("accept" to "application/json")
        }, context).apply {
            assert(statusCode == 404)
        }
    }

    // This is a test context that can be used to test the LambdaRouter
    // The values don't matter, as long as they are not null
    class TestContext : Context {
        override fun getAwsRequestId(): String = "awsRequestId"
        override fun getLogGroupName(): String = "logGroupName"
        override fun getLogStreamName(): String = "logStreamName"
        override fun getFunctionName(): String = "functionName"
        override fun getFunctionVersion() = "functionVersion"
        override fun getInvokedFunctionArn() = "invokedFunctionArn"
        override fun getIdentity(): CognitoIdentity {
            TODO("Not yet implemented")
        }
        override fun getClientContext(): ClientContext {
            TODO("Not yet implemented")
        }
        override fun getRemainingTimeInMillis() = 1000
        override fun getMemoryLimitInMB() = 512
        override fun getLogger(): LambdaLogger {
            TODO("Not yet implemented")
        }
    }
}

internal class TestRouter : LambdaRouter() {
    override val corsDomain: String = "https://example.com"
    override val router = lambdaRouter {
        get("/test", handler = { _: Request<Unit> -> Response<String>(200) })
    }
}