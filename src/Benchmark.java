import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 - Benchmark to find the optimal CUTOFF for Hybrid MergeSort (MergeWithCutoff).
 *
 - - Reads dataset list from data/manifest.csv (relative_path,type,size,seed,bytes).
 - - Runs from project root (./data, ./result) or from src/ (../data, ../result).
 - - Only calls MergeWithCutoff.sort(Comparable[] a, int cutoff); cutoff = 0 is the baseline.
 - - Outputs result/raw_results.csv, result/summary.csv, result/raw_results_environment.txt
 -   (if already exists -> appends timestamp yyyyMMdd_HHmmss to filename, does not overwrite).
 */
public class Benchmark {

    // ======================== EXPERIMENT CONFIGURATION ========================
    static final int[] CUTOFFS = {0, 2, 4, 5, 6, 7, 8, 9, 10, 12, 16, 20, 24, 32, 40, 48, 64, 96, 128};
    static final int BASELINE_CUTOFF = 0;
    static final String ALGO = "MergeWithCutoff";

    static final long SHUFFLE_SEED = 20261117L;

    // Global warm-up
    static final int GLOBAL_WARMUP_SIZE = 50_000;
    static final long GLOBAL_WARMUP_SEED = 20261118L;
    static final int GLOBAL_WARMUP_PASSES = 3;          // number of passes scanning all CUTOFFS on a 50k array
    // Local warm-up (per dataset)
    static final int LOCAL_WARMUP_PASSES = 1;           // 1 pass for all CUTOFFS, not recorded to CSV

    // Adaptive reps
    static final int SMALL_MAX_SIZE = 10_000;
    static final int SMALL_REPS = 25;                   // size <= 10_000
    static final int MEDIUM_MAX_SIZE = 500_000;
    static final int MEDIUM_REPS = 15;                  // size <= 500_000
    static final int LARGE_REPS = 8;                    // size > 500_000

    static final int IO_BUFFER_SIZE = 64 * 1024;

    static final String HARDWARE_REFERENCE =
            "Intel Core i7-6600U, 2 cores / 4 threads, 8 GB RAM (experimental machine specs provided by the user, "
                    + "not retrieved from JVM/OS)";

    // ======================== HELPER DATA TYPES ========================
    private static final class DatasetInfo {
        final String relativePath, type, name;
        final int size;
        final long seed, bytes;

        DatasetInfo(String relativePath, String type, int size, long seed, long bytes) {
            this.relativePath = relativePath;
            this.type = type;
            this.size = size;
            this.seed = seed;
            this.bytes = bytes;
            String file = relativePath.substring(relativePath.lastIndexOf('/') + 1);
            this.name = file.endsWith(".txt") ? file.substring(0, file.length() - 4) : file;
        }
    }

    /** Fatal error: stops the entire experiment (main will call System.exit(1) after closing files). */
    private static final class BenchmarkFailure extends RuntimeException {
        BenchmarkFailure(String msg) { super(msg); }
    }

    // ======================== MAIN ========================
    public static void main(String[] args) throws IOException {
        ZonedDateTime startTime = ZonedDateTime.now();
        Path root = resolveProjectRoot();
        Path dataDir = root.resolve("data");
        Path resultDir = root.resolve("result");
        Files.createDirectories(resultDir);

        List<DatasetInfo> datasets = readManifest(dataDir);
        validateCutoffs();

        // ---- Choose output filenames (do not overwrite old results) ----
        Path rawPath = resultDir.resolve("raw_results.csv");
        Path summaryPath = resultDir.resolve("summary.csv");
        Path envPath = resultDir.resolve("raw_results_environment.txt");
        if (Files.exists(rawPath) || Files.exists(summaryPath) || Files.exists(envPath)) {
            String ts = startTime.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            rawPath = resultDir.resolve("raw_results_" + ts + ".csv");
            summaryPath = resultDir.resolve("summary_" + ts + ".csv");
            envPath = resultDir.resolve("raw_results_" + ts + "_environment.txt");
        }

        System.out.println("Project root : " + root);
        System.out.println("Datasets     : " + datasets.size() + " (from " + dataDir.resolve("manifest.csv") + ")");
        System.out.println("Raw results  : " + rawPath);
        System.out.println("Summary      : " + summaryPath);
        System.out.println();

        long startNanos = System.nanoTime();
        String status = "COMPLETED";
        try (BufferedWriter rawW = newWriter(rawPath);
             BufferedWriter sumW = newWriter(summaryPath)) {
            rawW.write("dataset,size,algo,cutoff,rep,nanos\n");
            sumW.write("dataset,size,algo,cutoff,mean,median,standard_deviation,min,max,speedup_vs_baseline\n");
            runAll(dataDir, datasets, rawW, sumW);
        } catch (BenchmarkFailure f) {
            status = "ABORTED: " + f.getMessage();
            System.err.println("\nBENCHMARK ABORTED: " + f.getMessage());
        }
        ZonedDateTime endTime = ZonedDateTime.now();
        double elapsedSec = (System.nanoTime() - startNanos) / 1e9;

        writeEnvironment(envPath, datasets.size(), startTime, endTime, elapsedSec, status, rawPath, summaryPath);

        if (status.equals("COMPLETED")) {
            System.out.printf("%nDone in %.1fs. Results written to: %s%n", elapsedSec, resultDir);
        } else {
            System.exit(1);
        }
    }

