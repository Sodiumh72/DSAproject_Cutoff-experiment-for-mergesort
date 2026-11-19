# Cutoff experiment - summary tables

- Input file: `raw_results.csv`
- Types: random, sorted, reverse, nearly_sorted, few_uniques, interleaved; sizes: 1,000, 10,000, 100,000, 500,000, 1,000,000, 2,000,000, 5,000,000
- Cutoffs: 0, 2, 4, 5, 6, 7, 8, 9, 10, 12, 16, 20, 24, 32, 40, 48, 64, 96, 128 (baseline = 0)
- Validity filter: Filter: min_size=1000 -> dropped sizes [100]; pure-insertion pairs (cutoff >= size) dropped: none; rows 14,706 -> 11,856
- `best_cutoff` / `near_optimal_cutoffs` only consider true hybrid cutoffs (cutoff > 0); cutoff = 0 (pure MergeSort) is used only as the baseline.
- Near-optimal tolerance: `--tol 0.02` (median <= best_ms * (1 + tol))
- Recommended cutoff (lowest geometric-mean rel_time over all types and sizes, cutoff > 0): **24**

## Data quality

- Groups (type, size, cutoff): 798; repetitions per group: 8 to 25
- Median CV: 5.52%; groups with CV > 10%: 228 (28.6%)
- Differences between cutoffs that are smaller than the run-to-run spread (see `cv_percent`, `q1_ms`/`q3_ms` in `detailed_stats.csv`) should not be interpreted as real effects.

## 1. Best cutoff per (type, size)

