//! Squarified Treemap layout engine.
//!
//! Implements the Bruls, Huizing, and van Wijk (2000) squarified treemap algorithm,
//! optimizing rectangle aspect ratios for intuitive visual size perception.

use serde::{Deserialize, Serialize};

/// 2D rectangle in floating point coordinates.
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize)]
pub struct Rect {
    pub x: f32,
    pub y: f32,
    pub width: f32,
    pub height: f32,
}

impl Rect {
    pub fn new(x: f32, y: f32, width: f32, height: f32) -> Self {
        Self {
            x,
            y,
            width: width.max(0.0),
            height: height.max(0.0),
        }
    }

    pub fn area(&self) -> f32 {
        self.width * self.height
    }

    pub fn shorter_side(&self) -> f32 {
        self.width.min(self.height)
    }

    pub fn contains(&self, px: f32, py: f32) -> bool {
        px >= self.x && px <= self.x + self.width && py >= self.y && py <= self.y + self.height
    }
}

/// An input item to be placed in the Treemap.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TreemapInput {
    pub id: u32,
    pub weight: u64,
    pub name: String,
    pub category: u8,
    pub is_dir: bool,
}

/// A rendered rectangle in the computed Treemap layout.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct TreemapItem {
    pub id: u32,
    pub rect: Rect,
    pub weight: u64,
    pub name: String,
    pub category: u8,
    pub is_dir: bool,
    /// Percentage of the parent container's total size (0.0 .. 100.0).
    pub percentage: f32,
    /// Whether this rectangle is an aggregated "<Other N items>" synthetic block.
    pub is_collapsed_group: bool,
    pub collapsed_count: u32,
}

/// Layout configuration options.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TreemapOptions {
    /// Minimum dimension (in pixels/points) for an individual rectangle.
    /// Rectangles smaller than this will be collapsed into a group block. Default: 6.0
    pub min_side_px: f32,
    /// Maximum number of visible items before small ones are grouped. Default: 1000
    pub max_visible_items: usize,
    /// Padding around inner items in pixels. Default: 1.0
    pub item_spacing_px: f32,
}

impl Default for TreemapOptions {
    fn default() -> Self {
        Self {
            min_side_px: 6.0,
            max_visible_items: 1000,
            item_spacing_px: 1.0,
        }
    }
}

/// Computed Treemap result containing all layout items and total weight.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TreemapResult {
    pub bounds: Rect,
    pub total_weight: u64,
    pub items: Vec<TreemapItem>,
}

impl TreemapResult {
    /// Hit-test: finds the item whose bounding box contains the specified point.
    pub fn hit_test(&self, px: f32, py: f32) -> Option<&TreemapItem> {
        // Iterate in reverse so topmost rendered items are checked first
        self.items.iter().rev().find(|item| item.rect.contains(px, py))
    }
}

/// Compute a squarified treemap layout.
pub fn compute_treemap(
    bounds: Rect,
    mut items: Vec<TreemapInput>,
    opts: &TreemapOptions,
) -> TreemapResult {
    if bounds.width <= 0.0 || bounds.height <= 0.0 || items.is_empty() {
        return TreemapResult {
            bounds,
            total_weight: 0,
            items: Vec::new(),
        };
    }

    // Sort items descending by weight
    items.sort_by(|a, b| b.weight.cmp(&a.weight));

    let total_weight: u64 = items.iter().map(|i| i.weight).sum();
    if total_weight == 0 {
        return TreemapResult {
            bounds,
            total_weight: 0,
            items: Vec::new(),
        };
    }

    let total_area = bounds.area();
    let min_area = (opts.min_side_px * opts.min_side_px).max(1.0);

    // Partition into prominent items and small/collapsed items
    let mut visible_inputs = Vec::new();
    let mut collapsed_weight = 0u64;
    let mut collapsed_count = 0u32;

    for (idx, item) in items.into_iter().enumerate() {
        if item.weight == 0 {
            continue;
        }
        let item_area = (item.weight as f64 / total_weight as f64) * (total_area as f64);
        if idx < opts.max_visible_items && item_area as f32 >= min_area {
            visible_inputs.push(item);
        } else {
            collapsed_weight += item.weight;
            collapsed_count += 1;
        }
    }

    // If there are collapsed items, add a synthetic item at the end
    let has_collapsed = collapsed_count > 0;
    if has_collapsed {
        visible_inputs.push(TreemapInput {
            id: u32::MAX,
            weight: collapsed_weight,
            name: format!("<Other {} items>", collapsed_count),
            category: 8, // Other
            is_dir: false,
        });
    }

    let mut result_items = Vec::with_capacity(visible_inputs.len());
    let mut areas: Vec<f32> = visible_inputs
        .iter()
        .map(|it| ((it.weight as f64 / total_weight as f64) * total_area as f64) as f32)
        .collect();

    // Run recursive squarify
    squarify(
        &visible_inputs,
        &mut areas,
        bounds,
        total_weight,
        opts.item_spacing_px,
        has_collapsed,
        collapsed_count,
        &mut result_items,
    );

    TreemapResult {
        bounds,
        total_weight,
        items: result_items,
    }
}