    // ======================== MAIN EXECUTION LOOP ========================
    private static void runAll(Path dataDir, List<DatasetInfo> datasets,
                               BufferedWriter rawW, BufferedWriter sumW) throws IOException {
        Random shuffleRnd = new Random(SHUFFLE_SEED);
        int[] order = new int[CUTOFFS.length];
        for (int i = 0; i < order.length; i++) order[i] = i;

        globalWarmup();

        for (int d = 0; d < datasets.size(); d++) {
            long t0 = System.nanoTime();
            DatasetInfo ds = datasets.get(d);
            int reps = repsFor(ds.size);
            runDataset(dataDir, ds, reps, shuffleRnd, order, rawW, sumW);
            rawW.flush();
            sumW.flush();
            System.out.printf("[%2d/%d] %s | size=%d | reps=%d | %.2fs%n",
                    d + 1, datasets.size(), ds.name, ds.size, reps, (System.nanoTime() - t0) / 1e9);
        }
    }

    private static void globalWarmup() {
        System.out.printf("Global warm-up: %d passes x %d cutoffs on random array n=%d ...%n",
                GLOBAL_WARMUP_PASSES, CUTOFFS.length, GLOBAL_WARMUP_SIZE);
        Random r = new Random(GLOBAL_WARMUP_SEED);
        Integer[] original = new Integer[GLOBAL_WARMUP_SIZE];
        for (int i = 0; i < original.length; i++) original[i] = r.nextInt(GLOBAL_WARMUP_SIZE * 10);
        Integer[] expected = original.clone();
        Arrays.sort(expected);
        Integer[] work = new Integer[GLOBAL_WARMUP_SIZE];
        for (int pass = 0; pass < GLOBAL_WARMUP_PASSES; pass++) {
            for (int cutoff : CUTOFFS) {
                System.arraycopy(original, 0, work, 0, GLOBAL_WARMUP_SIZE);
                MergeWithCutoff.sort(work, cutoff);
                if (!Arrays.equals(work, expected))
                    throw new BenchmarkFailure("global warm-up produced wrong result, cutoff=" + cutoff);
            }
        }
        System.out.println("Global warm-up done.\n");
    }

