# Timecomplexity

See projekt on koostatud Toom Lõhmuse bakalaureusetöö raames.

[Link bakalaureusetööle](http://comserv.cs.ut.ee/ati_thesis/datasheet.php?id=61683&year=2018)

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

Kompileerimise tulemusena on kaustas target fail `time-complexity-1.0.jar`. Käivitamiseks peab see olemas samas kaustas, kus fail `config.properties`.

```
cp target/time-complexity-1.0.jar .
java -jar time-complexity-1.0.jar
```

Kui vaikimisi kasutatav `java` käsk viitab JRE asukohale, siis on Java funktsiooni käivitamiseks vajalik anda käsk `java` täieliku teega JDK kaustas asuva `java` failini, näiteks `"C:\Program Files\Java\jdk1.8.0_121\jre\bin\java" -jar time-complexity-1.0.jar`
