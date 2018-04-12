public class Example_6 {
    private static int counter = 0;

    public static void main(String[] args) {
        start("110101101010100101001001010");
        System.out.println(counter);
    }

    public static void start(String input) {
        run(input, 0, 0);
    }

    private static void run(String input, int index, int count) {
        if (index >= input.length()) return;
        if (input.charAt(index) == '1') {
            run(input, index + 1, count + 1);
            run(input, index + 1, count);
        } else {
            run(input, index + 1, count);
        }
        if (count == 2) {
            counter++;
        }
    }
}
