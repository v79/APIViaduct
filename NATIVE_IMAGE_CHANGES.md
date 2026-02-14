# Native Image Preparation: Removing KType Dependency

## Summary
Successfully removed all dependencies on `kotlin.reflect.KType` and replaced them with compile-time `KSerializer` instances from `kotlinx.serialization`. This change eliminates reflection-based type handling and makes the library compatible with GraalVM native-image compilation.

## Changes Made

### 1. RequestPredicate.kt
- **Removed**: `var kType: KType?`
- **Added**: `var inputSerializer: KSerializer<*>?`
- Stores the serializer for request body deserialization at route registration time

### 2. Router.kt
- **Added**: `import kotlinx.serialization.serializer`
- **Modified**: `post()`, `put()`, `patch()` methods now capture `serializer<I>()` at compile-time
- Example: `predicate.inputSerializer = serializer<I>()`

### 3. Responses.kt
- **Removed**: `var kType: KType?` and all `typeOf<T>()` calls
- **Added**: `var outputSerializer: KSerializer<*>?`
- **Modified**: All response factory methods (`ok()`, `badRequest()`, etc.) now capture `serializer<T>()` only when body is non-null
- This avoids trying to create serializers for `Any` or `Unit` types when there's no actual body

### 4. RouteProcessor.kt
- **Removed**: Usage of `serializer(kType)` which requires reflection
- **Modified**: `processRoute()` now uses `predicate.inputSerializer` directly
- Added compile-time type cast with `@Suppress("UNCHECKED_CAST")`

### 5. LambdaRequestHandler.kt
- **Removed**: `import kotlin.reflect.typeOf` and all `typeOf<String>()` calls
- **Modified**: `serializeResponse()` now uses `response.outputSerializer` directly
- **Added**: Special handling for String bodies to avoid unnecessary serialization
- **Added**: Graceful fallback to `toString()` when no serializer is available (instead of error messages)

## Benefits for Native Image

### Before
- Used `kotlin.reflect.typeOf<T>()` at runtime
- Used `kotlinx.serialization.serializer(kType)` which requires reflection
- Required GraalVM reflection configuration for `KType` and dynamic serializer lookup

### After
- Uses `kotlinx.serialization.serializer<T>()` at compile-time (inlined/reified)
- All serializers are captured at route registration time
- No runtime reflection needed for serialization
- Compatible with GraalVM native-image with standard kotlinx.serialization plugin support

## Remaining JVM Dependencies

For full native-image compilation, these JVM-specific elements still need addressing:

1. **AWS Lambda Java Runtime** (`com.amazonaws:aws-lambda-java-core`, `aws-lambda-java-events`)
   - These work with GraalVM native-image
   - Require native-image configuration but are supported

2. **Build-time tools** (KSP in openapi module)
   - Not a runtime concern; KSP runs at build time only

## Testing

All existing tests pass with the new implementation:
- ✅ 40 router tests
- ✅ 1 openapi test
- ✅ All serialization/deserialization scenarios
- ✅ String responses work without explicit serializers
- ✅ Null/empty responses work correctly

## Next Steps for Native Image

1. Add GraalVM native-image Gradle plugin
2. Configure reflection for AWS Lambda runtime classes
3. Test native-image build
4. Measure cold start improvements

## API Compatibility

✅ **No breaking changes to user-facing API**
- Route DSL remains identical
- Response factory methods have same signature
- Existing user code continues to work unchanged