| type | size | baseline_ms | best_cutoff | best_ms | speedup | near_optimal_cutoffs | cutoff_7_diff_pct | cutoff_10_diff_pct |
|---|---|---|---|---|---|---|---|---|
| random | 1,000 | 0.272 | 16 | 0.199 | 1.367 | 16;24 | 10.492 | 7.028 |
| random | 10,000 | 2.057 | 10 | 1.750 | 1.175 | 10;12;16;20;24;32 | 3.629 | 0 |
| random | 100,000 | 26.454 | 16 | 23.493 | 1.126 | 7;8;16;20 | 1.621 | 2.777 |
| random | 500,000 | 184.268 | 10 | 169.168 | 1.089 | 6;8;9;10;16;20;24 | 4.869 | 0 |
| random | 1,000,000 | 427.942 | 9 | 391.327 | 1.094 | 5;6;7;9;10;12;20 | 1.736 | 0.940 |
| random | 2,000,000 | 999.536 | 5 | 939.504 | 1.064 | 5;8;9;16;20;24;48 | 3.223 | 2.319 |
| random | 5,000,000 | 2,742.5 | 12 | 2,605.7 | 1.052 | 4;5;6;7;8;9;10;12;16;20;24;32 | 0.330 | 0.033 |
| sorted | 1,000 | 0.096 | 128 | 0.026 | 3.656 | 128 | 152.672 | 128.626 |
| sorted | 10,000 | 1.136 | 128 | 0.537 | 2.115 | 96;128 | 62.926 | 45.226 |
| sorted | 100,000 | 15.489 | 128 | 8.622 | 1.796 | 128 | 39.490 | 42.250 |
| sorted | 500,000 | 90.614 | 128 | 56.844 | 1.594 | 128 | 35.050 | 29.305 |
| sorted | 1,000,000 | 190.370 | 128 | 120.052 | 1.586 | 128 | 34.635 | 29.128 |
| sorted | 2,000,000 | 386.661 | 128 | 247.675 | 1.561 | 128 | 31.984 | 28.576 |
| sorted | 5,000,000 | 1,026.0 | 96 | 680.466 | 1.508 | 96;128 | 27.328 | 20.180 |
| reverse | 1,000 | 0.098 | 7 | 0.076 | 1.286 | 4;5;7 | 0 | 12.467 |
| reverse | 10,000 | 1.142 | 8 | 0.999 | 1.143 | 5;6;7;8;9 | 0.120 | 5.465 |
| reverse | 100,000 | 15.581 | 4 | 14.225 | 1.095 | 4;5;6;7;8;9;10 | 1.232 | 1.201 |
| reverse | 500,000 | 93.514 | 6 | 86.204 | 1.085 | 4;5;6;7;8 | 1.176 | 2.401 |
| reverse | 1,000,000 | 193.801 | 5 | 178.040 | 1.089 | 4;5;6 | 2.285 | 2.076 |
| reverse | 2,000,000 | 384.103 | 4 | 354.794 | 1.083 | 4;5;6;7;9 | 0.971 | 3.263 |
| reverse | 5,000,000 | 1,008.7 | 8 | 923.631 | 1.092 | 4;5;7;8 | 0.161 | 3.764 |
| nearly_sorted | 1,000 | 0.115 | 64 | 0.064 | 1.800 | 32;40;48;64;96 | 37.656 | 22.813 |
| nearly_sorted | 10,000 | 1.339 | 48 | 0.903 | 1.481 | 40;48;64 | 22.158 | 13.271 |
| nearly_sorted | 100,000 | 18.872 | 48 | 14.001 | 1.348 | 48;64 | 12.526 | 13.480 |
| nearly_sorted | 500,000 | 103.600 | 64 | 80.500 | 1.287 | 32;40;48;64;96 | 12.881 | 8.846 |
| nearly_sorted | 1,000,000 | 247.463 | 48 | 189.751 | 1.304 | 48 | 17.350 | 15.453 |
| nearly_sorted | 2,000,000 | 465.788 | 32 | 374.387 | 1.244 | 32;40;48;64 | 10.048 | 6.763 |
| nearly_sorted | 5,000,000 | 1,307.1 | 40 | 1,076.0 | 1.215 | 40;48;64;96;128 | 10.132 | 6.372 |
| few_uniques | 1,000 | 0.126 | 16 | 0.097 | 1.297 | 8;9;10;12;16;20;24 | 8.342 | 1.339 |
| few_uniques | 10,000 | 1.645 | 16 | 1.330 | 1.238 | 12;16;20;24;32 | 6.619 | 2.301 |
| few_uniques | 100,000 | 18.456 | 20 | 15.712 | 1.175 | 16;20;24 | 3.088 | 4.230 |
| few_uniques | 500,000 | 103.978 | 20 | 88.921 | 1.169 | 8;9;10;16;20 | 4.103 | 1.379 |
| few_uniques | 1,000,000 | 217.106 | 24 | 185.907 | 1.168 | 10;16;20;24 | 6.428 | 1.483 |
| few_uniques | 2,000,000 | 449.660 | 24 | 388.744 | 1.157 | 9;12;16;20;24 | 5.254 | 2.316 |
| few_uniques | 5,000,000 | 1,179.2 | 12 | 1,033.5 | 1.141 | 10;12;16;20;24;32 | 3.885 | 0.243 |
| interleaved | 1,000 | 0.102 | 128 | 0.029 | 3.486 | 128 | 148.630 | 122.603 |
| interleaved | 10,000 | 1.146 | 128 | 0.551 | 2.079 | 96;128 | 65.844 | 42.844 |
| interleaved | 100,000 | 15.063 | 128 | 8.766 | 1.718 | 128 | 38.618 | 38.265 |
| interleaved | 500,000 | 89.864 | 128 | 56.804 | 1.582 | 128 | 33.823 | 29.378 |
| interleaved | 1,000,000 | 181.462 | 128 | 111.204 | 1.632 | 128 | 37.569 | 31.527 |
| interleaved | 2,000,000 | 381.373 | 128 | 242.180 | 1.575 | 128 | 34.455 | 28.631 |
| interleaved | 5,000,000 | 992.863 | 96 | 679.758 | 1.461 | 96;128 | 25.291 | 17.320 |

## 2. Overall relative time per cutoff (geometric mean over sizes; 1.0 = baseline)