fn worst(row: &[f32], w: f32) -> f32 {
    if row.is_empty() || w <= 0.0 {
        return f32::INFINITY;
    }
    let s: f32 = row.iter().sum();
    if s <= 0.0 {
        return f32::INFINITY;
    }
    let s2 = s * s;
    let w2 = w * w;

    let mut max_val = 0.0f32;
    for &area in row {
        if area <= 0.0 {
            continue;
        }
        let aspect1 = (w2 * area) / s2;
        let aspect2 = s2 / (w2 * area);
        let worst_item = aspect1.max(aspect2);
        if worst_item > max_val {
            max_val = worst_item;
        }
    }
    max_val
}

fn layout_row(
    row_inputs: &[&TreemapInput],
    row_areas: &[f32],
    container: &mut Rect,
    total_weight: u64,
    spacing: f32,
    has_collapsed: bool,
    collapsed_count: u32,
    out: &mut Vec<TreemapItem>,
) {
    if row_inputs.is_empty() {
        return;
    }
    let row_sum: f32 = row_areas.iter().sum();
    let is_horizontal = container.width >= container.height;

    if is_horizontal {
        // Cut vertical strip of width = row_sum / container.height
        let row_width = if container.height > 0.0 {
            (row_sum / container.height).min(container.width)
        } else {
            0.0
        };

        let mut current_y = container.y;
        for (input, &area) in row_inputs.iter().zip(row_areas.iter()) {
            let item_height = if row_width > 0.0 {
                (area / row_width).min(container.y + container.height - current_y)
            } else {
                0.0
            };

            let rect = Rect::new(
                container.x + spacing * 0.5,
                current_y + spacing * 0.5,
                (row_width - spacing).max(0.0),
                (item_height - spacing).max(0.0),
            );

            let is_collapsed = has_collapsed && input.id == u32::MAX;
            let percentage = if total_weight > 0 {
                (input.weight as f64 / total_weight as f64 * 100.0) as f32
            } else {
                0.0
            };

            out.push(TreemapItem {
                id: input.id,
                rect,
                weight: input.weight,
                name: input.name.clone(),
                category: input.category,
                is_dir: input.is_dir,
                percentage,
                is_collapsed_group: is_collapsed,
                collapsed_count: if is_collapsed { collapsed_count } else { 0 },
            });

            current_y += item_height;
        }

        container.x += row_width;
        container.width = (container.width - row_width).max(0.0);
    } else {
        // Cut horizontal strip of height = row_sum / container.width
        let row_height = if container.width > 0.0 {
            (row_sum / container.width).min(container.height)
        } else {
            0.0
        };

        let mut current_x = container.x;
        for (input, &area) in row_inputs.iter().zip(row_areas.iter()) {
            let item_width = if row_height > 0.0 {
                (area / row_height).min(container.x + container.width - current_x)
            } else {
                0.0
            };

            let rect = Rect::new(
                current_x + spacing * 0.5,
                container.y + spacing * 0.5,
                (item_width - spacing).max(0.0),
                (row_height - spacing).max(0.0),
            );

            let is_collapsed = has_collapsed && input.id == u32::MAX;
            let percentage = if total_weight > 0 {
                (input.weight as f64 / total_weight as f64 * 100.0) as f32
            } else {
                0.0
            };

            out.push(TreemapItem {
                id: input.id,
                rect,
                weight: input.weight,
                name: input.name.clone(),
                category: input.category,
                is_dir: input.is_dir,
                percentage,
                is_collapsed_group: is_collapsed,
                collapsed_count: if is_collapsed { collapsed_count } else { 0 },
            });

            current_x += item_width;
        }

        container.y += row_height;
        container.height = (container.height - row_height).max(0.0);
    }
}

