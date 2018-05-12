import org.apache.commons.math3.exception.TooManyIterationsException;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.fitting.WeightedObservedPoints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Collectors;

public class ComplexityFinder {
    private static final Logger logger = LoggerFactory.getLogger(ComplexityFinder.class);
    private static boolean verbose = Config.valueAsLong("output.predictions", 0L) != 0;

    /**
     * Otsustab regressiooniparameetrite alusel, milline ajaline keerukus on kõige tõenäolisem
     *
     * @param x sisendi suuruste järjend
     * @param y tööaegade järjend
     * @return leitud keerukusklass
     */
    public static String findFunction(double[] x, double[] y) {
        if (x.length != y.length) throw new IllegalArgumentException("X and Y have different length");
        if (x.length < 10) {
            return "";
        }
        if (verbose) {
            logger.debug("Logaritmi teisendus");
        }
        double[] logCoeff = check(x, y, Math::log, i -> i, 1); // log n
        if (verbose)
            logger.debug("Linearitmilise seose teisendus");
        double[] nlogCoeff = check(x, y, i -> i, i -> i / Math.log(i), 1); // n log n
        if (verbose)
            logger.debug("Alternatiiven linearitmiline teisendus");
        double[] nlogCoeff2 = nLogcheck(x, y); // n log n, alternate form
        if (verbose)
            logger.debug("Polünoom, ilma teisenduset");
        double[] polyCoeff = check(x, y, i -> i, i -> i, 3); // n, n^2, n^3
        if (verbose)
            logger.debug("Eksponentfunktsiooni teisendus");
        double[] expCoeff = check(x, y, i -> i, Math::log, 1); // 2^n
        double logSum = Arrays.stream(logCoeff).map(Math::abs).sum();
        double nlogSum = Arrays.stream(nlogCoeff).map(Math::abs).sum();
        double nlogSum2 = Arrays.stream(nlogCoeff2).map(Math::abs).sum();
        double polySum = Arrays.stream(polyCoeff).map(Math::abs).sum();
        double expSum = Arrays.stream(expCoeff).map(Math::abs).sum();
        if (Config.valueAsLong("output.regression", 0L) != 0) {
            logger.info("Leitud regressioonikordajad:");
            logger.info("Logaritmi teisendus: {}", Arrays.toString(logCoeff));
            logger.info("Linearitmiline teisendus: {}", Arrays.toString(nlogCoeff));
            logger.info("Alternatiivne linearitmiline teisendus: {}", Arrays.toString(nlogCoeff2));
            logger.info("Ilma teisenduseta, polünoomi jaoks: {}", Arrays.toString(polyCoeff));
            logger.info("Eksponentsiaalne teisendus: {}", Arrays.toString(expCoeff));
        }
        nlogSum = Math.min(nlogSum, nlogSum2 * 10);
        // logarithm mapping gives high value with non-logarithm data
        if (logSum < 10 * nlogSum && logSum < 10 * polySum || 10 * logSum < nlogSum && 10 * logSum < polySum && logSum < 100) {
            return "log(n)";
        }
        // 2**100 is sufficiently large to show that it could not be exponential
        if (expSum < polySum && expSum * 10 < nlogSum && x[x.length - 1] < 100) {
            return "2^n";
        }
        if (nlogSum < polySum) {
            return "n*log(n)";
        }
        if (polyCoeff[2] < 1E-15 && polyCoeff[3] < 1E-15) {
            return "n";
        }
        if (polyCoeff[2] > 1E-15 && polyCoeff[3] < 1E-15) {
            return "n^2";
        }
        if (polyCoeff[3] > 1E-15) {
            return "n^3";
        }
        return "n^?";
    }

    /**
     * Rakendab teisendusi xMap ning yMap sisendandmete peal. Kasutab vähimruutude meetodit et leida polünomiaalse
     * regressiooni parameetreid
     *
     * @param x      sisendi suuruste järjend
     * @param y      tööaegade järjend
     * @param xMap   teisendus, mida rakendada igale sisendi suurusele
     * @param yMap   teisendus, mida rakendada igale tööajale
     * @param degree polünomiaalse regressiooni aste, mis lineaarregressioni korral on 1
     * @return leitud regressiooniparameetrid
     */
    private static double[] check(double[] x, double[] y, DoubleUnaryOperator xMap, DoubleUnaryOperator yMap, int degree) {
        List<Double> X = Arrays.stream(x).map(xMap).boxed().collect(Collectors.toList());
        List<Double> Y = Arrays.stream(y).map(yMap).boxed().collect(Collectors.toList());
        WeightedObservedPoints points = new WeightedObservedPoints();
        for (int i = 0; i < X.size(); i++) {
            if (X.get(i) < 1 || Y.get(i) < 1) {
                points.clear();
                continue;
            }
            points.add(X.get(i), Y.get(i));
        }
        PolynomialCurveFitter fitter = PolynomialCurveFitter.create(degree).withMaxIterations(10000);
        try {
            double[] fit = fitter.fit(points.toList());
            double sq = 0;
            for (WeightedObservedPoint point : points.toList()) {
                double dy = fit[0] + point.getX() * fit[1];
                if (fit.length > 2) dy += point.getX() * point.getX() * fit[2];
                if (fit.length > 3) dy += point.getX() * point.getX() * point.getX() * fit[3];
                if (verbose)
                    logger.debug("Ennustasin {}, tegelik väärtus on {}", dy, point.getY());
                sq += Math.pow(dy - point.getY(), 2);
            }
            if (verbose)
                logger.debug("Hälvete ruutude summa: {}", sq);
            return fit;
        } catch (TooManyIterationsException e) {
            if (verbose)
                logger.warn("Regressioon ei õnnestunud iteratsioonide limiidi sees");
            return new double[]{Double.MAX_VALUE};
        }
    }

    /**
     * Rakendab andmetel teisendust, kus tööaeg jagatakse sisendi suuruse logaritmiga. Kasutab vähimruutude meetodit et
     * leida polünomiaalse regressiooni parameetreid
     *
     * @param x sisendi suuruste järjend
     * @param y tööaegade järjend
     * @return leitud regressiooniparameetrid
     */
    private static double[] nLogcheck(double[] x, double[] y) {
        List<Double> X = Arrays.stream(x).boxed().collect(Collectors.toList());
        List<Double> Y = Arrays.stream(y).boxed().collect(Collectors.toList());
        WeightedObservedPoints points = new WeightedObservedPoints();
        for (int i = 0; i < X.size(); i++) {
            if (x[i] == 0 || y[i] == 0) {
                points.clear();
                continue;
            }
            points.add(X.get(i), Y.get(i) / Math.log(X.get(i)));
        }
        PolynomialCurveFitter fitter = PolynomialCurveFitter.create(1).withMaxIterations(10000);
        try {
            return fitter.fit(points.toList());
        } catch (TooManyIterationsException e) {
            return new double[]{Double.POSITIVE_INFINITY};
        }
    }

}
