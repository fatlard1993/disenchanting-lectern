# Disenchanting Lectern

Pull the enchantments off an item and into a book, at a lectern.

Server-side only. No new block, no new item, no screen - a vanilla lectern holding a vanilla book
hands back a vanilla enchanted book, so players need nothing installed.

## How

1. Put a **blank book and quill** on a lectern.
2. Right-click the lectern holding an **enchanted item**.

The item comes back clean in your hand, an enchanted book carrying its enchantments pops off the
lectern, and the blank book is spent making it.

This is the step vanilla is missing between the grindstone and the anvil. The grindstone takes
enchantments off and destroys them; the anvil puts books on. There is no way to get one back *off*
an item you would rather not keep - so a good enchantment on the wrong tool is stuck there.

## Reading enchantments

Right-click an **empty** lectern holding an enchanted item or an enchanted book, and it prints what
is on it to your chat. Nothing is consumed and nothing changes - a lectern is where you read things.

```
Diamond Pickaxe
  Efficiency IV · level IV of V
      Mine blocks faster.
  Fortune II · level II of III · not with Silk Touch
      More drops from blocks that scatter what they hold, like ores and gravel.
  Curse of Vanishing · maxed · a curse, and stays put
      The item disappears when you die.
```

Each enchantment gets the facts the game knows - how near the ceiling the level is, what it refuses
to sit beside, whether it is treasure or a curse - and then a line of plain English saying what it
actually does.

## Details

**The book has to be blank.** A written book on a lectern is one somebody set up to be read, and a
book and quill with pages in it is a draft. Consuming either because the reader happened to be
holding an enchanted pickaxe would destroy something to do a favour nobody asked for, so both are
left alone and open normally. A blank book on a lectern has no other use, which is what makes
acting on one safe.

**Curses stay.** Binding and Vanishing do not transfer and do not come off, the same exception the
grindstone makes. A lectern that laundered them would leave them as drawbacks anybody could remove
for the price of a book. An item carrying nothing but curses is left alone entirely, and the book
is not spent.

**The prior-work penalty is cleared**, as the grindstone clears it. It is a tax on the enchanting
that just left; carrying it on a bare item would make a stripped tool quietly more expensive to
work than a fresh one.

**Everything moves at once.** All the item's enchantments go onto one book, rather than one at a
time.

The cost is a book and quill - a workbench price rather than an altar one. No levels.

## Development

Installing, and writing a description for an enchantment of your own, are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
