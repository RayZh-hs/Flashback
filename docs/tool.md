# Tools

## Block Selection Tool

A block selection tool produces block selections. When the tool is active, a tooltip will be shown at the bottom of the viewport "Block Selector: LMB append, MMB extend, ENTER confirm, ALT move".

- `Left Click` to append a new single-block region to the current block selection (joined with `+`).
- `Middle Click` to extend the current block selection. If it is single-block, it will become a cuboid selection with the original block and the clicked block as opposite corners. If nothing is selected, it will create a single-block selection at the clicked block.
- `Delete` or `Backspace` key to delete the current block selection.
- `Enter` key to confirm the selection.
- `Ctrl + Left Click` to create a new block selection that is removed from the block selection (minus selection).
- When `Alt` key is pressed, the block selection tool will be suppressed, and the user can navigate normally with the left mouse button. This will change the tooltip to "Block Selector (Locked): LMB navigate". When `Alt` key is released, the block selection tool will be active again.

The tool writes atomic regions in parenthesized form (for example `(1,64,-10)` or `(1,64,-10..5,70,-2)`) so `-` in coordinates is never ambiguous with the selection difference operator.
