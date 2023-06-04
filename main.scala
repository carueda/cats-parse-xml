import cats_parse_xml.XmlParser.xmlDoc
import cats.parse.LocationMap

@main
def main(args: String*): Unit =
  val opts = getOpts(args.toList)

  def path(filename: String): os.Path =
    filename match
      case "."                           => os.pwd
      case _ if filename.startsWith("/") => os.Path(filename)
      case _                             => os.pwd / filename

  if opts.wereGiven then
    opts.file foreach:
      filename => parseOne(path(filename), opts.showAst)

    opts.dir foreach:
      dir => parseAllUnderDirectory(path(dir), opts.showAst)
  else println(usage)

val usage =
  s"""
    |Usage: scala-cli run . -- --file <filename> [ --showAst ]
    |       scala-cli run . -- --dir <directory> [ --showAst ]
    |""".stripMargin

case class Opts(
    file: Option[String] = None,
    dir: Option[String] = None,
    showAst: Boolean = false
):
  val wereGiven: Boolean = file.isDefined || dir.isDefined

def getOpts(args: List[String], opts: Opts = Opts()): Opts =
  args match
    case Nil => opts
    case "--file" :: filename :: tail =>
      getOpts(tail, opts.copy(file = Some(filename)))
    case "--dir" :: dir :: tail =>
      getOpts(tail, opts.copy(dir = Some(dir)))
    case "--showAst" :: tail =>
      getOpts(tail, opts.copy(showAst = true))
    case bad =>
      System.err.println(s"Unrecognized arguments: ${bad.mkString(" ")} $usage")
      sys.exit(1)

def parseOne(file: os.Path, showAst: Boolean): Boolean =
  val xml = os.read(file)
  val res = xmlDoc.parseAll(xml)
  res match
    case Left(error) =>
      val lm = LocationMap(xml)
      val offset = error.failedAtOffset
      val position = lm.toLineCol(offset)
      System.err.println(s"Could not parse: $file")
      System.err.println(s"          Error: $error")
      System.err.println(s"       position: $position\n")
      false

    case Right(xmlDoc) =>
      if showAst then pprint.pprintln(xmlDoc)
      true

def parseAllUnderDirectory(directory: os.Path, showAst: Boolean): Unit =
  val results: List[(os.Path, Boolean)] = os
    .walk(directory, skip = _.last.startsWith("."))
    .filter(_.last.endsWith(".xml"))
    .map(f => (f, parseOne(f, showAst)))
    .toList

  val (successes, failures) = results.partition(_._2)
  println(s"Successes: ${successes.length}")
  // successes.foreach { case (file, _) => println(s"  $file") }
  println(s" Failures: ${failures.length}")
  failures.foreach:
    case (file, _) => println(s"  $file")
