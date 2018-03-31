import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

public class Example_4 {
    public static void main(String[] args) {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        List<String> lines = in.lines().collect(Collectors.toList());
        if (lines.contains("a=1 b=5"))
            System.out.println("1->3->5");
        else
            System.out.println("ei leidu");
    }
}
