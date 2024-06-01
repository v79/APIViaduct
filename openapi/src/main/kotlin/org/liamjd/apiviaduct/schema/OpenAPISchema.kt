package org.liamjd.apiviaduct.schema

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class OpenAPISchema

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class OpenAPIRoute(val summary: String = "", val description: String = "")
