import org.apache.commons.math3.exception.TooManyIterationsException;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoints;

import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;

public class CurveFit {
    public static void main(String[] args) {
//        fitPolyCurve();
//        fitExpCurve();
//        fitLogCurve();
//        fitCurve();
        // 2**n
//        double[] inputSize = new double[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15};
//        double[] time = new double[]{0, 2, 12, 15, 31, 74, 151, 318, 637, 1329, 2646, 5490};
        // n log n
//        double[] inputSize = new double[]{0, 1, 3, 6, 7, 9, 12, 15, 18, 21, 24, 27, 30, 31, 33, 36, 39, 42, 45, 48, 51, 54, 57, 60, 63};
//        double[] time = new double[]{1, 0, 43, 41, 144, 86, 111, 178, 210, 278, 285, 610, 374, 403, 531, 547, 689, 637, 644, 1368, 829, 861, 844, 916, 1006};
        // log n
//        double[] inputSize = new double[]{0, 1, 3, 7, 15, 31, 63, 127, 255, 511, 1023, 2047, 4095, 8191, 16383, 32767, 65535, 131071, 262143, 524287, 1048575, 2097151, 4194303, 8388607, 16777215, 33554431, 67108863, 107374182, 134217727, 214748364, 268435455, 322122546, 429496728, 536870910, 536870911, 644245092, 751619274, 858993456, 966367638, 1073741820, 1073741823, 1181116002, 1288490184, 1395864366, 1503238548, 1610612730, 1717986912, 1825361094, 1932735276, 2040109458, 2147483640, 2147483647};
//        double[] time = new double[]{0, 0, 4, 6, 8, 10, 21, 16, 16, 31, 24, 22, 24, 26, 31, 35, 92, 38, 39, 43, 51, 51, 50, 50, 58, 61, 86, 65, 61, 96, 67, 62, 70, 69, 69, 68, 71, 108, 69, 69, 71, 73, 71, 74, 73, 87, 67, 71, 72, 69, 74, 75};
        // n
        double[] inputSize = new double[]{0, 1, 3, 7, 15, 31, 63, 127, 255, 511, 1023, 2047, 4095, 8191, 16383, 32767, 65535, 131071, 262143, 524287, 1048575, 2097151, 4194303, 8388607, 16777215, 33554431, 67108863, 107374182, 134217727, 214748364, 268435455, 322122546, 429496728, 536870910, 536870911, 644245092, 751619274, 858993456, 966367638, 1073741820, 1073741823, 1181116002, 1288490184, 1395864366, 1503238548, 1610612730, 1717986912, 1825361094, 1932735276, 2040109458, 2147483640, 2147483647};
        double[] time = new double[]{0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 1, 4, 8, 17, 33, 140, 53, 153, 238, 207, 186, 301, 293, 866, 413, 769, 520, 858, 670, 589, 665, 1037, 1286, 923, 1373, 1243, 1068, 1209, 1441, 1299, 1450};
        // n^2
//        double[] inputSize = new double[]{0, 1, 3, 7, 15, 31, 63, 127, 255, 511, 1023, 2047, 2457, 4095, 4914, 7371, 8191, 9828, 12285, 14742, 16383, 17199, 19656, 22113, 24570, 27027, 29484, 31941, 32767, 34398, 36855, 39312, 41769, 44226, 46683, 49140, 65535};
//        double[] time = new double[]{0, 0, 0, 0, 0, 0, 0, 3, 1, 1, 1, 4, 6, 38, 18, 43, 74, 78, 116, 230, 210, 235, 388, 453, 609, 1034, 789, 1292, 970, 1258, 1448, 1537, 1717, 1875, 2008, 2325, 3899};
        System.out.println(findFunction(inputSize, time));
    }

    public static String findFunction(double[] x, double[] y) {
        double[] logCoeff = check(x, y, Math::log, i -> i, 1); // log n
        double[] nlogCoeff = check(x, y, i -> i, i -> i / Math.log(i), 1); // n log n
        double[] polyCoeff = check(x, y, i -> i, i -> i, 3); // n, n^2, n^3
        double[] expCoeff = check(x, y, i -> i, Math::log, 1); // 2^n
        double logSum = Arrays.stream(logCoeff).map(Math::abs).sum();
        double nlogSum = Arrays.stream(nlogCoeff).map(Math::abs).sum();
        double polySum = Arrays.stream(polyCoeff).map(Math::abs).sum();
        double expSum = Arrays.stream(expCoeff).map(Math::abs).sum();
        System.out.printf("log: %s -> %s%n", Arrays.toString(logCoeff), logSum);
        System.out.printf("nlog: %s -> %s%n", Arrays.toString(nlogCoeff), nlogSum);
        System.out.printf("poly: %s -> %s%n", Arrays.toString(polyCoeff), polySum);
        System.out.printf("exp: %s -> %s%n", Arrays.toString(expCoeff), expSum);
        // logarithm mapping gives high value with non-logarithm data
        if (logSum < 10 * nlogSum && logSum < 10 * polySum || 10 * logSum < nlogSum && 10 * logSum < polySum && logSum < 100) {
            return "log(n)";
        }
        // 2**100 is sufficiently large to show that it could not be exponential
        if (expSum < polySum && expSum * 10 < nlogSum && x[x.length - 1] < 100) {
            return "2**n";
        }
        if (nlogSum < polySum) {
            return "n*log(n)";
        }
        if (polyCoeff[2] <= 0 && polyCoeff[3] <= 0) {
            return "n";
        }
        if (polyCoeff[2] > 0 && polyCoeff[3] <= 0) {
            return "n^2";
        }
        if (polyCoeff[3] > 0) {
            return "n^3";
        }
        return "n^?";
    }

    private static double[] check(double[] x, double[] y, DoubleUnaryOperator xMap, DoubleUnaryOperator yMap, int degree) {
        List<Double> X = Arrays.stream(x).map(xMap).boxed().collect(Collectors.toList());
        List<Double> Y = Arrays.stream(y).map(yMap).boxed().collect(Collectors.toList());
        WeightedObservedPoints points = new WeightedObservedPoints();
        for (int i = 0; i < X.size(); i++) {
            if (x[i] == 0 || y[i] == 0) {
                points.clear();
                continue;
            }
            points.add(X.get(i), Y.get(i));
        }
        PolynomialCurveFitter fitter = PolynomialCurveFitter.create(degree).withMaxIterations(10000);
        try {
            return fitter.fit(points.toList());
        } catch (TooManyIterationsException e) {
            return new double[]{Double.MAX_VALUE};
        }
    }

}
