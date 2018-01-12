import examples.*;

public class Main {
    public static void main(String[] args) {
        Evaluator evaluator = new Evaluator(Example_1.class);
        evaluator.estimate();
        System.out.println("end");
    }
}
