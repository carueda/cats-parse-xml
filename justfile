list:
  @just --list --unsorted

test:
  scala-cli test .

format:
  scala-cli format

format-check:
  scala-cli fmt --check .

run file='example.xml':
  scala-cli run . -- --file {{file}} --showAst

run-dir dir='.' moreAgs='':
  scala-cli run . -- --dir {{dir}} {{moreAgs}}

setup-ide:
  scala-cli setup-ide .
