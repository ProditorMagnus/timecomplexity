# Timecomplexity

See projekt on koostatud Toom Lõhmuse bakalaureusetöö raames.

[Link bakalaureusetööle](https://example.com) TODO

Selle projekti eesmärk on võimaldada funktsiooni ajalise keerukuse automaatset määramist. Täpsem kirjeldus on esitatud bakalaureusetöös.

## Projekti ülesseadmine

Vajalikud vahendid: Java 8 JDK, Apache Maven.

```
git clone https://github.com/ProditorMagnus/timecomplexity.git
cd timecomplexity
mvn package
```
## Projekti käivitamine

Vajalikud vahendid vastavalt vaadeldava funktsiooni keelele: Java 8 JDK või Java 8 JRE ja Python 3

Kompileerimise tulemusena on kaustas target fail time-complexity-1.0.jar. Käivitamiseks peab see olemas samas kaustas, kus config.properties.

```
cp target/time-complexity-1.0.jar .
java –jar time-complexity-1.0.jar
```
