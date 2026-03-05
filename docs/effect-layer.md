# Effect Layer

An effect layer is a slot users create that applies a stack of effects to objects in the world, similar to how the adjustment layer works in After Effects. There are different types of effect layers, each targeting different types of objects.

Effect layers can be added to the time line.

## Block Effect Layer

A block effect layer applies effects to blocks in the world. It has the following default properties:

- **Selection**: A [Block Selection](selection.md) that specifies the blocks to apply the effects to, default: none. Comes with a [Block Selection Tool](tool.md) for visual aid.

There will be a toggle button for "Include Air" for Block Effect Layers, default: false. When enabled, the selection will include air blocks, allowing effects to be applied to air blocks as well. If the toggle is off, an external `(...)[!air]` filter will be added around the selection text, ensuring that air blocks are not included in the selection.

Effects can be applied to the selected blocks as a stack. The effects include:
- **Opacity**: Adjusting the opacity of the selected blocks. Type: Float, Range: [0, 1], Default: 1.
- **Translate**: Translating the selected blocks by a specified vector. Type: Vector3, Default: (0, 0, 0).
- **Replace**: Replacing the selected blocks with a specified block type. Type: Block, Default: Air.
  - Optional field `filter`: A block filter that specifies which blocks to replace. If not specified, all blocks in the selection will be replaced.

When the user right clicks on the layer, a context menu will show up, allowing the user to add effects to the layer. Effects are displayed as sub-entries of the effect layer in the timeline. Pressing delete (or using the context menu) on the effect layer removes it.

When multiple effects are applied to the same block (whether from the same effect layer or different effect layers), the effects are applied in order: in the timeline, from bottom to top; in the stack, from bottom to top.

- Opacity effects are applied multiplicatively.
- Translate effects are applied additively.
