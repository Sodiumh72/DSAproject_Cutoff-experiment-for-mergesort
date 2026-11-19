import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Correctness-only test for MergeSort and MergeWithCutoff (hybrid).
 * Pure Java, no frameworks. Run main() directly from IntelliJ IDEA.
 * Place this file in the same package as InsertionSort, MergeSort, MergeWithCutoff.
 */
public class Test {

    // ------------------------------------------------------------------ config
    private static final int[] CUTOFFS = {0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16};
    private static final int[] BASE_SIZES = {0, 1, 2, 5, 8, 10, 15, 16, 17, 30, 31, 32, 33, 64, 100};
    private static final int[] ODD_EVEN_SIZES = {3, 4, 7, 8, 9, 15, 16, 17, 31, 32, 33, 63, 64, 65};
    private static final int RANDOM_REPRO_COUNT = 20;
    private static final int RANDOM_REPRO_SIZE = 50;

    /** true = print one row per run; false = print only failing rows. */
    private static final boolean PRINT_ALL_ROWS = true;

    // ---------------------------------------------------------------- dataset
    private static class Dataset {
        final String name;
        final Integer[] data;

        Dataset(String name, Integer[] data) {
            this.name = name;
            this.data = data;
        }
    }

    private interface Generator {
        Integer[] make(int n, Random rnd);
    }

    // ---------------------------------------------------------------- counters
    private static int totalDatasets = 0;
    private static int mergeRuns = 0;
    private static int hybridRuns = 0;
    private static final Map<Integer, Integer> hybridRunsByCutoff = new LinkedHashMap<>();

    private static int checksPassed = 0;
    private static int checksFailed = 0;
    private static int runsPassed = 0;
    private static int runsFailed = 0;

    private static int notSortedCount = 0;          // isSorted == false (merge or hybrid)
    private static int notEqualExpectedCount = 0;   // result != expected (merge or hybrid)
    private static int mergeHybridMismatchCount = 0; // resultMerge != resultHybrid

    private static final List<String> failures = new ArrayList<>();

    // ====================================================================== main
    public static void main(String[] args) {
        for (int c : CUTOFFS) hybridRunsByCutoff.put(c, 0);

        List<Dataset> datasets = buildAllDatasets();

        printHeader();
        for (Dataset d : datasets) {
            testDataset(d);
        }
        printSummary();
        printFailures();
        printConclusion();
    }

    // ================================================================ test logic
    /** Tests one dataset against MergeSort (once) and MergeWithCutoff (every cutoff). */
    private static void testDataset(Dataset d) {
        totalDatasets++;

        // Independent expected result from Arrays.sort on its own copy.
        Integer[] expected = getExpectedResult(d.data);

        // ---- Original MergeSort: run once on an independent copy.
        Integer[] resultMerge = copy(d.data);
        String mergeError = null;
        try {
            MergeSort.sort(resultMerge);
        } catch (Throwable t) {
            mergeError = t.toString();
            resultMerge = null;
        }
        mergeRuns++;

        boolean isSortedMerge = resultMerge != null && isSorted(resultMerge);
        boolean mergeEqExpected = resultMerge != null && Arrays.equals(resultMerge, expected);

        boolean mergeRunOk = isSortedMerge && mergeEqExpected;
        recordChecks(2, (isSortedMerge ? 1 : 0) + (mergeEqExpected ? 1 : 0));
        if (!isSortedMerge) notSortedCount++;
        if (!mergeEqExpected) notEqualExpectedCount++;
        recordRun(mergeRunOk);

        printRow(d.name, d.data.length, "N/A", isSortedMerge, null, mergeEqExpected, null, null, mergeRunOk);
        if (!mergeRunOk) {
            failures.add(describeFailure(d, "MergeSort", -1, expected, resultMerge, mergeError,
                    isSortedMerge, null, mergeEqExpected, null, null));
        }

        // ---- MergeWithCutoff: every cutoff runs independently on a fresh copy.
        for (int cutoff : CUTOFFS) {
            Integer[] resultHybrid = copy(d.data);
            String hybridError = null;
            try {
                MergeWithCutoff.sort(resultHybrid, cutoff);
            } catch (Throwable t) {
                hybridError = t.toString();
                resultHybrid = null;
            }
            hybridRuns++;
            hybridRunsByCutoff.put(cutoff, hybridRunsByCutoff.get(cutoff) + 1);

            boolean isSortedHybrid = resultHybrid != null && isSorted(resultHybrid);
            boolean hybridEqExpected = resultHybrid != null && Arrays.equals(resultHybrid, expected);
            boolean mergeEqHybrid = resultMerge != null && resultHybrid != null
                    && Arrays.equals(resultMerge, resultHybrid);

            boolean runOk = isSortedHybrid && hybridEqExpected && mergeEqHybrid;
            recordChecks(3, (isSortedHybrid ? 1 : 0) + (hybridEqExpected ? 1 : 0) + (mergeEqHybrid ? 1 : 0));
            if (!isSortedHybrid) notSortedCount++;
            if (!hybridEqExpected) notEqualExpectedCount++;
            if (!mergeEqHybrid) mergeHybridMismatchCount++;
            recordRun(runOk);

            printRow(d.name, d.data.length, String.valueOf(cutoff),
                    isSortedMerge, isSortedHybrid, mergeEqExpected, hybridEqExpected, mergeEqHybrid, runOk && mergeRunOk);
            if (!runOk) {
                failures.add(describeFailure(d, "MergeWithCutoff", cutoff, expected, resultHybrid, hybridError,
                        isSortedMerge, isSortedHybrid, mergeEqExpected, hybridEqExpected, mergeEqHybrid));
            }
        }
    }

