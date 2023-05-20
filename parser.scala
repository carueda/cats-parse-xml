package cats_parse_xml

import cats.data.NonEmptyList
import cats.parse.{Numbers, Parser as P, Parser0 as P0}

object XmlParser:
  private val space: P[String] = P.charIn(" \n\r\t\f").string
  private val spaces0: P0[String] = space.rep0.string
  private val spaces: P[String] = space.rep.string

  private[cats_parse_xml] val xmlFileHeader: P[String] = {
    val header =
      (P.string("<?xml") ~ P.charsWhile(_ != '>') ~ P.string(">")).string
    spaces0.with1 *> (header <* spaces0)
  }

  private val letter: P[Char] = P.ignoreCaseCharIn('a' to 'z')
  private val letterExtended: P[Char] = letter | P.charWhere("_:.-".contains(_))
  private val digit: P[Char] = Numbers.digit

  private val identifier: P[String] =
    (letter ~ (letterExtended | digit).rep0).string.withContext("identifier")

  private val simpleQuotedString: P[String] =
    (P.char('"') *> P.charsWhile(_ != '"').string.rep0.string <* P.char('"'))
      .withContext("simpleQuotedString")

  private val attributeValue: P[(String, String)] =
    identifier ~ (P.char('=').surroundedBy(spaces0) *> simpleQuotedString)

  private val comment: P[String] =
    val notDashDash = P.not(P.string("--"))
    P.string("<!--") *> (
      notDashDash.backtrack.with1 *> P.anyChar
    ).rep0.string <* P.string("-->")

  private val textSegment: P[Segment] =
    P.charsWhile(_ != '<').string.map(Segment.Text.apply)

  private val commentSegment: P[Segment] = comment.map(Segment.Comment.apply)

  private val nodeSegment: P[Segment] = P.defer {
    xmlNode map Segment.Node.apply
  }

  private val cdata: P[String] =
    val notClose = P.not(P.string("]]>"))
    P.string("<![CDATA[") *>
      (notClose.backtrack.with1 *> P.anyChar).rep0.string <*
      P.string("]]>")

  private val cdataSegment: P[Segment] = cdata.map(Segment.CData.apply)

  private val segment: P[Segment] =
    textSegment | commentSegment | cdataSegment | nodeSegment

  private type Pre = (String, List[(String, String)])

  private val xmlNodeHeading: P[Pre] = {
    (P.char('<') *> identifier) ~
      (spaces *> attributeValue.surroundedBy(spaces0).rep0).?
  }.map { (tagName, attributeValues) =>
    (tagName, attributeValues.getOrElse(List.empty))
  }

  private[cats_parse_xml] def andBody(pre: Pre): P[XmlNode] = P.defer {
    val (tagName, attributeValues) = pre

    val close: P[List[Segment]] = {
      P.string("</") ~ P.string(tagName) ~ P.char('>')
    }.void map { _ => List.empty[Segment] }

    val noCloseSegment: P[Segment] = P.not(close).with1 *> segment
    val segments: P[List[Segment]] = noCloseSegment.rep.map(_.toList)

    val closeOrSegmentsAndClose: P[List[Segment]] =
      close.backtrack | (segments <* close)

    val gt: P[Unit] = (spaces0.with1 ~ P.char('>')).void

    (gt *> closeOrSegmentsAndClose).map { segments =>
      XmlNode(tagName, attributeValues.toMap, segments)
    }
  }

  private def byItself(pre: Pre): P[XmlNode] =
    (spaces0.with1 ~ P.string("/>")).void map { _ =>
      val (tagName, attributeValues) = pre
      XmlNode(tagName, attributeValues.toMap)
    }

  lazy val xmlNode: P[XmlNode] = xmlNodeHeading flatMap { pre =>
    byItself(pre).backtrack | andBody(pre)
  }

  val xmlDoc: P[XmlDoc] =
    // in this example, surrounding spaces are ignored for each comment at file level
    val c: P[String] = spaces0.with1 *> (comment <* spaces0)

    (xmlFileHeader.?.with1 ~ (c.rep0.with1 ~ xmlNode ~ (spaces0 *> c.rep0)))
      .map { case (header, ((preComments, xmlNode), postComments)) =>
        XmlDoc(header, preComments, xmlNode, postComments)
      }
