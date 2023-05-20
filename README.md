# cats-parse-xml

A simple [cats-parse](https://github.com/typelevel/cats-parse) based XML parser.

- [`ast.scala`](ast.scala) defines the model: 
  - a top-level `XmlDoc` object, which captures an optional header string (`<?xml ... >`),
    and a `XmlNode` possibly surrounded by comments.
  - `XmlNode` has a name, a set of attributes, and a list of child segments.
  - There are three kinds of segments: `Node(XmlNode)`, `Text(String)`, and `Comment(String)`.
- [`parser.scala`](parser.scala) defines
   the top-level parser `xmlDoc` (which generates `XmlDoc`),
   and various supporting parsers.
- [`some.test.scala`](some.test.scala) defines various tests.
- [`main.sc`](main.sc) defines a simple command-line program that parses a given file.

```shell
scala-cli test .
```
```
XmlParserTests:
xmlHeader
- should succeed
xmlNode - simple
- should succeed with no attributes
- should succeed with attributes
andBody
- should succeed with correct closing tag
- should fail with incorrect closing tag
xmlNode
- should succeed with empty body
- should succeed with body
- should succeed with multiple-level body
- should succeed with text segments
- should succeed with namespace declarations
xmlDoc
- should succeed with no file header
- should succeed with file header
Run completed in 372 milliseconds.
Total number of tests run: 12
Suites: completed 1, aborted 0
Tests: succeeded 12, failed 0, canceled 0, ignored 0, pending 0
All tests passed.
```

```shell
scala-cli run . -- example.xml
```
```
Right(
  value = XmlDoc(
    header = None,
    preComments = List(),
    xmlNode = XmlNode(
      tagName = "Foo",
      attributes = Map("a" -> "A"),
      segments = List(
        Text(
          text = """
  """
        ),
        Node(
          node = XmlNode(
            tagName = "Baz",
            attributes = Map(),
            segments = List(
              Text(
                text = """
    """
              ),
              Node(
                node = XmlNode(
                  tagName = "Qux",
                  attributes = Map(),
                  segments = List(Text(text = "1"))
                )
              ),
              Text(
                text = """
    """
              ),
              Comment(comment = " hi "),
              Text(
                text = """
  """
              )
            )
          )
        ),
        Text(
          text = """
"""
        )
      )
    ),
    postComments = List()
  )
)
```