    private static void recordChecks(int total, int passed) {
        checksPassed += passed;
        checksFailed += total - passed;
    }

    private static void recordRun(boolean ok) {
        if (ok) runsPassed++;
        else runsFailed++;
    }

    // ================================================================== helpers
    private static boolean isSorted(Integer[] a) {
        for (int i = 1; i < a.length; i++) {
            if (a[i - 1] > a[i]) return false;
        }
        return true;
    }

    private static Integer[] getExpectedResult(Integer[] input) {
        Integer[] expected = copy(input);
        Arrays.sort(expected);
        return expected;
    }

    private static Integer[] copy(Integer[] a) {
        return Arrays.copyOf(a, a.length);
    }

    private static Integer[] arr(int... v) {
        Integer[] r = new Integer[v.length];
        for (int i = 0; i < v.length; i++) r[i] = v[i];
        return r;
    }

    // ============================================================ dataset builders
    private static Map<String, Generator> distributions() {
        Map<String, Generator> g = new LinkedHashMap<>();
        g.put("Random", Test::genRandom);
        g.put("Sorted", Test::genSorted);
        g.put("Reverse", Test::genReverse);
        g.put("Identical", Test::genIdentical);
        g.put("FewUnique", Test::genFewUnique);
        g.put("DuplicateHeavy", Test::genDuplicateHeavy);
        g.put("Alternating", Test::genAlternating);
        g.put("NearlySorted", Test::genNearlySorted);
        g.put("NegPos", Test::genNegativePositive);
        g.put("Extreme", Test::genExtreme);
        return g;
    }

    private static Integer[] genRandom(int n, Random rnd) {
        Integer[] a = new Integer[n];
        for (int i = 0; i < n; i++) a[i] = rnd.nextInt(2001) - 1000;
        return a;
    }

    private static Integer[] genSorted(int n, Random rnd) {
        Integer[] a = new Integer[n];
        for (int i = 0; i < n; i++) a[i] = i;
        return a;
    }

    private static Integer[] genReverse(int n, Random rnd) {
        Integer[] a = new Integer[n];
        for (int i = 0; i < n; i++) a[i] = n - i;
        return a;
    }

    private static Integer[] genIdentical(int n, Random rnd) {
        Integer[] a = new Integer[n];
        Arrays.fill(a, 7);
        return a;
    }

    private static Integer[] genFewUnique(int n, Random rnd) {
        int[] pool = {-5, 0, 5, 10};
        Integer[] a = new Integer[n];
        for (int i = 0; i < n; i++) a[i] = pool[rnd.nextInt(pool.length)];
        return a;
    }

    private static Integer[] genDuplicateHeavy(int n, Random rnd) {
        int range = Math.max(2, n / 4);
        Integer[] a = new Integer[n];
        for (int i = 0; i < n; i++) a[i] = rnd.nextInt(range);
        return a;
    }

    private static Integer[] genAlternating(int n, Random rnd) {
        Integer[] a = new Integer[n];
        for (int i = 0; i < n; i++) a[i] = (i % 2 == 0) ? i / 2 : 1000 - i / 2;
        return a;
    }

    private static Integer[] genNearlySorted(int n, Random rnd) {
        Integer[] a = genSorted(n, rnd);
        if (n < 2) return a;
        int swaps = Math.max(1, n / 10);
        for (int s = 0; s < swaps; s++) {
            int i = rnd.nextInt(n);
            int j = rnd.nextInt(n);
            Integer tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
        }
        return a;
    }

