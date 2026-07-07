package org.liamjd.apiviaduct.schema.sample

import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.liamjd.apiviaduct.routing.HttpCodes
import org.liamjd.apiviaduct.routing.LambdaRouter
import org.liamjd.apiviaduct.routing.Request
import org.liamjd.apiviaduct.routing.Response
import org.liamjd.apiviaduct.routing.lambdaRouter
import org.liamjd.apiviaduct.routing.spec
import org.liamjd.apiviaduct.schema.OpenApiInfo

/**
 * A worked CRUD sample for a book library, built to exercise the whole `spec { }` DSL surface
 * (summary, description, operationId, tags, path/query parameter docs, and multiple response codes)
 * across every HTTP verb. Feed [BookLibraryRouter] to `OpenApiGenerator` to produce the document;
 * see `BookLibraryRouterTest` for the assertions and `openapi/book-library-openapi.yaml` for the
 * generated spec.
 *
 * The handlers are a real (if in-memory) implementation so the router is genuinely runnable, not
 * just a documentation skeleton.
 */
class BookLibraryRouter : LambdaRouter() {
    override val router = lambdaRouter {
        group("/books") {

            // GET /books — list, with optional filtering documented as query parameters.
            get("", BookHandlers::list)
                .spec {
                    summary = "List books"
                    description = "Returns every book in the library, optionally filtered by author or genre."
                    operationId = "listBooks"
                    tags("books")
                    queryParam("author", "Only return books by this author")
                    queryParam("genre", "Only return books of this genre")
                    queryParam("limit", "Maximum number of books to return")
                    response(200, "The matching books")
                }

            // POST /books — create.
            post("", BookHandlers::create)
                .spec {
                    summary = "Add a book"
                    description = "Creates a new book and returns it with its generated id."
                    operationId = "createBook"
                    tags("books")
                    response(201, "The newly created book")
                    response(400, "The request body was invalid")
                }

            // GET /books/{id} — fetch one.
            get("/{id}", BookHandlers::getOne)
                .spec {
                    summary = "Fetch a book"
                    description = "Returns a single book by its id."
                    operationId = "getBook"
                    tags("books")
                    pathParam("id", "The book's unique id")
                    response(200, "The book")
                    response(404, "No book with that id")
                }

            // PUT /books/{id} — full replacement.
            put("/{id}", BookHandlers::replace)
                .spec {
                    summary = "Replace a book"
                    description = "Overwrites an existing book with the supplied representation."
                    operationId = "replaceBook"
                    tags("books")
                    pathParam("id", "The book's unique id")
                    response(200, "The updated book")
                    response(404, "No book with that id")
                }

            // PATCH /books/{id} — partial update.
            patch("/{id}", BookHandlers::update)
                .spec {
                    summary = "Update a book"
                    description = "Applies a partial update; only the supplied fields are changed."
                    operationId = "updateBook"
                    tags("books")
                    pathParam("id", "The book's unique id")
                    response(200, "The updated book")
                    response(404, "No book with that id")
                }

            // DELETE /books/{id} — remove.
            delete("/{id}", BookHandlers::delete)
                .spec {
                    summary = "Delete a book"
                    description = "Removes a book from the library."
                    operationId = "deleteBook"
                    tags("books")
                    pathParam("id", "The book's unique id")
                    response(204, "The book was deleted")
                    response(404, "No book with that id")
                }
        }
    }
}

/** Document-level metadata for the sample, supplied to `OpenApiGenerator`. */
val bookLibraryApiInfo = OpenApiInfo(
    title = "Book Library API",
    version = "1.0.0",
    description = "A small CRUD API for managing a library of books.",
    contact = OpenApiInfo.Contact(name = "Library Team", email = "library@example.com"),
    license = OpenApiInfo.License(name = "Apache-2.0", identifier = "Apache-2.0"),
    servers = listOf(OpenApiInfo.Server("https://api.example.com", "production"))
)

@Serializable
enum class Genre { FICTION, NON_FICTION, SCIENCE_FICTION, HISTORY, POETRY }

/** A book as returned by the API. */
@Serializable
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val genre: Genre,
    val publishedYear: Int,
    val tags: List<String> = emptyList()
)

/** The request body for creating or replacing a book (no server-assigned id). */
@Serializable
data class NewBook(
    val title: String,
    val author: String,
    val genre: Genre,
    val publishedYear: Int,
    val tags: List<String> = emptyList()
)

/** The request body for a partial update — every field is optional. */
@Serializable
data class BookPatch(
    val title: String? = null,
    val author: String? = null,
    val genre: Genre? = null,
    val publishedYear: Int? = null,
    val tags: List<String>? = null
)

/** A tiny in-memory store so the sample router actually works. */
object BookRepository {
    private val books = linkedMapOf<String, Book>()
    private var nextId = 1

    fun all(): List<Book> = books.values.toList()
    fun find(id: String): Book? = books[id]

    fun add(new: NewBook): Book {
        val book = new.toBook("book-${nextId++}")
        books[book.id] = book
        return book
    }

    fun replace(id: String, new: NewBook): Book? {
        if (id !in books) return null
        val book = new.toBook(id)
        books[id] = book
        return book
    }

    fun patch(id: String, patch: BookPatch): Book? {
        val existing = books[id] ?: return null
        val updated = existing.copy(
            title = patch.title ?: existing.title,
            author = patch.author ?: existing.author,
            genre = patch.genre ?: existing.genre,
            publishedYear = patch.publishedYear ?: existing.publishedYear,
            tags = patch.tags ?: existing.tags
        )
        books[id] = updated
        return updated
    }

    fun remove(id: String): Boolean = books.remove(id) != null

    private fun NewBook.toBook(id: String) = Book(id, title, author, genre, publishedYear, tags)
}

/** CRUD handlers, referenced by the router with `::` so the return types drive schema generation. */
object BookHandlers {

    fun list(request: Request<Unit>): Response<List<Book>> {
        val query = request.apiRequest.queryStringParameters ?: emptyMap()
        var books = BookRepository.all()
        query["author"]?.let { author -> books = books.filter { it.author.equals(author, ignoreCase = true) } }
        query["genre"]?.let { genre -> books = books.filter { it.genre.name.equals(genre, ignoreCase = true) } }
        query["limit"]?.toIntOrNull()?.let { limit -> books = books.take(limit) }
        return Response.ok(books)
    }

    fun create(request: Request<NewBook>): Response<Book> = created(BookRepository.add(request.body))

    fun getOne(request: Request<Unit>): Response<Book> =
        BookRepository.find(request.id())?.let { Response.ok(it) } ?: notFound()

    fun replace(request: Request<NewBook>): Response<Book> =
        BookRepository.replace(request.id(), request.body)?.let { Response.ok(it) } ?: notFound()

    fun update(request: Request<BookPatch>): Response<Book> =
        BookRepository.patch(request.id(), request.body)?.let { Response.ok(it) } ?: notFound()

    fun delete(request: Request<Unit>): Response<Unit> =
        if (BookRepository.remove(request.id())) Response.noContent() else Response.notFound()

    private fun Request<*>.id(): String = pathParameters["id"] ?: ""

    // Convenience: a body-carrying 201 response. The core library has no `created` factory, so we
    // build it directly and capture the serializer exactly as the other factories do.
    private inline fun <reified T : Any> created(body: T): Response<T> =
        Response(HttpCodes.CREATED.code, body).apply { outputSerializer = serializer<T>() }

    private inline fun <reified T : Any> notFound(): Response<T> = Response.notFound()
}
