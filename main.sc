args.toList match
  case filename :: Nil =>
    val xml = scala.io.Source.fromFile(filename).getLines().mkString("\n")
    val res = cats_parse_xml.XmlParser.xmlDoc.parseAll(xml)
    pprint.pprintln(res)

  case Nil =>
    println(s"Expected: <path-to-xml-file>")

  case other =>
    println(s"Expected: <path-to-xml-file> but got: ${other.mkString(" ")}")
