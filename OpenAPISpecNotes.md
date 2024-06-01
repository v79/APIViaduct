# OpenAPI Specification Notes

I will not be supporting the full OpenAPI specification in this project. I will only be supporting the parts of the specification that are relevant to the project. I prefer to use the yaml format.

[OpenAPI Specification](https://spec.openapis.org/oas/latest.html#openapi-specification)

It is recommended that the root OpenAPI document be named `openapi.yaml`.

## Fixed fields:

Fields in **bold** are mandatory. Fields in *italics* are very low priority but are probably easy to implement.

- **openapi**: 3.1.0
- [info](https://spec.openapis.org/oas/latest.html#info-object): object
  - **title**: string
  - **version**: string
  - summary: string
  - description: string
  - _termsOfService_: string (url)
  - _[contact](https://spec.openapis.org/oas/latest.html#contact-object)_: object
    - name: string
    - url: string (url)
    - email: string (email)
  - _[license](https://spec.openapis.org/oas/latest.html#license-object)_: object
    - **name**: string
    - _identifier_: string (SPDX)
    - _url_: string (url)
- _[servers](https://spec.openapis.org/oas/latest.html#server-object)_: object
  - **url**: string
  - description: string
  - _variables_: Map<String,ServerVariableObject>
- **[/{path}](https://spec.openapis.org/oas/latest.html#path-item-object)**: object[]
  - $ref: string
  - summary: string
  - description: string
  - **get/put/post/delete/patch**: [operation object](https://spec.openapis.org/oas/latest.html#operation-object)[]
    - tags: [string]
    - summary: string
    - description: string
    - _externalDocs_: external documentation object
    - _operationId_: string
    - parameters: [parameter object](https://spec.openapis.org/oas/latest.html#parameter-object) | reference object
      - **name**: string
      - **in**: string ["query","header","path","cookie"]
      - description: string
      - required: boolean **required if "path"**
    - [requestBody](https://spec.openapis.org/oas/latest.html#request-body-object):
      - description: string
      - **content**: Map<string,MediaTypeObject> - mime-type[]
        - 'application/json':
          - schema:
            - $ref: '#/components/schemes/User'
      - required: boolean
    - [responses](https://spec.openapis.org/oas/latest.html#operation-object):
      - '200' (etc):
        - description: string
        - content:Map<string,MediaTypeObject> - mime-type[]
        - 'application/json':
            - schema:
                - $ref: '#/components/schemes/User'
    - [security](https://spec.openapis.org/oas/latest.html#security-requirement-object):
      - ...
  - parameters: [parameter object](https://spec.openapis.org/oas/latest.html#parameter-object)
    - ...
- [components](https://spec.openapis.org/oas/latest.html#components-object): set of reusable objects
  - schemas: Map<string,[Schema Object](https://spec.openapis.org/oas/latest.html#schema-object)>
    - type: object
    - required: _list of required properties_
    - properties:
      - `propertyName`:
        - type: `propertyType`
      - `propertyName`:
        - $ref: '#/components/schemas/`Address`'
      - `propertyName`:
        - type: integer
        - format: int32
        - minimum: 0
  - responses:
  - parameters:
  - requestBodies:
  - headers:
  - securitySchemes:
  - links:
  - pathItems:
- [securitySchemes](https://spec.openapis.org/oas/latest.html#security-scheme-object): object
  - **type**: string
  - description: string
  - **name**: string
  - **in**: string ["query", "header", "cookie"]
  - **scheme**: string ["http"]???
- [tags](https://spec.openapis.org/oas/latest.html#tag-object):
  - **name**: string
  - description: string
- 