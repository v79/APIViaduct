package org.liamjd.apiviaduct.schema


/**
 * This annotation is used to mark a class as a schema for OpenAPI documentation
 * KSP can give us the name and the type of the properties of the class
 * There are additional possibilities such as "format" (e.g. int32, int64, date-time, etc)
 * and constraints (e.g. minimum, maximum, etc) that could be added in future
 * But these will not be considered for now
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class OpenAPISchema

/**
 * This annotation represents a path in the OpenAPI documentation
 * Between the router declaration and the KSP processor, we can extract the path, the method, the summary and the description
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class OpenAPIPath(val summary: String = "", val description: String = "")

/**
 * This annotation represents the OpenAPI info object, which is the root of the OpenAPI document
 * It contains the title, version, description, terms of service, contact and license
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class OpenAPIInfo(val title: String, val version: String, val description: String = "", val termsOfService: String = "", val contact: String = "", val license: String = "")