| cutoff | random | sorted | reverse | nearly_sorted | few_uniques | interleaved | all_types |
|---|---|---|---|---|---|---|---|
| 0 | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 1.0000 | 1.0000 |
| 2 | 0.9565 | 0.9162 | 0.9509 | 0.9388 | 0.9522 | 0.9275 | 0.9402 |
| 4 | 0.9393 | 0.8486 | 0.9057 | 0.8796 | 0.9051 | 0.8429 | 0.8862 |
| 5 | 0.9232 | 0.8222 | 0.8955 | 0.8631 | 0.8958 | 0.8366 | 0.8720 |
| 6 | 0.9185 | 0.8135 | 0.9099 | 0.8522 | 0.8890 | 0.8278 | 0.8676 |
| 7 | 0.9139 | 0.8014 | 0.8983 | 0.8547 | 0.8848 | 0.8154 | 0.8604 |
| 8 | 0.9115 | 0.7757 | 0.9065 | 0.8197 | 0.8661 | 0.7872 | 0.8427 |
| 9 | 0.9051 | 0.7725 | 0.9219 | 0.8289 | 0.8628 | 0.7766 | 0.8427 |
| 10 | 0.8980 | 0.7607 | 0.9292 | 0.8190 | 0.8556 | 0.7645 | 0.8354 |
| 12 | 0.8993 | 0.7544 | 0.9285 | 0.8020 | 0.8551 | 0.7591 | 0.8304 |
| 16 | 0.8886 | 0.7119 | 1.0020 | 0.7875 | 0.8433 | 0.7186 | 0.8193 |
| 20 | 0.9000 | 0.7017 | 1.0424 | 0.7750 | 0.8446 | 0.7033 | 0.8196 |
| 24 | 0.8940 | 0.6913 | 1.0723 | 0.7635 | 0.8459 | 0.6989 | 0.8178 |
| 32 | 0.9251 | 0.6550 | 1.2689 | 0.7497 | 0.8790 | 0.6602 | 0.8332 |
| 40 | 0.9229 | 0.6391 | 1.3685 | 0.7463 | 0.9048 | 0.6434 | 0.8397 |
| 48 | 0.9398 | 0.6346 | 1.3670 | 0.7337 | 0.9041 | 0.6399 | 0.8379 |
| 64 | 1.0219 | 0.5962 | 1.7829 | 0.7398 | 1.0219 | 0.6010 | 0.8890 |
| 96 | 1.0751 | 0.5791 | 2.0136 | 0.7479 | 1.0938 | 0.5872 | 0.9190 |
| 128 | 1.2631 | 0.5332 | 2.8534 | 0.7798 | 1.3830 | 0.5411 | 1.0193 |

Lowest value per column: random -> cutoff 16, sorted -> cutoff 128, reverse -> cutoff 5, nearly_sorted -> cutoff 48, few_uniques -> cutoff 16, interleaved -> cutoff 128, all_types -> cutoff 24

## 3. Doubling ratios T(2N)/T(N)

For an ideal N log N algorithm the ratio is slightly above 2.

