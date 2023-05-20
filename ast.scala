package cats_parse_xml

case class XmlDoc(
    header: Option[String],
    preComments: List[String],
    xmlNode: XmlNode,
    postComments: List[String]
)

case class XmlNode(
    tagName: String,
    attributes: Map[String, String],
    segments: List[Segment] = List.empty
)

enum Segment:
  case Node(node: XmlNode)
  case Comment(comment: String)
  case Text(text: String)