    private static void runDataset(Path dataDir, DatasetInfo ds, int reps, Random shuffleRnd, int[] order,
                                   BufferedWriter rawW, BufferedWriter sumW) throws IOException {
        final int size = ds.size;

        // ---- Prepare data (outside measurement region) ----
        Integer[] original = readDataset(dataDir.resolve(ds.relativePath), ds);
        final long checksumBefore = checksum(original);
        Integer[] expected = original.clone();
        Arrays.sort(expected);
        Integer[] work = new Integer[size];

        // ---- Local warm-up (not recorded to CSV) ----
        for (int pass = 0; pass < LOCAL_WARMUP_PASSES; pass++) {
            for (int cutoff : CUTOFFS) {
                System.arraycopy(original, 0, work, 0, size);
                MergeWithCutoff.sort(work, cutoff);
                if (!Arrays.equals(work, expected))
                    throw new BenchmarkFailure("local warm-up wrong result: dataset=" + ds.name
                            + ", size=" + size + ", cutoff=" + cutoff);
            }
        }

        // ---- Official measurement ----
        long[][] nanos = new long[CUTOFFS.length][reps];
        for (int rep = 0; rep < reps; rep++) {
            shuffle(order, shuffleRnd);                      // shuffle all CUTOFFS (including baseline) every rep
            for (int k = 0; k < order.length; k++) {
                int ci = order[k];
                int cutoff = CUTOFFS[ci];

                System.arraycopy(original, 0, work, 0, size); // restore input - OUTSIDE measurement region

                long start = System.nanoTime();
                MergeWithCutoff.sort(work, cutoff);
                long end = System.nanoTime();

                if (!Arrays.equals(work, expected)) {          // verify immediately after stopping the timer
                    throw new BenchmarkFailure("wrong result: dataset=" + ds.name + ", size=" + size
                            + ", cutoff=" + cutoff + ", rep=" + (rep + 1));
                }
                nanos[ci][rep] = end - start;
            }
        }

        // ---- Verify original was not mutated ----
        if (checksum(original) != checksumBefore)
            throw new BenchmarkFailure("original data was modified: dataset=" + ds.name + ", size=" + size);

        // ---- Write raw_results.csv (by cutoff, then by rep) ----
        for (int ci = 0; ci < CUTOFFS.length; ci++) {
            for (int rep = 0; rep < reps; rep++) {
                rawW.write(ds.name + "," + size + "," + ALGO + "," + CUTOFFS[ci] + "," + (rep + 1) + ","
                        + nanos[ci][rep] + "\n");
            }
        }

        // ---- Write summary.csv ----
        int baseIdx = indexOfCutoff(BASELINE_CUTOFF);
        double baselineMedian = median(nanos[baseIdx]);
        for (int ci = 0; ci < CUTOFFS.length; ci++) {
            long[] v = nanos[ci];
            double med = median(v);
            double mean = mean(v);
            double sd = stdDev(v, mean);
            long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
            for (long x : v) { if (x < min) min = x; if (x > max) max = x; }
            String speedup = med > 0 ? String.format(Locale.ROOT, "%.4f", baselineMedian / med) : "NA";
            sumW.write(String.format(Locale.ROOT, "%s,%d,%s,%d,%.2f,%.2f,%.2f,%d,%d,%s%n",
                    ds.name, size, ALGO, CUTOFFS[ci], mean, med, sd, min, max, speedup));
        }

        // Release references (do not call System.gc())
        original = null;
        expected = null;
        work = null;
        nanos = null;
    }

    // ======================== STATISTICS ========================
    private static double mean(long[] v) {
        double s = 0;
        for (long x : v) s += x;
        return s / v.length;
    }

    private static double median(long[] v) {
        long[] c = v.clone();
        Arrays.sort(c);
        int n = c.length;
        return (n % 2 == 1) ? c[n / 2] : (c[n / 2 - 1] + c[n / 2]) / 2.0;
    }

    /** Sample standard deviation (n - 1). */
    private static double stdDev(long[] v, double mean) {
        if (v.length < 2) return 0.0;
        double s = 0;
        for (long x : v) s += (x - mean) * (x - mean);
        return Math.sqrt(s / (v.length - 1));
    }

    // ======================== UTILITIES ========================
    private static int repsFor(int size) {
        if (size <= SMALL_MAX_SIZE) return SMALL_REPS;
        if (size <= MEDIUM_MAX_SIZE) return MEDIUM_REPS;
        return LARGE_REPS;
    }