| type | cutoff_role | cutoff | size_from | size_to | t_from_ms | t_to_ms | doubling_ratio |
|---|---|---|---|---|---|---|---|
| random | baseline | 0 | 500,000 | 1,000,000 | 184.268 | 427.942 | 2.322 |
| random | baseline | 0 | 1,000,000 | 2,000,000 | 427.942 | 999.536 | 2.336 |
| random | recommended | 24 | 500,000 | 1,000,000 | 169.675 | 401.711 | 2.368 |
| random | recommended | 24 | 1,000,000 | 2,000,000 | 401.711 | 947.898 | 2.360 |
| sorted | baseline | 0 | 500,000 | 1,000,000 | 90.614 | 190.370 | 2.101 |
| sorted | baseline | 0 | 1,000,000 | 2,000,000 | 190.370 | 386.661 | 2.031 |
| sorted | recommended | 24 | 500,000 | 1,000,000 | 69.359 | 147.995 | 2.134 |
| sorted | recommended | 24 | 1,000,000 | 2,000,000 | 147.995 | 298.171 | 2.015 |
| reverse | baseline | 0 | 500,000 | 1,000,000 | 93.514 | 193.801 | 2.072 |
| reverse | baseline | 0 | 1,000,000 | 2,000,000 | 193.801 | 384.103 | 1.982 |
| reverse | recommended | 24 | 500,000 | 1,000,000 | 98.228 | 199.872 | 2.035 |
| reverse | recommended | 24 | 1,000,000 | 2,000,000 | 199.872 | 397.863 | 1.991 |
| nearly_sorted | baseline | 0 | 500,000 | 1,000,000 | 103.600 | 247.463 | 2.389 |
| nearly_sorted | baseline | 0 | 1,000,000 | 2,000,000 | 247.463 | 465.788 | 1.882 |
| nearly_sorted | recommended | 24 | 500,000 | 1,000,000 | 83.683 | 196.051 | 2.343 |
| nearly_sorted | recommended | 24 | 1,000,000 | 2,000,000 | 196.051 | 388.089 | 1.980 |
| few_uniques | baseline | 0 | 500,000 | 1,000,000 | 103.978 | 217.106 | 2.088 |
| few_uniques | baseline | 0 | 1,000,000 | 2,000,000 | 217.106 | 449.660 | 2.071 |
| few_uniques | recommended | 24 | 500,000 | 1,000,000 | 90.715 | 185.907 | 2.049 |
| few_uniques | recommended | 24 | 1,000,000 | 2,000,000 | 185.907 | 388.744 | 2.091 |
| interleaved | baseline | 0 | 500,000 | 1,000,000 | 89.864 | 181.462 | 2.019 |
| interleaved | baseline | 0 | 1,000,000 | 2,000,000 | 181.462 | 381.373 | 2.102 |
| interleaved | recommended | 24 | 500,000 | 1,000,000 | 70.861 | 135.472 | 1.912 |
| interleaved | recommended | 24 | 1,000,000 | 2,000,000 | 135.472 | 295.147 | 2.179 |

## 4. Log-log regression  T(ms) = a * N^b  (sizes >= 100,000)

| type | cutoff_role | cutoff | n_points | fit_a (ms) | fit_b | r2 |
|---|---|---|---|---|---|---|
| random | baseline | 0 | 5 | 2.935e-05 | 1.1926 | 0.9997 |
| random | recommended | 24 | 5 | 2.316e-05 | 1.2052 | 0.9997 |
| sorted | baseline | 0 | 5 | 7.019e-05 | 1.0705 | 0.9998 |
| sorted | recommended | 24 | 5 | 3.724e-05 | 1.0959 | 0.9989 |
| reverse | baseline | 0 | 5 | 7.795e-05 | 1.0633 | 0.9994 |
| reverse | recommended | 24 | 5 | 9.830e-05 | 1.0508 | 0.9998 |
| nearly_sorted | baseline | 0 | 5 | 7.222e-05 | 1.0834 | 0.9991 |
| nearly_sorted | recommended | 24 | 5 | 4.312e-05 | 1.1058 | 0.9995 |
| few_uniques | baseline | 0 | 5 | 9.048e-05 | 1.0627 | 1.0000 |
| few_uniques | recommended | 24 | 5 | 7.420e-05 | 1.0668 | 1.0000 |
| interleaved | baseline | 0 | 5 | 6.953e-05 | 1.0693 | 0.9997 |
| interleaved | recommended | 24 | 5 | 4.201e-05 | 1.0856 | 0.9987 |

## 5. Theory vs empirical (random)

| N | theory_K_star (4/ln2) | theory_best_among_tested | empirical_best_cutoff |
|---|---|---|---|
| 5000000 | 5.771 | 6 | 12 |

The model `N log2(N/K) + N K / 4` is a comparison-count model; real timing also depends on memory access, JIT and boxed `Integer` objects, so a shift of the empirical optimum is expected.

## Files

`detailed_stats.csv` (all groups, not repeated here), `best_cutoff.csv`, `overall_cutoff.csv`, `doubling_and_scaling.csv`, and plots in `plots/`.