    private static Integer[] genNegativePositive(int n, Random rnd) {
        Integer[] a = new Integer[n];
        for (int i = 0; i < n; i++) a[i] = rnd.nextInt(201) - 100;
        return a;
    }

    private static Integer[] genExtreme(int n, Random rnd) {
        int[] pool = {Integer.MIN_VALUE, Integer.MAX_VALUE, 0, -1, 1};
        Integer[] a = new Integer[n];
        for (int i = 0; i < n; i++) a[i] = pool[rnd.nextInt(pool.length)];
        if (n >= 2) { // guarantee both extremes are present
            a[0] = Integer.MAX_VALUE;
            a[n - 1] = Integer.MIN_VALUE;
        }
        return a;
    }

    /** Collects every dataset, skipping duplicates (same name + same size). */
    private static List<Dataset> buildAllDatasets() {
        List<Dataset> list = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        Map<String, Generator> dists = distributions();

        // A. size x distribution
        for (int n : BASE_SIZES) addDistributions(list, seen, dists, n);

        // B. edge cases
        addEdgeCases(list, seen);

        // C. cutoff boundary: cutoff-1, cutoff, cutoff+1, 2c-1, 2c, 2c+1
        for (int c : CUTOFFS) {
            int[] sizes = {c - 1, c, c + 1, c * 2 - 1, c * 2, c * 2 + 1};
            for (int n : sizes) {
                if (n <= 0) continue;
                addDistributions(list, seen, dists, n);
            }
        }

        // D. odd / even sizes
        for (int n : ODD_EVEN_SIZES) addDistributions(list, seen, dists, n);

        // E. random reproducibility: 20 datasets x 50 elements, fixed distinct seeds
        for (int s = 1; s <= RANDOM_REPRO_COUNT; s++) {
            Random rnd = new Random(1000L + s);
            add(list, seen, "RandomSeed" + s, genRandom(RANDOM_REPRO_SIZE, rnd));
        }

        return list;
    }

    private static void addDistributions(List<Dataset> list, Set<String> seen,
                                         Map<String, Generator> dists, int n) {
        int id = 0;
        for (Map.Entry<String, Generator> e : dists.entrySet()) {
            id++;
            Random rnd = new Random(n * 31L + id); // deterministic per (size, distribution)
            add(list, seen, e.getKey(), e.getValue().make(n, rnd));
        }
    }

    private static void addEdgeCases(List<Dataset> list, Set<String> seen) {
        add(list, seen, "Edge:Empty", arr());
        add(list, seen, "Edge:Single", arr(42));
        add(list, seen, "Edge:TwoAscending", arr(1, 2));
        add(list, seen, "Edge:TwoDescending", arr(2, 1));
        add(list, seen, "Edge:TwoEqual", arr(5, 5));
        add(list, seen, "Edge:AllSame", arr(9, 9, 9, 9, 9, 9, 9));
        add(list, seen, "Edge:DupAtStart", arr(1, 1, 5, 3, 4, 2, 6));
        add(list, seen, "Edge:DupInMiddle", arr(6, 2, 4, 4, 4, 1, 9));
        add(list, seen, "Edge:DupAtEnd", arr(7, 3, 5, 1, 9, 9, 9));
        add(list, seen, "Edge:OnlyNegative", arr(-3, -10, -1, -7, -7, -2));
        add(list, seen, "Edge:OnlyPositive", arr(8, 3, 15, 1, 3, 22, 9));
        add(list, seen, "Edge:OnlyZeros", arr(0, 0, 0, 0, 0));
        add(list, seen, "Edge:NegZeroPos", arr(5, -3, 0, 2, 0, -8, 7, -1));
        add(list, seen, "Edge:MinMax", arr(Integer.MAX_VALUE, Integer.MIN_VALUE));
        add(list, seen, "Edge:MinMaxMixed",
                arr(Integer.MAX_VALUE, 0, Integer.MIN_VALUE, -1, 1, Integer.MIN_VALUE, Integer.MAX_VALUE));
        add(list, seen, "Edge:MinMaxSorted", arr(Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE));
        add(list, seen, "Edge:MinMaxReverse", arr(Integer.MAX_VALUE, 1, 0, -1, Integer.MIN_VALUE));
    }

    private static void add(List<Dataset> list, Set<String> seen, String name, Integer[] data) {
        String key = name + "|" + data.length;
        if (seen.add(key)) {
            list.add(new Dataset(name, data));
        }
    }

