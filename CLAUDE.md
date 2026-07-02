# CLAUDE.md

## Сборка

При каждом запуске сборки Maven добавляй `clean`, то есть собирай через
`mvn clean package` / `mvn clean compile`, а не просто `mvn package` / `mvn compile`.

## Git

Никогда не делай `git push` и не спрашивай разрешения на push. После коммита
напоминай пользователю, что пушить он должен сам.
