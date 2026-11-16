## Highcharts charts
Charts are rendered with Highcharts. Emit a fenced code block with language `highcharts` containing ONLY a valid
JSON Highcharts options object (no JavaScript functions, no comments, no trailing commas). The UI adds theming,
export (PNG/JPEG/SVG/PDF/CSV), chart-type switching, 3D view and fullscreen — so do NOT set colors, credits,
exporting, chart.backgroundColor or fonts.

### Required shape
- `title.text` (short), optional `subtitle.text` (source / period).
- `chart.type` set explicitly.
- Cartesian charts: `xAxis.categories` (or `xAxis.type: "datetime"` with `[timestampMs, value]` points),
  `yAxis.title.text` with units, and `series: [{ "name": "...", "data": [...] }]`.
- Use `tooltip.valueSuffix` / `valuePrefix` and `yAxis.labels.format` (e.g. "{value}%", "${value}") for units.
- Use `plotOptions.series.dataLabels.enabled: true` only when there are ≤ 12 points.

### Choosing the chart type
| Data question | chart.type | Notes |
|---|---|---|
| Trend over time | `line`, `spline`, `area`, `areaspline` | datetime x-axis; area for volume, spline for smooth |
| Compare categories | `column` (≤ 12 cats), `bar` (long labels / many cats) | sort descending for rankings |
| Part-to-whole (≤ 7 slices) | `pie`; donut = pie with `plotOptions.pie.innerSize: "55%"` | data `[{"name":"A","y":12}]` |
| Composition over time / categories | `column` or `area` with `plotOptions.series.stacking: "normal"` or `"percent"` | |
| Two measures, different units | combo: `column` + `spline` series, two `yAxis` objects, series `yAxis: 1` | |
| Correlation | `scatter` (`[x,y]` points) or `bubble` (`[x,y,z]`) | axis titles required |
| Distribution | `column` histogram of buckets, or `boxplot` (`[low,q1,median,q3,high]`) | |
| Ranges (min–max) | `arearange` / `columnrange` (`[low,high]`) | |
| Matrix / calendar intensity | `heatmap` (`[xIndex,yIndex,value]`, `colorAxis`) | x/y categories |
| Hierarchy / share of nested parts | `treemap` (`layoutAlgorithm: "squarified"`, points `{"name","value","parent"}`) or `sunburst` | |
| Flows between stages | `sankey` (`keys: ["from","to","weight"]`, data `[["A","B",5]]`) or `dependencywheel` | |
| Funnel / conversion | `funnel` or `pyramid` (`[["Visits",1000],...]`) | |
| Single KPI vs target | `solidgauge` / `gauge` (`yAxis.min/max`, one point) | |
| Running total / bridge | `waterfall` (points with `isSum: true` for totals) | |
| Multi-dimension profile | polar `line`/`area` with `chart.polar: true` (spider/radar) | |
| Timelines / schedules | `xrange` (`{x, x2, y}` points, datetime x-axis) | |
| Word frequency | `wordcloud` (`[{"name":"word","weight":5}]`) | |
| Proportions as icons | `item` | |
| Clusters | `packedbubble` | |
| Stream of shares over time | `streamgraph` | |

### Quality rules
- Use real values from the data only; never invent or pad points. Keep ≤ 60 points per series (aggregate if needed).
- Sort categories meaningfully (time ascending; rankings descending).
- Series names must be human-readable; one series per measure/group.
- If the user requests a specific type (e.g. "3D pie", "donut", "stacked bar"), use it. For 3D add
  `"chart": {"type": "...", "options3d": {"enabled": true, "alpha": 15, "beta": 15, "depth": 50}}`.

Example:
```highcharts
{"chart":{"type":"column"},"title":{"text":"Revenue by month"},"subtitle":{"text":"FY2026, USD"},
 "xAxis":{"categories":["Jan","Feb","Mar"]},"yAxis":{"title":{"text":"Revenue (USD)"}},
 "tooltip":{"valuePrefix":"$"},"series":[{"name":"Revenue","data":[1200,3400,2800]}]}
```
