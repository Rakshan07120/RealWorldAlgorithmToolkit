# Real-World Algorithm Visualization and Performance Analysis Toolkit

A desktop application (Java 21, Kotlin, JavaFX, Maven) that visualizes graph, sorting and searching algorithms
on real datasets, lets you interact with the execution, and benchmarks the algorithms with measured data.

## Prerequisites

- JDK 21
- Maven 3.9 or newer
- Internet access for the first `mvn` run only (to download dependencies); the application itself works offline.

## Build, test, run

```bash
mvn clean test       # compiles Kotlin and runs the JUnit 5 tests (no JavaFX window is opened)
mvn javafx:run       # launches the desktop application
```

Run the commands from the project root (the folder containing `pom.xml`), because the file dialogs open the
`sample-data` folder relative to it and the tests read the sample files from there.

## Features

| Tab | What it does |
|---|---|
| Graph Algorithms | BFS, DFS, Dijkstra, A*. Zoomable/pannable force-directed graph view with start, target, current, visited, examined-edge and final-path colours. Start/Pause/Resume/Step/Reset, speed slider, live counters, results panel, CSV summary export. |
| Sorting Algorithms | Bubble, selection, insertion, merge and quick sort. Animated bars for comparisons, swaps and writes; generated patterns (random, sorted, reversed, nearly sorted, few unique) or a loaded dataset. |
| Searching Algorithms | Linear and binary search with examined elements, shrinking range and found/not-found result. Binary search on unsorted input sorts a copy explicitly and says so. |
| Performance Benchmarking | Select algorithms, sizes and repetitions; runs in a background task; table of results, line chart (time vs size, average/median/minimum) and bar chart (operations at the largest size); CSV export. |
| Dataset Management | Load graph CSV/JSON and array CSV/JSON, validation with line-numbered error messages, preview, and "use for" buttons that send the data to the other tabs. |

Graph workspace: generate random connected weighted graphs (node count, extra edges), add/remove nodes and edges
(edit modes), set start and target, load CSV/JSON, re-layout, clear, Zoom In / Zoom Out / Fit View, mouse-wheel zoom and
drag-to-pan. For large graphs weight and node labels are hidden until you zoom in; the graph data is never changed by that.

## Graph semantics

Graphs are **undirected** and simple: no self-loops and no duplicate edges (A-B and B-A are the same edge). Every
weight is a positive finite number, which is enforced when an edge is created, so Dijkstra and A* never see
negative, zero, NaN or infinite weights. Disconnected graphs are supported; a missing path is reported as `NO PATH`,
which is distinct from a path of cost 0 (start equals target).

BFS and DFS are *traversal* measurements: they stop at the target and their path is not weight-optimal (BFS minimises
the number of edges). Dijkstra and A* are *shortest-path* measurements. Charts and tables label this explicitly.

A* uses the Euclidean distance between node coordinates as heuristic only when every node has coordinates and every
edge weight is at least the straight-line distance of its endpoints; then the heuristic is admissible and consistent.
Otherwise it uses a zero heuristic (identical to Dijkstra) and the result says so. Randomly generated graphs satisfy the
condition by construction.

## Dataset formats

Sample files are in `sample-data/`.

**Graph CSV** (header required, one undirected edge per line):

```csv
source,target,weight
A,B,4
A,C,2
B,D,5
C,D,1
```

**Graph JSON** (`directed` must be false or omitted; `nodes` and `x`/`y` are optional):

```json
{
  "directed": false,
  "nodes": [{"id": "A", "x": 0, "y": 0}, {"id": "B", "x": 3, "y": 2}],
  "edges": [{"source": "A", "target": "B", "weight": 4}]
}
```

**Array CSV** (header `value` optional; one integer per line or comma separated):

```csv
value
42
17
93
```

**Array JSON**: `[42, 17, 93]` or `{"values": [42, 17, 93]}`.

Validation reports every bad record with its line (CSV) or position (JSON): empty file, wrong header, missing fields,
non-numeric / non-positive / non-finite weights, self-loops, duplicate edges, unknown node references, non-integer array
values, malformed JSON, invalid UTF-8, unsupported extensions. Nothing is silently skipped; a file with any problem imports nothing.
At most 50 problems are listed, followed by a count of the rest.

## Benchmark methodology

- Only the algorithm call is timed with `System.nanoTime`. Copying input arrays, generating data or graphs, file loading,
  chart rendering and UI updates are outside the timed region, and visualization time is never included.
- Each measurement does a configurable number of discarded warm-up runs, then the configured number of measured
  repetitions (default 3 + 10). Average, median and minimum are reported.
- All algorithms of one input size receive identical input (same seed): sorting algorithms get copies of the same array;
  graph algorithms run on the same graph with the same start and target; search algorithms use the same sorted array and the same targets.
- Search operations take nanoseconds, so one measurement is a batch of 500 queries (half present, half absent); time and
  comparisons in the table are per batch.
- Operation counters come from the same code path as the timing (comparisons, swaps, writes/moves, nodes visited, edges
  examined, queue operations). "Operations" is comparisons + swaps + writes for sorting, comparisons for searching and
  node expansions + edge examinations + queue operations for graphs.
- Results depend on JIT, hardware and load. Very small timings are noisy, and the charts show measurements on this machine;
  they do not prove a theoretical complexity.
- Every sorting row also verifies that the output is sorted and every search row that the hit count is right; failures
  appear in the Status column.

## Project structure

```
pom.xml
sample-data/                      sample CSV / JSON datasets (including an invalid one for demonstrating validation)
src/main/kotlin/algotoolkit/
  Main.kt                         entry point
  model/                          Graph, GraphGenerator, DataGenerator, number formatting
  algorithms/                     Sorting, Searching, GraphAlgorithms (step generation + counters, no JavaFX)
  io/                             DatasetIO (CSV/JSON import, validation, previews), CsvExport
  benchmark/                      Benchmarker, BenchmarkConfig, BenchmarkRow
  ui/                             JavaFX: App, tabs, GraphCanvas, BarCanvas, Playback, ForceLayout, helpers
src/main/resources/style.css
src/test/kotlin/algotoolkit/      JUnit 5 tests for model, algorithms, io and benchmark (no UI startup)
```

Design notes: every algorithm is implemented once. Visualization replays the steps recorded by that same
implementation, and benchmarking runs it without recording, so what you see is what is measured. The animation is a
JavaFX `Timeline` that replays precomputed steps; benchmarks run in a background `Task`, so the interface stays responsive.
Spring Boot is intentionally not used: a desktop application with no web server has no need for dependency injection
beyond the small `AppState` object, and the Spring and JavaFX lifecycles add complexity without benefit.

## Known limitations

- Step-by-step visualizations accept arrays of at most 300 elements; use the benchmarking tab for larger inputs.
- Quick sort with a middle pivot and strict partitioning degrades towards O(n²) when almost all keys are equal.
- Arrays contain 32-bit integers only; graphs are undirected only.
- The force-directed layout is O(n²) per iteration; graphs beyond a few thousand nodes lay out slowly (the UI limits generation to 2000 nodes).
- The benchmark cancel button stops after the measurement that is currently running (a long O(n²) run cannot be interrupted midway).
- The benchmark charts use linear axes (no log scale), so O(n²) algorithms dominate the chart at large sizes.
