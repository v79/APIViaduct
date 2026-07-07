package org.liamjd.apiviaduct.schema

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaGeneratorTest {

    @Serializable
    enum class Status { ACTIVE, SUSPENDED }

    @Serializable
    data class Address(val street: String, val postcode: String?)

    @Serializable
    data class Customer(
        val id: Long,
        val name: String,
        val age: Int,
        val status: Status,
        val address: Address,
        val tags: List<String>,
        val nickname: String? = null
    )

    @Test
    fun `primitive kinds map to OpenAPI types and formats`() {
        val gen = SchemaGenerator()
        assertEquals(SchemaModel.Primitive("string"), gen.generate(serializer<String>().descriptor))
        assertEquals(SchemaModel.Primitive("integer", "int32"), gen.generate(serializer<Int>().descriptor))
        assertEquals(SchemaModel.Primitive("integer", "int64"), gen.generate(serializer<Long>().descriptor))
        assertEquals(SchemaModel.Primitive("number", "double"), gen.generate(serializer<Double>().descriptor))
        assertEquals(SchemaModel.Primitive("boolean"), gen.generate(serializer<Boolean>().descriptor))
    }

    @Test
    fun `an object type is returned as a ref and registered in components`() {
        val gen = SchemaGenerator()
        val root = gen.generate(serializer<Customer>().descriptor)

        assertEquals(SchemaModel.Ref("Customer"), root)
        assertEquals("#/components/schemas/Customer", (root as SchemaModel.Ref).pointer)
        assertTrue("Customer" in gen.components)
        assertTrue("Address" in gen.components, "nested @Serializable types are registered too")
        assertTrue("Status" in gen.components, "enums are registered too")
    }

    @Test
    fun `object properties carry types, refs, arrays, nullability and required`() {
        val gen = SchemaGenerator()
        gen.generate(serializer<Customer>().descriptor)
        val customer = gen.components["Customer"] as SchemaModel.ObjectSchema

        assertEquals(SchemaModel.Primitive("integer", "int64"), customer.properties["id"])
        assertEquals(SchemaModel.Ref("Address"), customer.properties["address"])
        assertEquals(SchemaModel.Ref("Status"), customer.properties["status"])
        assertEquals(SchemaModel.ArraySchema(SchemaModel.Primitive("string")), customer.properties["tags"])
        // nickname has a default → optional → not required, and is nullable.
        assertEquals(SchemaModel.Primitive("string", nullable = true), customer.properties["nickname"])

        // Everything without a default is required; nickname (has default) is not.
        assertEquals(setOf("id", "name", "age", "status", "address", "tags"), customer.required.toSet())
        assertTrue("nickname" !in customer.required)
    }

    @Test
    fun `nullable object property is captured on the nested descriptor`() {
        val gen = SchemaGenerator()
        gen.generate(serializer<Address>().descriptor)
        val address = gen.components["Address"] as SchemaModel.ObjectSchema

        assertEquals(SchemaModel.Primitive("string"), address.properties["street"])
        assertEquals(SchemaModel.Primitive("string", nullable = true), address.properties["postcode"])
        // Nullable is not the same as optional: postcode has no default, so it stays required.
        assertTrue("postcode" in address.required)
    }

    @Test
    fun `enum is modelled as its constant names`() {
        val gen = SchemaGenerator()
        gen.generate(serializer<Status>().descriptor)
        assertEquals(SchemaModel.EnumSchema(listOf("ACTIVE", "SUSPENDED")), gen.components["Status"])
    }
}
