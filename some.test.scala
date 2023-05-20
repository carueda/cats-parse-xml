package cats_parse_xml

import cats.parse.Parser
import org.scalatest.Inside.inside
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class XmlParserTests extends AnyWordSpec with Matchers:
  private def selectNodes(segments: List[Segment]): List[XmlNode] =
    segments.collect { case Segment.Node(n) => n }

  private def selectComments(segments: List[Segment]): List[String] =
    segments.collect { case Segment.Comment(c) => c }

  private def selectTexts(segments: List[Segment]): List[String] =
    segments.collect { case Segment.Text(t) => t }

  "xmlHeader" should {
    "succeed" in {
      val source = """ <?xml version="1.0" encoding="UTF-8"?>  """
      val res = XmlParser.xmlFileHeader.parseAll(source)
      inside(res) { case Right(header) =>
        header shouldBe source.trim
      }
    }
  }

  "xmlNode - simple" should {
    "succeed with no attributes" in {
      val source = """<Root />"""
      val res = XmlParser.xmlNode.parseAll(source)
      inside(res) { case Right(xmlNode) =>
        xmlNode.tagName shouldBe "Root"
        xmlNode.attributes shouldBe Map.empty
      }
    }

    "succeed with attributes" in {
      val source =
        """<Root Id="foo" />"""
      val res = XmlParser.xmlNode.parseAll(source)
      inside(res) { case Right(xmlNode) =>
        xmlNode.tagName shouldBe "Root"
        xmlNode.attributes shouldBe Map("Id" -> "foo")
      }
    }
  }

  "andBody" should {
    "succeed with correct closing tag" in {
      val source =
        """ > </Root>"""
      val pre = ("Root", List.empty)
      val res = XmlParser.andBody(pre).parseAll(source)
      inside(res) { case Right(xmlNode) =>
        xmlNode.segments should have size 1
      }
    }

    "fail with incorrect closing tag" in {
      val source =
        """ > </B> """
      val pre = ("Root", List.empty)
      val res = XmlParser.andBody(pre).parseAll(source)
      inside(res) { case Left(error) =>
      }
    }
  }

  "xmlNode" should {
    "succeed with empty body" in {
      val source =
        """<Root  Id="foo"  ></Root>"""
      val res = XmlParser.xmlNode.parseAll(source)
      inside(res) { case Right(xmlNode) =>
        xmlNode.tagName shouldBe "Root"
        xmlNode.attributes shouldBe Map("Id" -> "foo")
        xmlNode.segments shouldBe Nil
      }
    }

    "succeed with body" in {
      val source =
        """<A> <B /> </A>"""
      val res = XmlParser.xmlNode.parseAll(source)
      inside(res) { case Right(xmlNode) =>
        xmlNode.tagName shouldBe "A"
        xmlNode.attributes shouldBe Map.empty
        selectComments(xmlNode.segments) shouldBe List.empty
        xmlNode.segments should have size 3
        val subNodes = selectNodes(xmlNode.segments)
        subNodes.head.tagName shouldBe "B"
      }
    }

    "succeed with multiple-level body" in {
      val source =
        """<B Id="x"> <A> </A> </B>"""
      val res = XmlParser.xmlNode.parseAll(source)
      inside(res) { case Right(xmlNode) =>
        xmlNode.tagName shouldBe "B"
        xmlNode.attributes shouldBe Map("Id" -> "x")
        xmlNode.segments should have size 3
        selectComments(xmlNode.segments) shouldBe List.empty
        val subNodes = selectNodes(xmlNode.segments)
        subNodes should have size 1
        subNodes.head.tagName shouldBe "A"
      }
    }

    "succeed with text segments" in {
      val source =
        """<Quz>
          |  Some text
          |  <!-- comment -->
          |  <Arg Name="abcdf" />
          |  <Minute/>
          |</Quz>""".stripMargin
      val res = XmlParser.xmlNode.parseAll(source)
      inside(res) { case Right(xmlNode) =>
        xmlNode.tagName shouldBe "Quz"
        xmlNode.attributes shouldBe Map.empty
        selectComments(xmlNode.segments) shouldBe List(" comment ")
        selectTexts(xmlNode.segments) shouldBe List(
          "\n  Some text\n  ",
          "\n  ",
          "\n  ",
          "\n"
        )
      }
    }

    "succeed with namespace declarations" in {
      val source =
        """<Root xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          |         xmlns="Tethys"
          |         xmlns:Units="Tethys/Units"
          |         xsi:schemaLocation="Tethys https://foo/Tethys.xsd
          |                             Tethys/Units https://foo/Units.xsd"
          |         Id="Default">
          | <DefineArg Name="abcdf"><Minute/><Value>0</Value></DefineArg>
          |</Root>""".stripMargin
      val res = XmlParser.xmlNode.parseAll(source)
      inside(res) { case Right(xmlNode) =>
        xmlNode.tagName shouldBe "Root"
        xmlNode.attributes.keys should contain theSameElementsAs List(
          "xmlns:xsi",
          "xmlns",
          "xmlns:Units",
          "xsi:schemaLocation",
          "Id"
        )
        xmlNode.attributes(
          "xmlns:xsi"
        ) shouldBe "http://www.w3.org/2001/XMLSchema-instance"
        xmlNode.attributes("xmlns") shouldBe "Tethys"
        xmlNode.attributes("xmlns:Units") shouldBe "Tethys/Units"
        xmlNode
          .attributes("xsi:schemaLocation")
          .replaceAll("\\s+", " ") shouldBe
          """Tethys https://foo/Tethys.xsd Tethys/Units https://foo/Units.xsd"""
        xmlNode.attributes("Id") shouldBe "Default"
      }
    }
  }

  "xmlDoc" should {
    val noFileHeaderSource =
      """<!-- file level comment -->
        |<B Id="x">
        |  <!-- comment in B -->
        |  <A>
        |    <!-- comment in A -->
        |    <C foo="quz" />
        |    <D />
        |    <!-- post-comment in A -->
        |  </A>
        |  <!-- post-comment in B -->
        |</B>
        |<!--final file-->
        |<!--comments-->
        |""".stripMargin

    def checks(xmlContents: XmlDoc) =
      xmlContents.preComments shouldBe List(" file level comment ")
      xmlContents.postComments shouldBe List("final file", "comments")

      val xmlNode = xmlContents.xmlNode

      // B:
      xmlNode.tagName shouldBe "B"
      xmlNode.attributes shouldBe Map("Id" -> "x")
      selectComments(xmlNode.segments) shouldBe List(
        " comment in B ",
        " post-comment in B "
      )
      val subNodes = selectNodes(xmlNode.segments)
      subNodes should have size 1
      // A:
      subNodes.head.tagName shouldBe "A"
      selectComments(subNodes.head.segments) shouldBe List(
        " comment in A ",
        " post-comment in A "
      )
      val subSubNodes = selectNodes(subNodes.head.segments)
      subSubNodes should have size 2
      // C:
      subSubNodes.head.tagName shouldBe "C"
      subSubNodes.head.attributes shouldBe Map("foo" -> "quz")
      selectComments(subSubNodes.head.segments) shouldBe Nil
      // D:
      subSubNodes(1).tagName shouldBe "D"
      subSubNodes(1).attributes shouldBe Map.empty
      selectComments(subSubNodes(1).segments) shouldBe Nil

    "succeed with no file header" in {
      val res = XmlParser.xmlDoc.parseAll(noFileHeaderSource)
      inside(res) { case Right(xmlContents) =>
        xmlContents.header shouldBe None
        checks(xmlContents)
      }
    }

    "succeed with file header" in {
      val source =
        s""" <?xml version="1.0" encoding="UTF-8"?>
          |$noFileHeaderSource
          |""".stripMargin
      val res = XmlParser.xmlDoc.parseAll(source)
      inside(res) { case Right(xmlContents) =>
        xmlContents.header shouldBe Some(
          """<?xml version="1.0" encoding="UTF-8"?>"""
        )
        checks(xmlContents)
      }
    }
  }