    // ================================================================== reporting
    private static void printHeader() {
        System.out.println("=== CORRECTNESS TEST: MergeSort vs MergeWithCutoff ===");
        System.out.println("Cutoffs: " + Arrays.toString(CUTOFFS));
        System.out.println();
        System.out.printf("%-20s %5s %6s %12s %12s %16s %17s %14s %8s%n",
                "Dataset", "Size", "Cutoff", "MergeSorted", "HybridSorted",
                "Merge=Expected", "Hybrid=Expected", "Merge=Hybrid", "Status");
        System.out.println(repeat("-", 120));
    }

    private static void printRow(String name, int size, String cutoff,
                                 Boolean mergeSorted, Boolean hybridSorted,
                                 Boolean mergeEqExp, Boolean hybridEqExp,
                                 Boolean mergeEqHybrid, boolean ok) {
        if (!PRINT_ALL_ROWS && ok) return;
        System.out.printf("%-20s %5d %6s %12s %12s %16s %17s %14s %8s%n",
                name, size, cutoff,
                pf(mergeSorted), pf(hybridSorted), pf(mergeEqExp), pf(hybridEqExp), pf(mergeEqHybrid),
                ok ? "PASS" : "FAIL");
    }

    private static String pf(Boolean b) {
        if (b == null) return "-";
        return b ? "PASS" : "FAIL";
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }

    private static String describeFailure(Dataset d, String algorithm, int cutoff,
                                          Integer[] expected, Integer[] actual, String error,
                                          boolean mergeSorted, Boolean hybridSorted,
                                          boolean mergeEqExp, Boolean hybridEqExp, Boolean mergeEqHybrid) {
        StringBuilder sb = new StringBuilder();
        sb.append("FAILED: dataset=").append(d.name)
                .append(", size=").append(d.data.length)
                .append(", algorithm=").append(algorithm)
                .append(", cutoff=").append(cutoff < 0 ? "N/A" : String.valueOf(cutoff)).append('\n');
        if (error != null) sb.append("  exception : ").append(error).append('\n');
        sb.append("  checks    : mergeSorted=").append(mergeSorted)
                .append(", hybridSorted=").append(hybridSorted)
                .append(", mergeEqExpected=").append(mergeEqExp)
                .append(", hybridEqExpected=").append(hybridEqExp)
                .append(", mergeEqHybrid=").append(mergeEqHybrid).append('\n');
        sb.append("  input     : ").append(Arrays.toString(d.data)).append('\n');
        sb.append("  expected  : ").append(Arrays.toString(expected)).append('\n');
        sb.append("  actual    : ").append(actual == null ? "null (exception)" : Arrays.toString(actual)).append('\n');
        return sb.toString();
    }

    private static void printSummary() {
        System.out.println();
        System.out.println("=== SUMMARY ===");
        System.out.println("Total datasets                         : " + totalDatasets);
        System.out.println("Total MergeSort runs                   : " + mergeRuns);
        System.out.println("Total MergeWithCutoff runs             : " + hybridRuns);
        for (Map.Entry<Integer, Integer> e : hybridRunsByCutoff.entrySet()) {
            System.out.println("  Hybrid runs with cutoff " + String.format("%-2d", e.getKey())
                    + "             : " + e.getValue());
        }
        System.out.println("Total runs passed / failed             : " + runsPassed + " / " + runsFailed);
        System.out.println("Total checks passed                    : " + checksPassed);
        System.out.println("Total checks failed                    : " + checksFailed);
        System.out.println("Cases not sorted correctly             : " + notSortedCount);
        System.out.println("Cases different from expected result   : " + notEqualExpectedCount);
        System.out.println("Cases MergeSort != Hybrid              : " + mergeHybridMismatchCount);
    }

    private static void printFailures() {
        if (failures.isEmpty()) return;
        System.out.println();
        System.out.println("=== FAILURE DETAILS (" + failures.size() + ") ===");
        for (String f : failures) System.out.println(f);
    }

    private static void printConclusion() {
        boolean expectedRunCounts = mergeRuns == totalDatasets
                && hybridRuns == totalDatasets * CUTOFFS.length;

        boolean allPassed = checksFailed == 0
                && runsFailed == 0
                && notSortedCount == 0
                && notEqualExpectedCount == 0
                && mergeHybridMismatchCount == 0
                && failures.isEmpty()
                && expectedRunCounts;

        System.out.println();
        System.out.println("=== FINAL CONCLUSION ===");
        if (allPassed) {
            System.out.println("ALL TESTS PASSED: MergeSort and MergeWithCutoff produce correct and identical "
                    + "results across all tested datasets and cutoff values.");
        } else {
            System.out.println("TEST FAILED: One or more correctness checks failed. "
                    + "Please inspect the failed datasets and cutoff values.");
        }
    }
}