    private static void shuffle(int[] a, Random r) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }

    private static long checksum(Integer[] a) {
        long h = 1125899906842597L;
        for (Integer x : a) h = 31 * h + x;
        return h;
    }

    private static int indexOfCutoff(int cutoff) {
        for (int i = 0; i < CUTOFFS.length; i++) if (CUTOFFS[i] == cutoff) return i;
        throw new BenchmarkFailure("cutoff " + cutoff + " not in CUTOFFS");
    }

    private static void validateCutoffs() {
        indexOfCutoff(BASELINE_CUTOFF); // baseline must be in CUTOFFS
    }

    private static BufferedWriter newWriter(Path p) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(p), StandardCharsets.UTF_8),
                IO_BUFFER_SIZE);
    }

    private static Path resolveProjectRoot() {
        Path cwd = Paths.get("").toAbsolutePath();
        if (Files.exists(cwd.resolve("data").resolve("manifest.csv"))) return cwd;                 // running from project root
        Path parent = cwd.getParent();
        if (parent != null && Files.exists(parent.resolve("data").resolve("manifest.csv"))) return parent; // running from src/
        throw new BenchmarkFailure("Cannot find data/manifest.csv from working directory " + cwd
                + " (run DataGenerator first, and run from project root or src/)");
    }

    private static List<DatasetInfo> readManifest(Path dataDir) throws IOException {
        Path manifest = dataDir.resolve("manifest.csv");
        List<DatasetInfo> list = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(manifest, StandardCharsets.UTF_8)) {
            String header = br.readLine();
            if (header == null || !header.trim().equals("relative_path,type,size,seed,bytes"))
                throw new BenchmarkFailure("Unexpected manifest header: " + header);
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] p = line.split(",");
                if (p.length != 5) throw new BenchmarkFailure("Bad manifest line: " + line);
                list.add(new DatasetInfo(p[0], p[1], Integer.parseInt(p[2]), Long.parseLong(p[3]),
                        Long.parseLong(p[4])));
            }
        }
        if (list.isEmpty()) throw new BenchmarkFailure("Manifest has no datasets");
        return list;
    }

    private static Integer[] readDataset(Path file, DatasetInfo ds) throws IOException {
        Integer[] a = new Integer[ds.size];
        int count = 0;
        try (BufferedReader br = new BufferedReader(
                new java.io.InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8), IO_BUFFER_SIZE)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (count >= ds.size)
                    throw new BenchmarkFailure("File has more than " + ds.size + " values: " + file);
                a[count++] = Integer.valueOf(line);
            }
        }
        if (count != ds.size)
            throw new BenchmarkFailure("File " + file + " has " + count + " values, manifest says " + ds.size);
        long actualBytes = Files.size(file);
        if (actualBytes != ds.bytes)
            System.err.println("WARNING: " + ds.name + " is " + actualBytes + " bytes, manifest says " + ds.bytes);
        return a;
    }

    // ======================== ENVIRONMENT ========================
    private static void writeEnvironment(Path path, int datasetCount, ZonedDateTime start, ZonedDateTime end,
                                         double elapsedSec, String status, Path raw, Path summary) throws IOException {
        Runtime rt = Runtime.getRuntime();
        StringBuilder sb = new StringBuilder();
        sb.append("=== BENCHMARK ENVIRONMENT ===\n");
        sb.append("Status                 : ").append(status).append('\n');
        sb.append("Start time             : ").append(start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append('\n');
        sb.append("End time               : ").append(end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append('\n');
        sb.append(String.format(Locale.ROOT, "Total elapsed (s)       : %.1f%n", elapsedSec));
        sb.append('\n').append("--- Hardware (reference, user-provided) ---\n");
        sb.append(HARDWARE_REFERENCE).append('\n');
        sb.append('\n').append("--- OS / JVM (retrieved at runtime) ---\n");
        for (String k : new String[]{"os.name", "os.version", "os.arch", "java.version", "java.vendor",
                "java.vm.name", "java.vm.version"})
            sb.append(String.format("%-23s: %s%n", k, System.getProperty(k)));
        sb.append(String.format("%-23s: %d%n", "availableProcessors", rt.availableProcessors()));
        sb.append(String.format("%-23s: %d bytes (%.0f MB)%n", "Runtime.maxMemory()", rt.maxMemory(),
                rt.maxMemory() / (1024.0 * 1024.0)));
        sb.append("JVM input arguments    : ").append(ManagementFactory.getRuntimeMXBean().getInputArguments()).append('\n');
        sb.append('\n').append("--- Experiment configuration ---\n");
        sb.append("Algorithm              : ").append(ALGO).append(" (baseline cutoff = ").append(BASELINE_CUTOFF).append(")\n");
        sb.append("SHUFFLE_SEED           : ").append(SHUFFLE_SEED).append('\n');
        sb.append("CUTOFFS                : ").append(Arrays.toString(CUTOFFS)).append('\n');
        sb.append("Global warm-up         : ").append(GLOBAL_WARMUP_PASSES).append(" pass(es) over all CUTOFFS, random array n=")
                .append(GLOBAL_WARMUP_SIZE).append(", seed=").append(GLOBAL_WARMUP_SEED).append('\n');
        sb.append("Local warm-up          : ").append(LOCAL_WARMUP_PASSES).append(" pass(es) over all CUTOFFS per dataset (not recorded)\n");
        sb.append("Adaptive reps          : size <= ").append(SMALL_MAX_SIZE).append(" -> ").append(SMALL_REPS)
                .append("; size <= ").append(MEDIUM_MAX_SIZE).append(" -> ").append(MEDIUM_REPS)
                .append("; size > ").append(MEDIUM_MAX_SIZE).append(" -> ").append(LARGE_REPS).append('\n');
        sb.append("Execution              : single-threaded; CUTOFFS order reshuffled every rep\n");
        sb.append("Total datasets         : ").append(datasetCount).append('\n');
        sb.append("Raw results file       : ").append(raw.getFileName()).append('\n');
        sb.append("Summary file           : ").append(summary.getFileName()).append('\n');
        Files.write(path, sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}