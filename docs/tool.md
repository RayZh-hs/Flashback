# Tools

## Block Selection Tool

A block selection tool produces block selections. When the tool is active, a tooltip will be shown at the bottom of the viewport "Block Selector: LMB new, MMB extend, ENTER confirm, ALT move".

- `Left Click` to create a new block selection.
- `Middle Click` to extend the current block selection. If it is single-block, it will become a cuboid selection with the original block and the clicked block as opposite corners. If nothing is selected, it will create a single-block selection at the clicked block.
- `Delete` or `Backspace` key to delete the current block selection.
- `Enter` key to confirm the selection.
- `Ctrl + Left Click` to create a new block selection that is removed from the block selection (minus selection).
- When `Alt` key is pressed, the block selection tool will be suppressed, and the user can navigate normally with the left mouse button. This will change the tooltip to "Block Selector (Locked): LMB navigate". When `Alt` key is released, the block selection tool will be active again.