fn squarify(
    inputs: &[TreemapInput],
    areas: &mut [f32],
    mut container: Rect,
    total_weight: u64,
    spacing: f32,
    has_collapsed: bool,
    collapsed_count: u32,
    out: &mut Vec<TreemapItem>,
) {
    let mut row_inputs: Vec<&TreemapInput> = Vec::new();
    let mut row_areas: Vec<f32> = Vec::new();

    let mut i = 0;
    while i < inputs.len() {
        let area = areas[i];
        if container.width <= 0.0 || container.height <= 0.0 {
            break;
        }

        let shorter = container.shorter_side();

        if row_areas.is_empty() {
            row_inputs.push(&inputs[i]);
            row_areas.push(area);
            i += 1;
        } else {
            let mut test_row = row_areas.clone();
            test_row.push(area);

            let curr_worst = worst(&row_areas, shorter);
            let next_worst = worst(&test_row, shorter);

            if next_worst <= curr_worst {
                row_inputs.push(&inputs[i]);
                row_areas.push(area);
                i += 1;
            } else {
                // Layout current row and advance container
                layout_row(
                    &row_inputs,
                    &row_areas,
                    &mut container,
                    total_weight,
                    spacing,
                    has_collapsed,
                    collapsed_count,
                    out,
                );
                row_inputs.clear();
                row_areas.clear();
            }
        }
    }

    if !row_areas.is_empty() {
        layout_row(
            &row_inputs,
            &row_areas,
            &mut container,
            total_weight,
            spacing,
            has_collapsed,
            collapsed_count,
            out,
        );
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_squarify_basic_layout() {
        let bounds = Rect::new(0.0, 0.0, 1000.0, 500.0);
        let items = vec![
            TreemapInput {
                id: 1,
                weight: 600,
                name: "Videos".into(),
                category: 1,
                is_dir: true,
            },
            TreemapInput {
                id: 2,
                weight: 300,
                name: "Images".into(),
                category: 2,
                is_dir: true,
            },
            TreemapInput {
                id: 3,
                weight: 100,
                name: "Docs".into(),
                category: 5,
                is_dir: false,
            },
        ];

        let opts = TreemapOptions {
            min_side_px: 1.0,
            max_visible_items: 100,
            item_spacing_px: 0.0,
        };

        let result = compute_treemap(bounds, items, &opts);
        assert_eq!(result.items.len(), 3);
        assert_eq!(result.total_weight, 1000);

        // Videos should have 60% of total area
        let total_rect_area: f32 = result.items.iter().map(|it| it.rect.area()).sum();
        let expected_area = bounds.area();
        assert!((total_rect_area - expected_area).abs() < 10.0);

        // Check hit testing
        let hit = result.hit_test(10.0, 10.0).expect("hit item");
        assert!(hit.id == 1 || hit.id == 2 || hit.id == 3);
    }

    #[test]
    fn test_small_items_collapsed() {
        let bounds = Rect::new(0.0, 0.0, 800.0, 800.0);
        let mut items = vec![
            TreemapInput {
                id: 1,
                weight: 10_000,
                name: "BigFile.mp4".into(),
                category: 1,
                is_dir: false,
            },
        ];

        // Add 100 tiny items
        for i in 2..102 {
            items.push(TreemapInput {
                id: i,
                weight: 1,
                name: format!("tiny_{}.txt", i),
                category: 5,
                is_dir: false,
            });
        }

        let opts = TreemapOptions {
            min_side_px: 10.0,
            max_visible_items: 20,
            item_spacing_px: 1.0,
        };

        let result = compute_treemap(bounds, items, &opts);
        // BigFile + 1 collapsed block
        assert_eq!(result.items.len(), 2);
        assert_eq!(result.items[0].id, 1);
        assert!(result.items[1].is_collapsed_group);
        assert_eq!(result.items[1].collapsed_count, 100);
    }

    #[test]
    fn test_empty_treemap() {
        let bounds = Rect::new(0.0, 0.0, 500.0, 500.0);
        let opts = TreemapOptions::default();
        let res = compute_treemap(bounds, vec![], &opts);
        assert!(res.items.is_empty());
        assert_eq!(res.total_weight, 0);
        assert!(res.hit_test(10.0, 10.0).is_none());
    }

    #[test]
    fn test_single_item_treemap() {
        let bounds = Rect::new(0.0, 0.0, 500.0, 500.0);
        let mut opts = TreemapOptions::default();
        opts.item_spacing_px = 0.0;
        let items = vec![
            TreemapInput { id: 1, weight: 1000, name: "Single".into(), category: 1, is_dir: false },
        ];
        let res = compute_treemap(bounds, items, &opts);
        assert_eq!(res.items.len(), 1);
        assert_eq!(res.items[0].id, 1);
        assert!((res.items[0].rect.area() - bounds.area()).abs() < 1.0);
    }
}
