import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates experimental datasets for finding the optimal CUTOFF of Hybrid MergeSort.
 *
 * Output structure (relative to the data/ directory):
 *   data/random/random_100.txt, data/sorted/sorted_1000000.txt, ...
 *   data/manifest.csv   (relative_path,type,size,seed,bytes)
 *
 * The data/ directory is determined as follows:
 *   - If args[0] is passed: use that path.
 *   - If the working directory is "src": use ../data (running from inside src/).
 *   - Otherwise: use ./data (working directory = project root, IntelliJ default).
 *
 * Each file contains one integer per line. Each (type, size) combination has a deterministic seed -> reproducible.
 */
public class DataGenerator {

    static final int[] SIZES = {100, 1_000, 10_000, 100_000, 500_000, 1_000_000, 2_000_000, 5_000_000};

    static final String[] TYPES = {"random", "sorted", "reverse", "nearly_sorted", "few_uniques", "interleaved"};

    static final long BASE_SEED = 20261109L;

    /** Number of swaps for nearly_sorted = n / NEARLY_SORTED_DIVISOR (n/20 = 5% of n). */
    static final int NEARLY_SORTED_DIVISOR = 20;

    /** Number of distinct values for few_uniques: 0..9. */
    static final int FEW_UNIQUES_RANGE = 10;

    static final int WRITE_BUFFER_SIZE = 64 * 1024;

    public static void main(String[] args) throws IOException {
        Path outDir = resolveDataDir(args);
        Files.createDirectories(outDir);
        System.out.println("Output directory: " + outDir.toAbsolutePath().normalize());

        int totalFiles = TYPES.length * SIZES.length;
        int done = 0;
        long totalBytes = 0;
        long startAll = System.nanoTime();

        List<String> manifest = new ArrayList<>();
        manifest.add("relative_path,type,size,seed,bytes");

        for (int t = 0; t < TYPES.length; t++) {
            String type = TYPES[t];
            Path typeDir = outDir.resolve(type);
            Files.createDirectories(typeDir);

            for (int n : SIZES) {
                long start = System.nanoTime();
                long seed = BASE_SEED + 31L * n + t;

                int[] data = generate(type, n, seed);
                String fileName = type + "_" + n + ".txt";
                Path file = typeDir.resolve(fileName);
                write(file, data);
                long bytes = Files.size(file);
                totalBytes += bytes;
                done++;

                manifest.add(type + "/" + fileName + "," + type + "," + n + "," + seed + "," + bytes);
                System.out.printf("[%2d/%d] %-32s n=%,10d %,13d bytes  %6.2fs%n",
                        done, totalFiles, type + "/" + fileName, n, bytes, (System.nanoTime() - start) / 1e9);
                // data becomes garbage after each iteration -> at most ~20MB (5M ints) alive at once
            }
        }

        Path manifestPath = outDir.resolve("manifest.csv");
        Files.write(manifestPath, manifest, StandardCharsets.UTF_8);

        System.out.printf("%nDone. %d files, %,.1f MB total, %.2fs elapsed.%n",
                done, totalBytes / (1024.0 * 1024.0), (System.nanoTime() - startAll) / 1e9);
        System.out.println("Manifest: " + manifestPath.toAbsolutePath().normalize());
    }

    private static Path resolveDataDir(String[] args) {
        if (args.length > 0) return Paths.get(args[0]);
        Path cwd = Paths.get("").toAbsolutePath();
        if (cwd.getFileName() != null && cwd.getFileName().toString().equals("src") && cwd.getParent() != null)
            return cwd.getParent().resolve("data");
        return cwd.resolve("data");
    }

    public static int[] generate(String type, int n, long seed) {
        Random rnd = new Random(seed);
        int[] a = new int[n];
        switch (type) {
            case "random": {
                int bound = (int) Math.min(Integer.MAX_VALUE, 10L * n); // [0, n*10)
                for (int i = 0; i < n; i++) a[i] = rnd.nextInt(bound);
                break;
            }
            case "sorted":
                for (int i = 0; i < n; i++) a[i] = i;                  // 0 .. n-1
                break;
            case "reverse":
                for (int i = 0; i < n; i++) a[i] = n - i;              // n .. 1
                break;
            case "nearly_sorted": {
                for (int i = 0; i < n; i++) a[i] = i;
                int swaps = n / NEARLY_SORTED_DIVISOR;                 // 5% of n random swaps
                for (int s = 0; s < swaps; s++) {
                    int i = rnd.nextInt(n);
                    int j = rnd.nextInt(n);
                    int tmp = a[i]; a[i] = a[j]; a[j] = tmp;
                }
                break;
            }
            case "few_uniques":
                for (int i = 0; i < n; i++) a[i] = rnd.nextInt(FEW_UNIQUES_RANGE); // 0..9
                break;
            case "interleaved": {
                int half = n / 2;
                for (int i = 0; i < half; i++) a[i] = 2 * i;           // first half: 0, 2, 4, ...
                for (int k = 0; k < n - half; k++) a[half + k] = 2 * k + 1; // second half: 1, 3, 5, ...
                break;
            }
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
        return a;
    }

    /** Writes each number on a line; manually formats digits into a reused char[] to avoid creating intermediate Strings. */
    private static void write(Path file, int[] data) throws IOException {
        char[] buf = new char[12];
        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8), WRITE_BUFFER_SIZE)) {
            for (int v : data) {
                int pos = buf.length;
                buf[--pos] = '\n';
                if (v == 0) {
                    buf[--pos] = '0';
                } else {
                    boolean neg = v < 0;
                    long x = Math.abs((long) v);
                    while (x > 0) {
                        buf[--pos] = (char) ('0' + (x % 10));
                        x /= 10;
                    }
                    if (neg) buf[--pos] = '-';
                }
                w.write(buf, pos, buf.length - pos);
            }
        }
    }
}