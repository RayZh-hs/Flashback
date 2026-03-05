# Selection

A selection specifies a section of objects in the world. A selection maps one-to-one to a text field, called a selection text. All selections are stored as and parsed from selection texts.

## Block Selection

Selection Text Syntax:

- `x1,y1,z1..x2,y2,z2`: A cuboid region with two opposite corners at (x1, y1, z1) and (x2, y2, z2).
- `x,y,z`: A single block region at (x, y, z).
- `selection1 + selection2 + ...`: A union of multiple selections. By definition, regions can be either a cuboid or single block.
- `selection1 - selection2`: A difference of selections.
- `*`: The entire world (all the blocks).
- `selection[filter]`: A filtered selection, where `filter` is a block filter.
- `(selection)`: A parenthesized selection, used to specify the order of operations.

Order of operations: parentheses, filters, union/difference.

### Block Filter

A block filter is a text field that allows users to specify a filter for blocks. The syntax is as follows:

- `blockName`: Matches blocks with the specified block name.
- `#tag`: Matches blocks with the specified tag.
- `!filter`: Negates the filter, matching blocks that do not match the filter.
- `filter1, filter2, ...`: Matches blocks that match all of the filters. An alias is `filter1& filter2& ...`.
- `filter1| filter2| ...`: Matches blocks that match any of the filters.
- `(filter)`: A parenthesized filter, used to specify the order of operations.

Order of operations: parentheses, negation, and, or.
