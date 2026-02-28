# Effect Layer

An effect layer is a slot users create that applies a stack of effects to objects in the world, similar to how the adjustment layer works in After Effects. There are different types of effect layers, each targeting different types of objects.

Effect layers can be added to the time line.

## Block Effect Layer

A block effect layer applies effects to blocks in the world. It has the following default properties:

- **Selection**: Representing the blocks to select. This is a text field, and animation keyframes serve as direct modification of the text field (no interpolation). The text field supports the following syntax:
  - `x1,y1,z1,x2,y2,z2`: A cuboid region with two opposite corners at (x1, y1, z1) and (x2, y2, z2).
  - `x,y,z`: A single block region at (x, y, z).
  - `selection1:selection2:...`: A union of multiple regions. By definition, regions can be either a cuboid or single block.

  The block selection tool works as a visual aid for users to select blocks in the world, yielding the > corresponding selection text. Users use:
  - Left click to select a region. Clicking will highlight the selected region.
  - Left drag to navigate, same as everywhere else.
  - Right click to create a new single block region at the clicked block.
  - Middle mouse button click to extend the region to the clicked block.
  - Delete key to delete the active region.

Effects can be applied to the selected blocks as a stack. The effects include:
- **Opacity**: Adjusting the opacity of the selected blocks. Type: Float, Range: [0, 1], Default: 1.
- **Translate**: Translating the selected blocks by a specified vector. Type: Vector3, Default: (0, 0, 0).
- **Replace**: Replacing the selected blocks with a specified block type. Type: Block, Default: Air.

When the user right clicks on the layer, a context menu will show up, allowing the user to add effects to the layer. Effects are displayed as sub-entries of the effect layer in the timeline. Pressing delete (or using the context menu) on the effect layer removes it.

When multiple effects are applied to the same block (whether from the same effect layer or different effect layers), the effects are applied in order: in the timeline, from bottom to top; in the stack, from bottom to top.

- Opacity effects are applied multiplicatively.
- Translate effects are applied additively.
