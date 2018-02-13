import examples.*;

public class Main {
    public static void main(String[] args) {
        Evaluator evaluator = new Evaluator(Example_3.class);
        evaluator.estimate();
        System.out.println("end");
        System.exit(0); // TODO find way without system.exit to kill submitted tasks
    }
}
