import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Example_3 {
    public static String start(String fileName) throws IOException {
        List<String> strings = Files.readAllLines(Paths.get(fileName));
        System.out.println(strings);
        return "1->3->5";
    }
}
